package ai.aisector.guilds;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class GuildRegionListener implements Listener {

    private final GuildManager guildManager;

    public GuildRegionListener(SectorPlugin plugin) {
        this.guildManager = plugin.getGuildManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!guildDataReady(e.getPlayer())) {
            e.setCancelled(true);
            return;
        }
        var blocks = e instanceof org.bukkit.event.block.BlockMultiPlaceEvent multi
                ? multi.getReplacedBlockStates().stream().map(org.bukkit.block.BlockState::getLocation).toList()
                : java.util.List.of(e.getBlock().getLocation());
        if (blocks.stream().anyMatch(block -> !allows(e.getPlayer(), block, GuildRank.Right.PLACE))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Messages.text("guild-region-listener.nie-mozesz-budowac-na-terenie-tej-gildii"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!guildDataReady(e.getPlayer())) {
            e.setCancelled(true);
            return;
        }
        if (!allows(e.getPlayer(), e.getBlock().getLocation(), GuildRank.Right.BREAK)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Messages.text("guild-region-listener.nie-mozesz-niszczyc-na-terenie-tej-gildii"));
        }
    }

    private boolean guildDataReady(Player player) {
        if (guildManager.isReady()) return true;
        player.sendMessage(Messages.text("guild-region-listener.dane-gildii-sa-ladowane-sprobuj-ponownie-za"));
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (!guildDataReady(event.getPlayer()) || !allows(event.getPlayer(), event.getBlock().getLocation(), GuildRank.Right.PLACE))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
        if (!guildDataReady(event.getPlayer()) || !allows(event.getPlayer(), event.getBlock().getLocation(), GuildRank.Right.BREAK))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplosion(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(block -> protectedBlock(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplosion(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(block -> protectedBlock(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        if (crossesTerritory(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        if (crossesTerritory(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(org.bukkit.event.block.BlockFromToEvent event) {
        if (!guildManager.isReady() || !sameTerritories(event.getBlock(), event.getToBlock())) event.setCancelled(true);
    }

    private boolean crossesTerritory(org.bukkit.block.Block piston, java.util.List<org.bukkit.block.Block> blocks,
                                     org.bukkit.block.BlockFace direction) {
        if (!guildManager.isReady() || !sameTerritories(piston, piston.getRelative(direction))) return true;
        return blocks.stream().anyMatch(b -> !sameTerritories(piston, b) || !sameTerritories(b, b.getRelative(direction)));
    }

    private boolean sameTerritories(org.bukkit.block.Block first, org.bukkit.block.Block second) {
        Location a = first.getLocation(), b = second.getLocation();
        return guildManager.getAllGuilds().stream().noneMatch(guild -> contains(guild, a) != contains(guild, b));
    }

    private boolean protectedBlock(Location loc) {
        return !guildManager.isReady() || guildManager.getAllGuilds().stream().anyMatch(guild -> contains(guild, loc));
    }

    private boolean allows(Player player, Location loc, GuildRank.Right right) {
        return guildManager.getAllGuilds().stream().filter(guild -> contains(guild, loc))
                .allMatch(guild -> !guildManager.isPersistencePending(guild.getTag())
                        && !guildManager.permissionsRefreshing(guild.getTag()) && guild.allows(player.getUniqueId(), right));
    }

    private static boolean contains(Guild guild, Location loc) {
        World w = loc.getWorld();
        return w != null && guild.territory() != null && guild.territory().contains(w.getName(), loc.getBlockX(), loc.getBlockZ());
    }
}
