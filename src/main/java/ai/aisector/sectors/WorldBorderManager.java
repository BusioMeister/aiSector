package ai.aisector.sectors;


import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class WorldBorderManager {

    public void sendWorldBorder(Player player, Sector sector) {
        if (player == null || !player.isOnline() || sector == null) {
            Bukkit.getLogger().warning("[WorldBorderManager] Gracz lub sektor jest nullem");
            return;
        }

        double centerX = (sector.getMinX() + sector.getMaxX()) / 2.0;
        double centerZ = (sector.getMinZ() + sector.getMaxZ()) / 2.0;
        double sizeX = sector.getMaxX() - sector.getMinX() + 1;
        double sizeZ = sector.getMaxZ() - sector.getMinZ() + 1;
        double finalSize = Math.max(sizeX, sizeZ) + 3;

        WorldBorder border = new WorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(finalSize);

        ServerLevel nmsWorld = ((CraftWorld) player.getWorld()).getHandle();
        border.world = nmsWorld;

        try {
            ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
            ClientboundInitializeBorderPacket packet = new ClientboundInitializeBorderPacket(border);
            nmsPlayer.connection.send(packet);

            Bukkit.getLogger().info("[WorldBorderManager] Border wysłany do " + player.getName()
                    + " | X: " + sector.getMinX() + " - " + sector.getMaxX()
                    + " | Z: " + sector.getMinZ() + " - " + sector.getMaxZ());
        } catch (Exception e) {
            Bukkit.getLogger().severe("[WorldBorderManager] Błąd przy wysyłaniu bordera: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
