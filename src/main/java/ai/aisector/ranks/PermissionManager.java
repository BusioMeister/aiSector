package ai.aisector.ranks;

import ai.aisector.SectorPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionManager {

    private final SectorPlugin plugin;
    private final RankManager rankManager;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final List<String> availablePermissions = new ArrayList<>();

    public PermissionManager(SectorPlugin plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        loadPermissionsFromFile();
    }

    public void applyPlayerPermissions(Player player) {
        removePlayerPermissions(player);
        Rank rank = rankManager.getPlayerRank(player.getUniqueId());
        if (rank == null) return;

        Set<String> permissions = rankManager.getEffectivePermissions(rank);
        if (permissions.isEmpty()) return;

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);
        for (String permission : permissions) {
            attachment.setPermission(permission, true);
        }
        player.recalculatePermissions();
        plugin.getLogger().info("Zastosowano " + permissions.size() + " efektywnych permisji dla " + player.getName());
    }

    public void removePlayerPermissions(Player player) {
        if (attachments.containsKey(player.getUniqueId())) {
            try {
                player.removeAttachment(attachments.remove(player.getUniqueId()));
            } catch (Exception e) {
                // Ignorujemy, gracz mógł już wyjść z serwera
            }
        }
    }

    public void loadPermissionsFromFile() {
        availablePermissions.clear();
        File permissionsFile = new File(plugin.getDataFolder(), "permissions.yml");
        if (!permissionsFile.exists()) {
            plugin.getLogger().warning("Plik permissions.yml nie został znaleziony! GUI z permisjami będzie puste.");
            return;
        }
        FileConfiguration permissionsConfig = YamlConfiguration.loadConfiguration(permissionsFile);
        availablePermissions.addAll(permissionsConfig.getStringList("permissions"));
    }

    public List<String> getAvailablePermissions() {
        return availablePermissions;
    }
}