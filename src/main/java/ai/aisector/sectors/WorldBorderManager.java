package ai.aisector.sectors;

import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.Location;

public class WorldBorderManager {

    // Definiuje rozmiar "bańki", w której gracz się porusza.
    private static final double BORDER_VIEW_SIZE = 200.0;

    // 🔥 NOWOŚĆ: Definiuje 2-blokowy bufor, który pozwala graczom swobodnie przechodzić.
    private static final double CROSSING_BUFFER = 2.0;

    /**
     * Wysyła do gracza pakiet z dynamiczną, "pływającą" granicą świata,
     * która jest zablokowana w granicach aktualnego sektora.
     *
     * @param player Gracz, do którego ma zostać wysłana granica.
     * @param sector Sektor, w którym gracz się znajduje.
     */
    public void sendWorldBorder(Player player, Sector sector) {
        if (player == null || !player.isOnline() || sector == null) {
            return;
        }

        Location playerLoc = player.getLocation();
        double halfSize = BORDER_VIEW_SIZE / 2.0;

        double potentialCenterX = playerLoc.getX();
        double potentialCenterZ = playerLoc.getZ();

        // Zablokuj (clamp) pozycję środka granicy, uwzględniając bufor na przejście.
        // Krawędzie granicy będą teraz o 'CROSSING_BUFFER' szersze niż sam sektor.
        // 👇 ZMIANA TUTAJ:
        double clampedCenterX = Math.max(sector.getMinX() + halfSize - CROSSING_BUFFER, Math.min(sector.getMaxX() - halfSize + CROSSING_BUFFER, potentialCenterX));
        // 👇 I ZMIANA TUTAJ:
        double clampedCenterZ = Math.max(sector.getMinZ() + halfSize - CROSSING_BUFFER, Math.min(sector.getMaxZ() - halfSize + CROSSING_BUFFER, potentialCenterZ));

        // Tworzenie obiektu WorldBorder
        WorldBorder border = new WorldBorder();
        border.setCenter(clampedCenterX, clampedCenterZ);
        border.setSize(BORDER_VIEW_SIZE);
        border.setWarningBlocks(1);
        border.setWarningTime(3);

        ServerLevel nmsWorld = ((CraftWorld) player.getWorld()).getHandle();
        border.world = nmsWorld;

        try {
            ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
            ClientboundInitializeBorderPacket packet = new ClientboundInitializeBorderPacket(border);
            nmsPlayer.connection.send(packet);

        } catch (Exception e) {
            Bukkit.getLogger().severe("[WorldBorderManager] Błąd przy wysyłaniu bordera: " + e.getMessage());
            e.printStackTrace();
        }
    }
}