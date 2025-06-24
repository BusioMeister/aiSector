package ai.aisector;

import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

public class WorldBorderManager {

    public void sendWorldBorder(Player player, double centerX, double centerZ, double size) {
        // Tworzymy nowy border
        WorldBorder border = Bukkit.createWorldBorder();

        // Ustawienia borderu
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamageAmount(0.2);
        border.setDamageBuffer(5);
        border.setWarningDistance(15);
        border.setWarningTime(15);

        // Ustawiamy border dla konkretnego gracza
        player.setWorldBorder(border);
    }
}

