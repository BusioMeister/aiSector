package ai.aisector.listeners;

import ai.aisector.SectorPlugin;
import ai.aisector.ranking.AdminStatsCommand;
import ai.aisector.task.TeleportWarmupRegistry;
import ai.aisector.utils.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class CombatLogoutListener implements Listener {
    private static final String CHANNEL = "aisector:combat";
    private static final int SECONDS = 20;
    private record Tag(Player player, UUID opponent, long expiresAt) {}
    private record IncomingHit(Player victim, UUID attacker, long expiresAt) {}
    private final SectorPlugin plugin;
    private final Map<UUID, Tag> tags = new HashMap<>();
    private final Map<UUID, IncomingHit> lastIncomingHits = new HashMap<>();
    private final java.util.Set<UUID> logoutDeaths = new java.util.HashSet<>();

    public CombatLogoutListener(SectorPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getFinalDamage() <= 0) return;
        if (!(event.getDamageSource().getCausingEntity() instanceof Player attacker) || attacker.equals(victim)) return;
        long expiry = System.nanoTime() + TimeUnit.SECONDS.toNanos(SECONDS);
        lastIncomingHits.put(victim.getUniqueId(), new IncomingHit(victim, attacker.getUniqueId(), expiry));
        tag(attacker, victim, expiry);
        tag(victim, attacker, expiry);
    }

    private void tag(Player player, Player opponent, long expiry) {
        tags.put(player.getUniqueId(), new Tag(player, opponent.getUniqueId(), expiry));
        if (hasBypass(player)) return;
        TeleportWarmupRegistry.cancelWarmup(player);
        syncProxy(player, SECONDS * 1000);
        if (isCraftingTable(player.getOpenInventory().getTopInventory().getType())) player.closeInventory();
    }

    public boolean active(Player player) {
        Tag tag = tags.get(player.getUniqueId());
        return tag != null && tag.player() == player && tag.expiresAt() - System.nanoTime() > 0 && !hasBypass(player);
    }

    public boolean isLogoutDeath(Player player) {
        return logoutDeaths.contains(player.getUniqueId());
    }

    public Player lastPlayerToHit(Player victim) {
        IncomingHit hit = lastIncomingHits.get(victim.getUniqueId());
        if (hit == null || hit.victim() != victim || hit.expiresAt() - System.nanoTime() <= 0) return null;
        Player attacker = Bukkit.getPlayer(hit.attacker());
        return attacker != null && attacker.isOnline() ? attacker : null;
    }

    private boolean hasBypass(Player player) {
        var user = plugin.getUserManager().getUser(player);
        return user != null && user.hasCombatBypass();
    }

    public void refreshBypass(Player player) {
        if (!active(player)) {
            syncProxy(player, 0);
            player.sendActionBar(Component.empty());
            return;
        }
        Tag tag = tags.get(player.getUniqueId());
        int millis = (int) Math.clamp((tag.expiresAt() - System.nanoTime() + 999_999) / 1_000_000, 1, 20_000);
        TeleportWarmupRegistry.cancelWarmup(player);
        syncProxy(player, millis);
        if (isCraftingTable(player.getOpenInventory().getTopInventory().getType())) player.closeInventory();
    }

    private void tick() {
        long now = System.nanoTime();
        lastIncomingHits.values().removeIf(hit -> hit.expiresAt() - now <= 0);
        var iterator = tags.values().iterator();
        while (iterator.hasNext()) {
            Tag tag = iterator.next();
            Player player = tag.player();
            if (!player.isOnline()) {
                iterator.remove();
                continue;
            }
            long remaining = tag.expiresAt() - now;
            if (remaining <= 0) {
                iterator.remove();
                syncProxy(player, 0);
                if (!hasBypass(player)) player.sendActionBar(Component.text(Messages.text("combat.ended"), NamedTextColor.GREEN));
            } else if (!hasBypass(player)) {
                long millis = (remaining + 999_999) / 1_000_000;
                String time = String.format(Locale.ROOT, "%d.%03d", millis / 1000, millis % 1000);
                player.sendActionBar(Component.text(Messages.text("combat.countdown", time), NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastIncomingHits.remove(player.getUniqueId());
        Tag tag = tags.remove(player.getUniqueId());
        if (tag == null || tag.player() != player || tag.expiresAt() - System.nanoTime() <= 0
                || Bukkit.isStopping() || plugin.isShutdownCheckpointConfirmed() || player.isDead() || hasBypass(player)) return;
        Player opponent = Bukkit.getPlayer(tag.opponent());
        if (opponent != null && opponent.isOnline()) player.setKiller(opponent);
        logoutDeaths.add(player.getUniqueId());
        try {
            player.setHealth(0);
            if (player.isDead()) player.saveData();
        } finally {
            logoutDeaths.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (tags.remove(player.getUniqueId()) != null) {
            syncProxy(player, 0);
            player.sendActionBar(Component.empty());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void clearLastHitOnDeath(PlayerDeathEvent event) {
        lastIncomingHits.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombatDeathDrops(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.getKiller() == null && !logoutDeaths.contains(player.getUniqueId())) return;
        event.setKeepInventory(false);
        event.getItemsToKeep().clear();
        // Paper does not populate the drop list when keep-inventory is enabled.
        if (player.getWorld().getGameRuleValue(org.bukkit.GameRules.KEEP_INVENTORY) == Boolean.TRUE
                && event.getDrops().isEmpty() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            for (var item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()
                        && !item.containsEnchantment(org.bukkit.enchantments.Enchantment.VANISHING_CURSE))
                    event.getDrops().add(item.clone());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (AdminStatsCommand.isBypassCommand(event.getMessage())) return;
        if (!active(event.getPlayer())) return;
        String command = normalizedCommand(event.getMessage());
        var rules = blockedCommands();
        if (rules.stream()
                .noneMatch(rule -> rule.equals("*") || !rule.isEmpty() && (command.equals(rule) || command.startsWith(rule + " ")))) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Messages.text("combat.command-blocked"));
    }

    private static String normalizedCommand(String input) {
        String command = input.strip().replaceFirst("^/+", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        int namespace = command.indexOf(':');
        int space = command.indexOf(' ');
        return namespace >= 0 && (space < 0 || namespace < space) ? command.substring(namespace + 1) : command;
    }

    private java.util.List<String> blockedCommands() {
        var rules = plugin.getConfig().getStringList("combat.blocked-commands").stream()
                .map(CombatLogoutListener::normalizedCommand).filter(rule -> !rule.isEmpty() && rule.length() <= 64)
                .distinct().limit(128).toList();
        return rules.isEmpty() ? java.util.List.of("*") : rules;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && active(player) && isCraftingTable(event.getInventory().getType())) {
            event.setCancelled(true);
            player.sendMessage(Messages.text("combat.crafting-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !active(player)) return;
        if (event.getSlotType() == InventoryType.SlotType.CRAFTING || event.getSlotType() == InventoryType.SlotType.RESULT
                && event.getView().getTopInventory() instanceof org.bukkit.inventory.CraftingInventory
                || isCraftingTable(event.getView().getTopInventory().getType())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !active(player)) return;
        if (event.getView().getTopInventory() instanceof org.bukkit.inventory.CraftingInventory
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())
                || isCraftingTable(event.getView().getTopInventory().getType())) event.setCancelled(true);
    }

    private static boolean isCraftingTable(InventoryType type) {
        return type == InventoryType.WORKBENCH || type == InventoryType.CRAFTER;
    }

    private void syncProxy(Player player, int millis) {
        if (!player.getListeningPluginChannels().contains(CHANNEL)) return;
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            var rules = millis == 0 ? java.util.List.<String>of() : blockedCommands();
            out.writeInt(millis);
            out.writeShort(rules.size());
            for (String rule : rules) out.writeUTF(rule);
        } catch (IOException error) {
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
    }
}
