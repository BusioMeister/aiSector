package ai.aisector.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQLManager {

    private HikariDataSource dataSource;

    public MySQLManager(String host, int port, String database, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(user);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        this.dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // Ta metoda stworzy potrzebne tabele w bazie, jeśli jeszcze nie istnieją.
    public void createTables() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE IF NOT EXISTS ranks (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(50) NOT NULL UNIQUE," +
                    "prefix VARCHAR(50)," +
                    "chat_color VARCHAR(20)," +
                    "weight INT NOT NULL DEFAULT 1," +
                    "is_default BOOLEAN NOT NULL DEFAULT FALSE," +
                    // --- NOWA KOLUMNA ---
                    "parent_rank_id INT NULL," + // Przechowuje ID rangi-rodzica
                    "FOREIGN KEY (parent_rank_id) REFERENCES ranks(id) ON DELETE SET NULL" + // Zabezpieczenie
                    ")");

            // Tabela przechowująca dane graczy (UUID -> Ranga)
            statement.execute("CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "last_known_nickname VARCHAR(16) NOT NULL," +
                    "rank_id INT NOT NULL," +
                    "first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (rank_id) REFERENCES ranks(id)" +
                    ")");

            // Tabela łącząca rangi z permisjami
            statement.execute("CREATE TABLE IF NOT EXISTS rank_permissions (" +
                    "rank_id INT NOT NULL," +
                    "permission_node VARCHAR(255) NOT NULL," +
                    "PRIMARY KEY (rank_id, permission_node)," +
                    "FOREIGN KEY (rank_id) REFERENCES ranks(id)" +
                    ")");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}