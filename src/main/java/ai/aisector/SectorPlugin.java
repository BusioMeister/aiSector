package ai.aisector;

import org.bukkit.plugin.java.JavaPlugin;

public class SectorPlugin extends JavaPlugin {

    private SectorManager sectorManager;
    private RedisManager redisManager;

    @Override
    public void onEnable() {
        redisManager = new RedisManager("127.0.0.1", 6379);
        sectorManager = new SectorManager(redisManager);
        WorldBorderManager borderManager = new WorldBorderManager();

        PlayerListener playerListener = new PlayerListener(sectorManager, redisManager, borderManager);
        PlayerJoinListener playerJoinListener = new PlayerJoinListener(this, sectorManager, redisManager, borderManager);

        getServer().getPluginManager().registerEvents(playerJoinListener, this);
        getServer().getPluginManager().registerEvents(playerListener, this);

        getCommand("sectorinfo").setExecutor(new SectorInfoCommand(sectorManager));
    }

    @Override
    public void onDisable() {
        if (redisManager != null) {
            redisManager.closePool();
        }
    }
}
