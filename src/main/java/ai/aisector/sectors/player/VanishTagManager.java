package ai.aisector.sectors.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class VanishTagManager {

    private static final String VANISHED_TEAM_NAME = "z_vanished";

    public void setVanishedTag(String targetPlayerName) {
        Bukkit.getOnlinePlayers().forEach(staff -> {
            if (staff.hasPermission("aisector.command.vanish.see")) {
                applyTagForStaff(staff, targetPlayerName, true);
            }
        });
    }

    public void removeVanishedTag(String targetPlayerName) {
        Bukkit.getOnlinePlayers().forEach(staff -> {
            if (staff.hasPermission("aisector.command.vanish.see")) {
                applyTagForStaff(staff, targetPlayerName, false);
            }
        });
    }

    public void updateTagsForJoiningStaff(Player joiningStaff) {
        for (Player vanishedPlayer : VanishManager.getVanishedPlayers()) {
            applyTagForStaff(joiningStaff, vanishedPlayer.getName(), true);
        }
    }

    public void reconcileViewer(Player viewer, java.util.Set<String> desiredNames) {
        Team team = viewer.getScoreboard().getTeam(VANISHED_TEAM_NAME);
        boolean allowed = viewer.hasPermission("aisector.command.vanish.see");
        if (team != null) {
            for (String entry : java.util.Set.copyOf(team.getEntries())) {
                if (!allowed || !desiredNames.contains(entry)) team.removeEntry(entry);
            }
        }
        if (allowed) desiredNames.forEach(name -> applyTagForStaff(viewer, name, true));
    }

    private void applyTagForStaff(Player staff, String targetPlayerName, boolean isVanished) {
        Scoreboard scoreboard = staff.getScoreboard();
        Team vanishedTeam = scoreboard.getTeam(VANISHED_TEAM_NAME);

        // Jeśli drużyna nie istnieje, stwórz ją
        if (vanishedTeam == null) {
            vanishedTeam = scoreboard.registerNewTeam(VANISHED_TEAM_NAME);
        }

        // --- KLUCZOWA ZMIANA ---
        // Ustawiamy prefix i kolor ZA KAŻDYM RAZEM, poza warunkiem 'if'.
        // To gwarantuje, że stare wartości zostaną nadpisane nowymi.
        vanishedTeam.prefix(Component.text("[V] ", NamedTextColor.RED));   // Twój nowy, czerwony kolor
        vanishedTeam.color(NamedTextColor.WHITE); // Twój nowy, biały kolor
        // --- KONIEC ZMIANY ---

        if (isVanished) {
            vanishedTeam.addEntry(targetPlayerName);
        } else {
            vanishedTeam.removeEntry(targetPlayerName);
        }
    }
}
