package ai.aisector.task;

import ai.aisector.SectorPlugin;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class GuildHomeTeleportWarmupTask {

    private final BaseTeleportWarmupTask task;

    public GuildHomeTeleportWarmupTask(Player player, String targetSector, JsonObject targetLocationJson, SectorPlugin plugin) {
        World world = Bukkit.getWorld(targetLocationJson.get("world").getAsString());
        Location targetLocation = new Location(
                world,
                targetLocationJson.get("x").getAsDouble(),
                targetLocationJson.get("y").getAsDouble(),
                targetLocationJson.get("z").getAsDouble(),
                targetLocationJson.get("yaw").getAsFloat(),
                targetLocationJson.get("pitch").getAsFloat()
        );

        this.task = new BaseTeleportWarmupTask(
                player,
                targetLocation,
                targetLocationJson.get("world").getAsString(),
                targetSector,
                5,
                ai.aisector.utils.Messages.text("teleport.guild-countdown"),
                ai.aisector.utils.Messages.text("teleport.stay-still"),
                ai.aisector.utils.Messages.text("teleport.guild-arrived"),
                "player:final_teleport_target:" + player.getUniqueId(),
                60,
                plugin
        );
    }

    public void start() {
        task.start();
    }
}
