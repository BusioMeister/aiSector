package ai.aisector.ranks;

import ai.aisector.database.MySQLManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class RankManager {

    private final MySQLManager mySQLManager;
    private final Logger logger;
    private final Map<Integer, Rank> ranksByIdCache = new ConcurrentHashMap<>();
    private final Map<String, Rank> ranksByNameCache = new ConcurrentHashMap<>();
    private Rank defaultRank;

    public RankManager(MySQLManager mySQLManager, Logger logger) {
        this.mySQLManager = mySQLManager;
        this.logger = logger;
    }

    public void loadRanks() {
        ranksByIdCache.clear();
        ranksByNameCache.clear();
        String query = "SELECT * FROM ranks";
        try (Connection connection = mySQLManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                Integer parentId = resultSet.getObject("parent_rank_id", Integer.class);
                Rank rank = new Rank(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("prefix"),
                        resultSet.getString("chat_color"),
                        resultSet.getInt("weight"),
                        resultSet.getBoolean("is_default"),
                        parentId
                );
                ranksByIdCache.put(rank.getId(), rank);
                ranksByNameCache.put(rank.getName().toLowerCase(), rank);
                if (rank.isDefault()) {
                    this.defaultRank = rank;
                }
            }
            loadPermissionsForAllRanks();
            logger.info("Załadowano " + ranksByIdCache.size() + " rang z bazy danych.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Rank getPlayerRank(UUID uuid) {
        String query = "SELECT r.name FROM ranks r JOIN player_data pd ON r.id = pd.rank_id WHERE pd.uuid = ?";
        try (Connection connection = mySQLManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setString(1, uuid.toString());
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                String rankName = resultSet.getString("name");
                return getRank(rankName); // Zawsze zwracaj obiekt z cache'u
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return defaultRank;
    }

    public boolean playerExistsInRankDB(UUID uuid) {
        String query = "SELECT uuid FROM player_data WHERE uuid = ?";
        try (Connection connection = mySQLManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            logger.severe("Błąd podczas sprawdzania istnienia gracza w bazie rang: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void loadPermissionsForAllRanks() {
        String query = "SELECT rank_id, permission_node FROM rank_permissions";
        try (Connection connection = mySQLManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                int rankId = resultSet.getInt("rank_id");
                String permission = resultSet.getString("permission_node");
                Rank rank = ranksByIdCache.get(rankId);
                if (rank != null) {
                    rank.getPermissions().add(permission);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Set<String> getDirectPermissions(Rank rank) {
        return rank.getPermissions();
    }

    public Set<String> getEffectivePermissions(Rank rank) {
        Set<String> effectivePermissions = new HashSet<>();
        if (rank == null) return effectivePermissions;
        effectivePermissions.addAll(rank.getPermissions());
        Integer parentId = rank.getParentRankId();
        while (parentId != null) {
            Rank parentRank = getRankById(parentId);
            if (parentRank != null) {
                effectivePermissions.addAll(parentRank.getPermissions());
                parentId = parentRank.getParentRankId();
            } else {
                break;
            }
        }
        return effectivePermissions;
    }

    public void addPermissionToRank(Rank rank, String permission) {
        String query = "INSERT IGNORE INTO rank_permissions (rank_id, permission_node) VALUES (?, ?)";
        try (Connection connection = mySQLManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, rank.getId());
            statement.setString(2, permission);
            statement.executeUpdate();
            rank.getPermissions().add(permission); // Natychmiastowa aktualizacja cache'u
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removePermissionFromRank(Rank rank, String permission) {
        String query = "DELETE FROM rank_permissions WHERE rank_id = ? AND permission_node = ?";
        try (Connection connection = mySQLManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, rank.getId());
            statement.setString(2, permission);
            statement.executeUpdate();
            rank.getPermissions().remove(permission); // Natychmiastowa aktualizacja cache'u
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void reloadPermissionsForRank(Rank rank) {
        rank.getPermissions().clear();
        String query = "SELECT permission_node FROM rank_permissions WHERE rank_id = ?";
        try (Connection connection = mySQLManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, rank.getId());
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                rank.getPermissions().add(rs.getString("permission_node"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setPlayerRank(UUID uuid, Rank rank, String playerName) {
        String query = "INSERT INTO player_data (uuid, last_known_nickname, rank_id) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE rank_id = ?, last_known_nickname = ?";
        try (Connection connection = mySQLManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setInt(3, rank.getId());
            statement.setInt(4, rank.getId());
            statement.setString(5, playerName);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Rank getRank(String name) { return ranksByNameCache.get(name.toLowerCase()); }
    public Rank getRankById(int id) { return ranksByIdCache.get(id); }
    public Rank getDefaultRank() { return defaultRank; }
}