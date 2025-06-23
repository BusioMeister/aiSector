package ai.aisector;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class ProtocolListener implements Listener {

    private SectorManager sectorManager;
    private RedisManager redisManager;


    public void onPacketSending(PacketEvent packetEvent) {
        if (packetEvent.getPacket().getType() == PacketType.Play.Server.WORLD_BORDER){
            Player player = packetEvent.getPlayer();
            int playerX = player.getLocation().getBlockX();
            int playerZ = player.getLocation().getBlockZ();

            String sectorName = sectorManager.getSectorForLocation(playerX,playerZ);
            if (!sectorName.isEmpty()){
                Sector sector = getSectorByName(sectorName);
                if (sector != null){
                    setBorderForSector(player,sector);
                }
            }
        }
    }
    private void setBorderForSector(Player player, Sector sector) {
        PacketContainer packetContainer = new PacketContainer(PacketType.Play.Server.WORLD_BORDER);
        packetContainer.getDoubles().write(0, (sector.getMaxX()) + sector.getMaxX() / 2.0);
        packetContainer.getDoubles().write(1, (sector.getMinZ() + sector.getMaxZ()) / 2.0); // Środek sektora (Z)
        packetContainer.getIntegers().write(0, (int) (sector.getMaxX() - sector.getMinX())); // Promień sektora
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player,packetContainer);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private Sector getSectorByName(String sectorName) {
        return sectorManager.getSECTORS().stream()
                .filter(sector -> sector.getName().equals(sectorName))
                .findFirst()
                .orElse(null);
    }
}
