package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.shared.RedisChannels;
import ai.aisector.database.RedisManager;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpacceptCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    private final RedisManager redisManager;
    public TpacceptCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.redisManager = plugin.getRedisManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player accepter = (Player) sender;
        if (accepter.getLocation().getWorld() == null) {
            accepter.sendMessage(Messages.text("tpaccept-command.nie-mozna-odczytac-twojej-lokalizacji"));
            return true;
        }


        JsonObject req = new JsonObject();
        req.addProperty("accepter", accepter.getName());

        Location loc = accepter.getLocation();
        JsonObject locationJson = new JsonObject();
        locationJson.addProperty("world", loc.getWorld().getName());
        locationJson.addProperty("x", loc.getX());
        locationJson.addProperty("y", loc.getY());
        locationJson.addProperty("z", loc.getZ());
        locationJson.addProperty("yaw", loc.getYaw());
        locationJson.addProperty("pitch", loc.getPitch());
        req.add("location", locationJson);

        redisManager.publishAsync(RedisChannels.TPA_ACCEPT, req.toString()).whenComplete((subscribers, error) -> {
            if (error != null || subscribers == null || subscribers == 0) {
                BukkitTaskScheduler.execute(plugin, () -> {
                    if (accepter.isOnline()) accepter.sendMessage(Messages.text("tpaccept-command.nie-udalo-sie-wyslac-akceptacji-tpa-przez"));
                });
            }
        });
        return true;
    }
}
