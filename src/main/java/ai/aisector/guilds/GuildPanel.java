package ai.aisector.guilds;

import ai.aisector.SectorPlugin;
import ai.aisector.user.User;
import ai.aisector.utils.BukkitTaskScheduler;
import ai.aisector.utils.Messages;
import com.mongodb.client.model.Filters;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

final class GuildPanel implements Listener {
    private static final int PAGE_LENGTH = 45;
    private static final int MAX_CUSTOM = 18;
    private final SectorPlugin plugin;
    private final GuildManager guilds;
    private final Map<UUID, NamePrompt> prompts = new ConcurrentHashMap<>();

    private record NamePrompt(Player player, String tag, String generation) {}

    private static final class Menu implements InventoryHolder {
        final UUID viewer;
        final String tag;
        final String generation;
        final Inventory inventory;
        final Map<Integer, BiConsumer<Player, Guild>> actions = new HashMap<>();

        Menu(Player player, Guild guild, String title, int size) {
            viewer = player.getUniqueId();
            tag = guild.getTag();
            generation = guild.getPersistenceGeneration();
            inventory = Bukkit.createInventory(this, size, Component.text(title));
        }

        @Override public Inventory getInventory() { return inventory; }

        void button(int slot, Material icon, String label, BiConsumer<Player, Guild> action, String... lore) {
            inventory.setItem(slot, item(icon, label, lore));
            if (action != null) actions.put(slot, action);
        }
    }

    GuildPanel(SectorPlugin plugin) {
        this.plugin = plugin;
        this.guilds = plugin.getGuildManager();
    }

    void open(Player player, User user) {
        Guild guild = user.hasGuild() ? guilds.getGuild(user.getGuildTag()) : null;
        if (guild == null || !player.getUniqueId().equals(guild.getOwner())) {
            player.sendMessage(Messages.text("guild-panel.only-leader"));
            return;
        }
        home(player, guild);
    }

    private void home(Player player, Guild guild) {
        Menu menu = new Menu(player, guild, Messages.text("guild-panel.title", guild.getTag()), 27);
        menu.button(11, Material.PLAYER_HEAD, Messages.text("guild-panel.assign"), (p, g) -> members(p, g, 0));
        menu.button(15, Material.WRITABLE_BOOK, Messages.text("guild-panel.creator"), this::ranks);
        player.openInventory(menu.inventory);
    }

    private void members(Player player, Guild guild, int page) {
        List<UUID> all = guild.getMembers().stream().sorted().toList();
        int lastPage = Math.max(0, (all.size() - 1) / PAGE_LENGTH);
        int current = Math.clamp(page, 0, lastPage);
        List<UUID> shown = all.subList(current * PAGE_LENGTH, Math.min(all.size(), (current + 1) * PAGE_LENGTH));
        Menu menu = new Menu(player, guild, Messages.text("guild-panel.members", current + 1, lastPage + 1), 54);
        for (int i = 0; i < shown.size(); i++) {
            UUID id = shown.get(i);
            var offline = Bukkit.getOfflinePlayer(id);
            setHead(menu, i, offline, offline.getName(), guild);
            if (!id.equals(guild.getOwner())) menu.actions.put(i, (p, g) -> choose(p, g, id, current));
        }
        if (current > 0) menu.button(45, Material.ARROW, Messages.text("guild-panel.previous"), (p, g) -> members(p, g, current - 1));
        if (current < lastPage) menu.button(53, Material.ARROW, Messages.text("guild-panel.next"), (p, g) -> members(p, g, current + 1));
        menu.button(49, Material.BARRIER, Messages.text("guild-panel.back"), this::home);
        player.openInventory(menu.inventory);
        if (shown.isEmpty()) return;
        plugin.getMongoDBManager().findManyAsync("users", Filters.in("uuid", shown.stream().map(UUID::toString).toList()))
                .thenAccept(profiles -> BukkitTaskScheduler.execute(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
                    Map<String, String> names = new HashMap<>();
                    for (var profile : profiles) {
                        String name = profile.getString("name");
                        if (name != null) names.put(profile.getString("uuid"), name);
                    }
                    for (int i = 0; i < shown.size(); i++) {
                        UUID id = shown.get(i);
                        String name = names.get(id.toString());
                        if (name != null) setHead(menu, i, Bukkit.getOfflinePlayer(id), name, guild);
                    }
                }));
    }

    private void setHead(Menu menu, int slot, org.bukkit.OfflinePlayer member, String name, Guild guild) {
        UUID id = member.getUniqueId();
        GuildRank rank = guild.rank(id);
        if (rank == null) return;
        ItemStack head = item(Material.PLAYER_HEAD, name == null ? id.toString() : name,
                Messages.text("guild-panel.current-rank", rank.name()));
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(member);
        head.setItemMeta(meta);
        menu.inventory.setItem(slot, head);
    }

    private void choose(Player player, Guild guild, UUID member, int page) {
        if (member.equals(guild.getOwner()) || !guild.getMembers().contains(member)) return;
        Menu menu = new Menu(player, guild, Messages.text("guild-panel.assign"), 36);
        List<GuildRank> options = new ArrayList<>(List.of(GuildRank.DEPUTY, GuildRank.MASTER, GuildRank.RECRUIT));
        options.addAll(guild.customRanks().values());
        for (int i = 0; i < options.size(); i++) {
            GuildRank rank = options.get(i);
            menu.button(i, rank == GuildRank.DEPUTY ? Material.GOLD_INGOT : Material.NAME_TAG, rank.name(), (p, g) -> {
                if (member.equals(g.getOwner()) || !g.getMembers().contains(member)) return;
                GuildRank actual = iRank(g, rank.id());
                if (actual == null) return;
                Set<UUID> changed = new HashSet<>();
                changed.add(member);
                if (actual == GuildRank.DEPUTY && g.getDeputy() != null) changed.add(g.getDeputy());
                g.assignRank(member, actual);
                save(p, g, changed, (viewer, updated) -> members(viewer, updated, page));
            }, rightsLore(rank));
        }
        menu.button(31, Material.BARRIER, Messages.text("guild-panel.back"), (p, g) -> members(p, g, page));
        player.openInventory(menu.inventory);
    }

    private static GuildRank iRank(Guild guild, String id) {
        return switch (id) {
            case "deputy" -> GuildRank.DEPUTY;
            case "master" -> GuildRank.MASTER;
            case "recruit" -> GuildRank.RECRUIT;
            default -> guild.customRanks().get(id);
        };
    }

    private void ranks(Player player, Guild guild) {
        Menu menu = new Menu(player, guild, Messages.text("guild-panel.creator"), 36);
        int slot = 0;
        for (GuildRank rank : guild.customRanks().values()) {
            menu.button(slot++, Material.BOOK, rank.name(), (p, g) -> {
                GuildRank current = g.customRanks().get(rank.id());
                if (current != null) edit(p, g, current, current);
            }, rightsLore(rank));
        }
        menu.button(30, Material.EMERALD, Messages.text("guild-panel.new"), (p, g) -> {
            if (g.customRanks().size() >= MAX_CUSTOM) {
                p.sendMessage(Messages.text("guild-panel.limit", MAX_CUSTOM));
                return;
            }
            p.closeInventory();
            NamePrompt prompt = new NamePrompt(p, g.getTag(), g.getPersistenceGeneration());
            prompts.put(p.getUniqueId(), prompt);
            Bukkit.getScheduler().runTaskLater(plugin, () -> prompts.remove(p.getUniqueId(), prompt), 600L);
            p.sendMessage(Messages.text("guild-panel.enter-name"));
        });
        menu.button(32, Material.BARRIER, Messages.text("guild-panel.back"), this::home);
        player.openInventory(menu.inventory);
    }

    private void edit(Player player, Guild guild, GuildRank rank, GuildRank original) {
        Menu menu = new Menu(player, guild, Messages.text("guild-panel.edit", rank.name()), 27);
        for (GuildRank.Right right : GuildRank.Right.values()) {
            boolean enabled = rank.rights().contains(right);
            menu.button(10 + right.ordinal(), enabled ? Material.LIME_DYE : Material.GRAY_DYE, right.label,
                    (p, g) -> edit(p, g, rank.toggle(right), original),
                    Messages.text(enabled ? "guild-panel.enabled" : "guild-panel.disabled"));
        }
        menu.button(22, Material.EMERALD, Messages.text("guild-panel.save"), (p, g) -> {
            if (!Objects.equals(g.customRanks().get(rank.id()), original)
                    || original == null && (g.customRanks().size() >= MAX_CUSTOM || g.customRanks().values().stream()
                    .anyMatch(existing -> existing.name().equalsIgnoreCase(rank.name())))) {
                p.sendMessage(Messages.text("guild-panel.changed"));
                return;
            }
            g.customRanks().put(rank.id(), rank);
            save(p, g, Set.of(), this::ranks);
        });
        menu.button(26, Material.BARRIER, Messages.text("guild-panel.back"), this::ranks);
        player.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void name(AsyncChatEvent event) {
        Player player = event.getPlayer();
        NamePrompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null || prompt.player() != player) return;
        event.setCancelled(true);
        String value = PlainTextComponentSerializer.plainText().serialize(event.message()).strip();
        BukkitTaskScheduler.execute(plugin, () -> {
            Guild guild = guilds.getGuild(prompt.tag());
            if (!player.isOnline() || !authorized(player, guild, prompt.generation())) return;
            if (value.equalsIgnoreCase("anuluj")) { ranks(player, guild); return; }
            if (!value.matches("[\\p{L}\\p{N} _-]{2,20}") || List.of(GuildRank.LEADER, GuildRank.DEPUTY, GuildRank.MASTER, GuildRank.RECRUIT)
                    .stream().anyMatch(rank -> rank.name().equalsIgnoreCase(value))
                    || guild.customRanks().values().stream().anyMatch(rank -> rank.name().equalsIgnoreCase(value))) {
                player.sendMessage(Messages.text("guild-panel.invalid-name"));
                ranks(player, guild);
                return;
            }
            String id = "r_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            edit(player, guild, new GuildRank(id, value, Set.of()), null);
        });
    }

    @EventHandler public void quit(PlayerQuitEvent event) { prompts.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick()
                || !menu.viewer.equals(player.getUniqueId())) return;
        var action = menu.actions.get(event.getRawSlot());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Guild guild = guilds.getGuild(menu.tag);
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != menu
                    || plugin.isCombatTagged(player) || plugin.getUserManager().isTransferPending(player)) return;
            if (!authorized(player, guild, menu.generation)) { player.closeInventory(); return; }
            if (guilds.isPersistencePending(menu.tag) || guilds.permissionsRefreshing(menu.tag)) {
                player.sendMessage(Messages.text("guild-panel.saving"));
                return;
            }
            action.accept(player, guild);
        });
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    private static boolean authorized(Player player, Guild guild, String generation) {
        return guild != null && Objects.equals(guild.getPersistenceGeneration(), generation)
                && player.getUniqueId().equals(guild.getOwner()) && guild.getMembers().contains(player.getUniqueId());
    }

    private void save(Player player, Guild guild, Set<UUID> changed, BiConsumer<Player, Guild> success) {
        Menu active = player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu ? menu : null;
        guilds.saveGuild(guild).whenComplete((ignored, error) -> BukkitTaskScheduler.execute(plugin, () -> {
            if (error == null) {
                for (UUID id : changed) {
                    var user = plugin.getUserManager().getUser(id);
                    if (user != null && guild.getTag().equalsIgnoreCase(user.getGuildTag())) user.setGuildRole(guild.roleOf(id));
                }
                plugin.getGuildTagManager().refreshAllOnline();
            }
            if (!player.isOnline()) return;
            player.sendMessage(Messages.text(error == null ? "guild-panel.saved" : "guild-panel.failed"));
            if (error == null && active != null && player.getOpenInventory().getTopInventory().getHolder() == active)
                success.accept(player, guild);
        }));
    }

    private static String[] rightsLore(GuildRank rank) {
        return rank.rights().stream().sorted().map(right -> right.label).toArray(String[]::new);
    }

    private static ItemStack item(Material icon, String title, String... lore) {
        ItemStack result = new ItemStack(icon);
        var meta = result.getItemMeta();
        meta.displayName(Component.text(title));
        meta.lore(Arrays.stream(lore).map(text -> (Component) Component.text(text)).toList());
        result.setItemMeta(meta);
        return result;
    }
}
