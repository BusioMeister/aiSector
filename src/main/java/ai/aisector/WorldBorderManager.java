package ai.aisector;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

public class WorldBorderManager {

    public void sendWorldBorder(Player player, double centerX, double centerZ, double size) {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        WorldBorder worldBorder = Bukkit.getWorld(player.getLocation().getWorld().getName()).getWorldBorder();
        worldBorder.setCenter(centerX,centerZ);
        worldBorder.setSize(size);
        player.setWorldBorder(worldBorder);

        /*try {
            // 1. Tworzenie pakietu ustawiającego środek worldborder
            PacketContainer centerPacket = protocolManager.createPacket(PacketType.Play.Server.SET_BORDER_CENTER);
            centerPacket.getWorldBorderActions().write(0, EnumWrappers.WorldBorderAction.SET_CENTER);
            centerPacket.getDoubles().write(0, centerX); // Środek X
            centerPacket.getDoubles().write(1, centerZ); // Środek Z
            protocolManager.sendServerPacket(player, centerPacket);

            // 2. Tworzenie pakietu ustawiającego rozmiar worldborder
            PacketContainer sizePacket = protocolManager.createPacket(PacketType.Play.Server.SET_BORDER_SIZE);
            sizePacket.getWorldBorderActions().write(0, EnumWrappers.WorldBorderAction.SET_SIZE);
            sizePacket.getDoubles().write(0, size+1); // Rozmiar borderu
            protocolManager.sendServerPacket(player, sizePacket);

            // 3. (Opcjonalnie) Ustawienie czasu animacji zmiany rozmiaru
            PacketContainer transitionPacket = protocolManager.createPacket(PacketType.Play.Server.SET_BORDER_LERP_SIZE);
            transitionPacket.getWorldBorderActions().write(0, EnumWrappers.WorldBorderAction.LERP_SIZE);
            transitionPacket.getDoubles().write(0, size); // Rozmiar początkowy
            transitionPacket.getDoubles().write(1, size); // Rozmiar końcowy
            transitionPacket.getLongs().write(0, 0L); // Czas animacji (w milisekundach)
            protocolManager.sendServerPacket(player, transitionPacket);

        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }
}
