package ai.aisector;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class SectorPlugin extends JavaPlugin {
    private SectorManager sectorManager;
    private RedisManager redisManager;

    @Override
    public void onEnable() {

        // Inicjalizacja połączenia z Redis
        redisManager = new RedisManager("127.0.0.1", 6379); // Parametry połączenia z Redis

        // Tworzenie menedżera sektorów
        sectorManager = new SectorManager(redisManager);

        // Rejestracja nasłuchiwacza zdarzeń
        PlayerListener playerListener = new PlayerListener(sectorManager, redisManager);

        // Rejestracja nasłuchiwaczy zdarzeń
        getServer().getPluginManager().registerEvents(new ProtocolListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this,sectorManager, redisManager), this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getCommand("sectorinfo").setExecutor(new SectorInfoCommand(sectorManager));



    }

    @Override
    public void onDisable() {
        // Zamykanie połączenia z Redis
        if (redisManager != null) {
            redisManager.closePool();
        }
    }
}
