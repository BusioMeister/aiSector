package ai.aisector;

import ai.aisector.commands.*;
import ai.aisector.database.RedisManager;
import ai.aisector.listeners.PlayerDataSaveListener;
import ai.aisector.player.*;
import ai.aisector.sectors.BorderInitListener;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class SectorPlugin extends JavaPlugin {

    private SectorManager sectorManager;
    private RedisManager redisManager;
    private WorldBorderManager worldBorderManager;
    private VanishManager vanishManager;
    private BukkitTask publisherTask;
    private final Map<UUID, String> playerDeathSectors = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Inicjalizacja managerów
        redisManager = new RedisManager("localhost", 6379);
        sectorManager = new SectorManager(redisManager);
        worldBorderManager = new WorldBorderManager();
        vanishManager = new VanishManager(this);

        // Rejestracja komend
        getCommand("enderchest").setExecutor(new EnderchestCommand());
        getCommand("ci").setExecutor(new ClearInventoryCommand());
        getCommand("repair").setExecutor(new RepairCommand());
        getCommand("naprawkilof").setExecutor(new NaprawKilofCommand());
        getCommand("gamemode").setExecutor(new GameModeCommand());
        getCommand("weather").setExecutor(new WeatherCommand(redisManager));
        getCommand("time").setExecutor(new TimeCommand(redisManager));



        getCommand("alert").setExecutor(new AlertCommand(redisManager));
        getCommand("fly").setExecutor(new FlyCommand());
        getCommand("speed").setExecutor(new SpeedCommand());
        getCommand("heal").setExecutor(new HealCommand());
        getCommand("god").setExecutor(new GodCommand());
        getCommand("wyjebane").setExecutor(new WyjebaneCommand(redisManager));
        getCommand("v").setExecutor(new VanishCommand(redisManager));
        getCommand("spawn").setExecutor(new SpawnCommand(this, redisManager, sectorManager, worldBorderManager));
        getCommand("sectorinfo").setExecutor(new SectorInfoCommand(redisManager));
        getCommand("setspawnsector").setExecutor(new SetSpawnSectorCommand(sectorManager, redisManager));
        getCommand("tp").setExecutor(new TpCommand(this, redisManager, sectorManager, worldBorderManager));
        getCommand("s").setExecutor(new SummonCommand(this, redisManager, sectorManager, worldBorderManager));

        getCommand("tpa").setExecutor(new TpaCommand(redisManager));
        getCommand("tpaccept").setExecutor(new TpacceptCommand(redisManager));
        getCommand("sektor").setExecutor(new SektorCommand(redisManager));
        getCommand("send").setExecutor(new SendCommand(redisManager));

        getCommand("tp").setTabCompleter(new TpTabCompleter());
        getCommand("s").setTabCompleter(new TpTabCompleter());
        getCommand("tpa").setTabCompleter(new TpTabCompleter());
        getCommand("sektor").setTabCompleter(new TpTabCompleter());

        String thisSectorName = getConfig().getString("this-sector-name");
        if(thisSectorName != null && !thisSectorName.isEmpty()){
            new SectorStatsPublisher(this, redisManager, thisSectorName).runTaskTimerAsynchronously(this, 100L, 100L); // co 5 sekund
        }
        // Rejestracja listenerów eventów Bukkit
        getServer().getPluginManager().registerEvents(new GodCommand(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(sectorManager, redisManager, worldBorderManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, sectorManager, redisManager, worldBorderManager), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this, redisManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new VanishPlayerListener(vanishManager, redisManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this, sectorManager), this);
        getServer().getPluginManager().registerEvents(new GuiClickListener(), this);

        new GlobalChatPlugin(this).register();

        // Uruchomienie zadań cyklicznych
        new ActionBarTask().runTaskTimer(this, 0L, 20L);
        startRedisListeners(); // Uruchomienie wszystkich listenerów Redis w osobnych wątkach
        startPlayerPublisher(); // Uruchomienie wysyłania listy graczy
    }

    private void startRedisListeners() {
        // Listener dla komend i odpowiedzi
        new Thread(() -> {
            try (Jedis jedis = redisManager.getJedis()) {
                jedis.subscribe(new CommandResponseListener(this, sectorManager, worldBorderManager),
                        "aisector:tp_execute_local",
                        "aisector:send_message",
                        "aisector:alert",
                        "aisector:tpa_initiate_warmup",
                        "aisector:tp_execute_local_tpa",
                        "aisector:global_weather_change",
                        "aisector:global_time_change");
            }
        }, "Redis-Command-Listener-Thread").start();
        PlayerDataSaveListener dataSaveListener = new PlayerDataSaveListener(this, redisManager);
        new Thread(dataSaveListener, "RedisPlayerDataSaveThread").start();
        // Listener dla listy graczy
        new Thread(() -> {
            try (Jedis jedis = redisManager.getJedis()) {
                jedis.subscribe(new GlobalPlayerListListener(), "aisector:global_playerlist_update");
            }
        }, "Redis-PlayerList-Listener-Thread").start();

        // Listener dla Vanisha
        new Thread(() -> {
            try (Jedis jedis = redisManager.getJedis()) {
                jedis.subscribe(new VanishUpdateListener(this, vanishManager), "aisector:vanish_update", "aisector:admin_chat");
            }
        }, "Redis-Vanish-Listener-Thread").start();

        new Thread(() -> {
            try (Jedis jedis = redisManager.getJedis()) {
                jedis.psubscribe(new GuiDataListener(this, sectorManager), "aisector:gui_data_response:*");
            }
        }).start();
        // Listener dla Borderów
        List<String> channels = sectorManager.getSECTORS().stream()
                .map(sector -> "sector-border-init:" + sector.getName())
                .collect(Collectors.toList());
        new Thread(() -> {
            try (Jedis jedis = redisManager.getJedis()) {
                jedis.subscribe(new BorderInitListener(sectorManager, this), channels.toArray(new String[0]));
            }
        }, "Redis-Border-Listener-Thread").start();
    }

    private void startPlayerPublisher() {
        String thisSectorName = getConfig().getString("this-sector-name");
        if (thisSectorName == null || thisSectorName.isEmpty()) {
            getLogger().severe("Nie ustawiono 'this-sector-name' w config.yml!");
        } else {
            this.publisherTask = new OnlinePlayersPublisherTask(redisManager, thisSectorName).runTaskTimer(this, 0L, 100L);
            getLogger().info("Uruchomiono raportowanie graczy dla sektora: " + thisSectorName);
        }
    }

    @Override
    public void onDisable() {
        if (this.publisherTask != null && !this.publisherTask.isCancelled()) {
            this.publisherTask.cancel();
        }
        if (redisManager != null) {
            redisManager.unsubscribe(); // To powinno zamknąć wszystkie subskrypcje
            redisManager.closePool();
        }
    }

    // Gettery
    public Map<UUID, String> getPlayerDeathSectors() { return playerDeathSectors; }
    public RedisManager getRedisManager() { return redisManager; }
    public SectorManager getSectorManager() { return sectorManager; }
}