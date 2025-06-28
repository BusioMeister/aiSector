package ai.aisector;

import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DynamicBorderTask extends BukkitRunnable {

    private final Player player;
    private final double minX, maxX, minZ, maxZ;
    private final double activationDistance;

    private boolean borderActive = false;

    public DynamicBorderTask(Player player, double minX, double maxX, double minZ, double maxZ, double activationDistance) {
        this.player = player;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.activationDistance = activationDistance;
    }

    @Override
    public void run() {
        if (player == null || !player.isOnline()) {
            this.cancel();
            return;
        }

        Location loc = player.getLocation();
        double px = loc.getX();
        double pz = loc.getZ();

        double dx = Math.max(0, Math.max(minX - px, px - maxX));
        double dz = Math.max(0, Math.max(minZ - pz, pz - maxZ));
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance <= activationDistance) {
            if (!borderActive) {
                showBorderToPlayer();
                borderActive = true;
            }
        } else {
            if (borderActive) {
                hideBorderForPlayer();
                borderActive = false;
            }
        }
    }

    private void showBorderToPlayer() {
        double centerX = (minX + maxX) / 2;
        double centerZ = (minZ + maxZ) / 2;
        double sizeX = maxX - minX;
        double sizeZ = maxZ - minZ;
        double borderSize = Math.max(sizeX, sizeZ);
        Bukkit.getLogger().info("[DEBUG] Tworzony border: centerX=" + centerX + ", centerZ=" + centerZ + ", size=" + borderSize);

        WorldBorder border = new WorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);
        border.setDamagePerBlock(0.2);
        border.setDamageSafeZone(1);
        border.setWarningBlocks(5);
        border.setWarningTime(5);

        sendBorderPacket(border);
    }

    private void hideBorderForPlayer() {
        WorldBorder border = new WorldBorder();
        border.setCenter(0, 0);
        border.setSize(60000000); // Usunięcie borderu
        sendBorderPacket(border);
    }

    private void sendBorderPacket(WorldBorder border) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();

        border.world = nmsPlayer.serverLevel(); // 🔧 Tu poprawiamy

        ClientboundInitializeBorderPacket packet = new ClientboundInitializeBorderPacket(border);
        nmsPlayer.connection.send(packet);
    }

}
