import ai.aisector.SectorPlugin;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.Switch;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only console fixture for isolated spawn and seam smoke. */
public class LayoutSmokePlugin extends JavaPlugin {
    private SectorPlugin sector;
    private Path output;
    private String name;
    private int spawnY;

    @Override public void onEnable() {
        sector = (SectorPlugin) Bukkit.getPluginManager().getPlugin("aiSECTOR");
        output = Path.of(System.getProperty("layout.smoke.output"));
        name = System.getProperty("layout.smoke.player");
        if (!"aisector_staging".equals(sector.getConfig().getString("mongodb.database"))
                || sector.getConfig().getInt("redis.port") != 6380 || !name.matches("RwSL[a-f0-9]{6}")
                || !output.getFileName().toString().startsWith("aisector-layout-smoke-"))
            throw new IllegalStateException("Only isolated staging permitted");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || args.length < 3 || !args[0].matches("[a-f0-9]{32}")
                || !name.equals(args[1])) return true;
        String request = args[0];
        var world = Bukkit.getWorlds().getFirst();
        Player player = Bukkit.getPlayerExact(name);
        try {
            switch (args[2]) {
                case "prepare" -> {
                    if (!sector.getSectorManager().isLockedSpawnBackend()) throw new IllegalStateException("Not Spawn");
                    spawnY = world.getHighestBlockYAt(2050, 2050) + 1;
                    world.setSpawnLocation(2050, spawnY, 2050);
                    placeButton(world.getBlockAt(2051, spawnY, 2050), Material.SPONGE);
                    placeButton(world.getBlockAt(2050, spawnY, 2052), Material.STONE);
                    reply(request, new Document("spawnY", spawnY));
                }
                case "state" -> reply(request, state(player));
                case "escape" -> player.teleportAsync(new Location(world, 2101, spawnY, 2050)).whenComplete((ok, error) ->
                        reply(request, new Document("blocked", error == null && Boolean.FALSE.equals(ok))));
                case "position" -> player.teleportAsync(new Location(world, Double.parseDouble(args[3]), spawnY,
                                Double.parseDouble(args[4]))).whenComplete((ok, error) -> reply(request,
                        Boolean.TRUE.equals(ok) ? state(player) : new Document("error", "Position failed: " + error)));
                case "seed" -> {
                    player.getInventory().clear();
                    player.getInventory().setItem(4, new ItemStack(Material.DIAMOND, 19));
                    player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
                    player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
                    player.setLevel(7);
                    reply(request, state(player).append("ordinary", "2050," + (spawnY + 1) + ",2052")
                            .append("button", "2051," + (spawnY + 1) + ",2050"));
                }
                case "crossing-setup" -> {
                    boolean west = sector.getConfig().getString("this-sector-name").equals("GameWest");
                    player.setAllowFlight(true);
                    player.setFlying(true);
                    Location at = new Location(world, west ? -0.3 : 0.3, 150, 0.5);
                    player.teleportAsync(at).whenComplete((ok, error) -> Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (!Boolean.TRUE.equals(ok)) {
                            reply(request, new Document("error", "Seam teleport failed"));
                            return;
                        }
                        var old = player.getWorldBorder();
                        var bounds = sector.getSectorManager().getSector(at.getBlockX(), at.getBlockZ());
                        sector.getWorldBorderManager().sendWorldBorder(player, bounds);
                        var border = player.getWorldBorder();
                        double wall = border == null ? Double.NaN : border.getCenter().getX() + border.getSize() / 2 * (west ? 1 : -1);
                        reply(request, new Document("border", wall == (west ? 2 : -2))
                                .append("sameBorder", old != null && old == border)
                                .append("clientTarget", west ? "0.35" : "-0.35"));
                    }, 2L));
                }
                default -> throw new IllegalArgumentException("Unknown action");
            }
        } catch (RuntimeException error) {
            reply(request, new Document("error", error.toString()));
        }
        return true;
    }

    private void placeButton(org.bukkit.block.Block support, Material material) {
        support.setType(material);
        var button = support.getRelative(org.bukkit.block.BlockFace.UP);
        var data = (Switch) Bukkit.createBlockData(Material.STONE_BUTTON);
        data.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);
        button.setBlockData(data);
    }

    private Document state(Player player) {
        if (player == null) return new Document("ready", false);
        var loc = player.getLocation();
        var floor = loc.clone().subtract(0, 1, 0).getBlock();
        return new Document("ready", sector.getUserManager().getUser(player) != null && player.getWalkSpeed() > 0)
                .append("inventory", java.util.HexFormat.of().formatHex(ItemStack.serializeItemsAsBytes(
                        player.getInventory().getContents()))).append("level", player.getLevel())
                .append("x", loc.getX()).append("y", loc.getY()).append("z", loc.getZ())
                .append("floorSafe", floor.getType().isOccluding() && !floor.isLiquid());
    }

    private void reply(String token, Document result) {
        try { Files.writeString(output.resolve(token + ".json"), result.toJson()); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
