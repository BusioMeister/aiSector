package ai.aisector.task;

import ai.aisector.SectorPlugin;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class HomeTeleportWarmupTask {

    private final BaseTeleportWarmupTask task;

    public HomeTeleportWarmupTask(Player player, String targetSector, JsonObject targetLocationJson, int homeSlot, SectorPlugin plugin) {
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
                targetSector,
                5,
                "§aTeleportacja do domu za: §e",
                "§cNie ruszaj się! §7Teleportacja za §e",
                "§aPrzeteleportowano do domu #" + homeSlot + "!",
                "player:final_teleport_target:" + player.getUniqueId(),
                60,
                plugin
        );
    }

    public void start() {
        task.start();
    }
}
