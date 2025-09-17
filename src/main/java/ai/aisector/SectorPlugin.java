package ai.aisector;

import ai.aisector.commands.*;
import ai.aisector.database.MongoDBManager;
import ai.aisector.database.MySQLManager;
import ai.aisector.database.RedisManager;
import ai.aisector.generators.GeneratorItems;
import ai.aisector.generators.GeneratorManager;
import ai.aisector.listeners.*;
import ai.aisector.sectors.player.*;
import ai.aisector.ranks.PermissionManager;
import ai.aisector.ranks.RankManager;
import ai.aisector.ranks.gui.PermissionGuiListener;
import ai.aisector.sectors.BorderInitListener;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.SectorStatsPublisher;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.skills.MiningLevelManager;
import ai.aisector.task.ActionBarTask;
import ai.aisector.task.OnlinePlayersPublisherTask;
import ai.aisector.user.UserManager;

import ai.aisector.utils.GlobalChatPlugin;
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
    private MongoDBManager mongoDBManager;
    private UserManager userManager;

    private MySQLManager mySQLManager; // <-- DODAJ POLE
    private RankManager rankManager; // <-- DODAJ POLE
    private PermissionManager permissionManager; // <-- DODAJ POLE
    private MiningLevelManager miningLevelManager;
    private ai.aisector.generators.GeneratorManager generatorManager;


    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("permissions.yml", false); // <-- DODAJ TĘ LINIĘ

        // Inicjalizacja managerów
        mySQLManager = new MySQLManager("localhost", 3306, "minecraft", "root", "root");
        mySQLManager.createTables(); // Tworzy tabele, jeśli nie istnieją

        mongoDBManager = new MongoDBManager("mongodb://localhost:27017", "users"); // Użyj poprawnej nazwy bazy danych, jeśli jest inna
        rankManager = new RankManager(mySQLManager, getLogger());
        permissionManager = new PermissionManager(this, rankManager);
        this.generatorManager = new GeneratorManager(this);

        redisManager = new RedisManager("localhost", 6379);
        sectorManager = new SectorManager(redisManager);
        worldBorderManager = new WorldBorderManager();
        userManager = new UserManager(this);
        vanishManager = new VanishManager(this);
        miningLevelManager = new MiningLevelManager(this); // Przeniesiono tutaj!
        rankManager.loadRanks();


        // Rejestracja komend
        getCommand("enderchest").setExecutor(new EnderchestCommand());
        getCommand("ci").setExecutor(new ClearInventoryCommand());
        getCommand("repair").setExecutor(new RepairCommand());
        getCommand("naprawkilof").setExecutor(new NaprawKilofCommand());
        getCommand("gamemode").setExecutor(new GameModeCommand());
        getCommand("weather").setExecutor(new WeatherCommand(redisManager));
        getCommand("time").setExecutor(new TimeCommand(redisManager));
        getCommand("tppos").setExecutor(new TpposCommand(this));
        getCommand("backup").setExecutor(new BackupCommand(mongoDBManager));
        getCommand("sethome").setExecutor(new SetHomeCommand(mongoDBManager));
        getCommand("home").setExecutor(new HomeCommand(mongoDBManager));
        getCommand("lvl").setExecutor(new LevelCommand(this,miningLevelManager));


        getCommand("alert").setExecutor(new AlertCommand(redisManager));
        getCommand("fly").setExecutor(new FlyCommand(this));
        getCommand("speed").setExecutor(new SpeedCommand(this));
        getCommand("heal").setExecutor(new HealCommand());
        getCommand("god").setExecutor(new GodCommand(this));
        getCommand("wyjebane").setExecutor(new WyjebaneCommand(redisManager));
        getCommand("v").setExecutor(new VanishCommand(this,vanishManager));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("drop").setExecutor(new DropCommand(this));

        getCommand("sectorinfo").setExecutor(new SectorInfoCommand(redisManager));
        getCommand("setspawnsector").setExecutor(new SetSpawnSectorCommand(sectorManager, redisManager));
        getCommand("tp").setExecutor(new TpCommand(this));
        getCommand("s").setExecutor(new SummonCommand(this));
        getCommand("invsee").setExecutor(new InvseeCommand());
        getServer().getPluginManager().registerEvents(new InvseeGuiListener(), this);

        getCommand("rang").setExecutor(new RankCommand(this));

        getCommand("tpa").setExecutor(new TpaCommand(redisManager));
        getCommand("tpaccept").setExecutor(new TpacceptCommand(redisManager));
        getCommand("sektor").setExecutor(new SektorCommand(redisManager));
        getCommand("send").setExecutor(new SendCommand(this));
        getCommand("lvl").setExecutor(new LevelCommand(this, miningLevelManager)); // Ta linia jest teraz bezpieczna

        getCommand("tp").setTabCompleter(new TpTabCompleter());
        getCommand("s").setTabCompleter(new TpTabCompleter());
        getCommand("tpa").setTabCompleter(new TpTabCompleter());
        getCommand("sektor").setTabCompleter(new TpTabCompleter());

        String thisSectorName = getConfig().getString("this-sector-name");
        if(thisSectorName != null && !thisSectorName.isEmpty()){
            new SectorStatsPublisher(this, redisManager, thisSectorName).runTaskTimerAsynchronously(this, 100L, 100L); // co 5 sekund
        }
        // Rejestracja listenerów eventów Bukkit

        getServer().getPluginManager().registerEvents(new UserDataListener(this), this);
        getServer().getPluginManager().registerEvents(new PermissionGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiClickListener(), this);
        getServer().getPluginManager().registerEvents(new RankListener(this), this); // <-- DODAJ REJESTRACJĘ NOWEGO LISTNERA
        getServer().getPluginManager().registerEvents(new StoneDropListener(this, miningLevelManager), this);
        getServer().getPluginManager().registerEvents(new DropGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this, redisManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new VanishPlayerListener(this,vanishManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this, mongoDBManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new BackupGuiListener(mongoDBManager, redisManager), this);
        getServer().getPluginManager().registerEvents(new SetHomeGuiListener(mongoDBManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new HomeGuiListener(mongoDBManager, sectorManager, this), this);


        getServer().getPluginManager().registerEvents(
                new ai.aisector.listeners.GeneratorListener(this, this.generatorManager), this);
        ai.aisector.generators.GeneratorItems.registerRecipes(this);
        getServer().getScheduler().runTask(this, () -> this.generatorManager.loadAll());
        new GlobalChatPlugin(this).register();
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
                        "aisector:global_time_change",
                        "aisector:get_location_for_tp",
                        "aisector:get_location_for_admin_tp",
                        "aisector:save_player_data",
                        "player:force_sector_spawn:",
                        "aisector:rank_update",
                        "aisector:rank_permissions_update"
                );
            }
        }, "Redis-Command-Listener-Thread").start();
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
                // 🔥 POPRAWKA: Przekazujemy 'worldBorderManager' jako trzeci argument 🔥
                jedis.subscribe(new BorderInitListener(sectorManager, this, worldBorderManager), channels.toArray(new String[0]));
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
        if (mySQLManager != null) {
            mySQLManager.close(); // Zamykamy połączenie z bazą danych
        }
        if (this.generatorManager != null) {
            this.generatorManager.saveAll();
        }
    }

    // Gettery
    public Map<UUID, String> getPlayerDeathSectors() { return playerDeathSectors; }
    public RedisManager getRedisManager() { return redisManager; }
    public SectorManager getSectorManager() { return sectorManager; }
    public RankManager getRankManager() {
        return rankManager;
    }
    public MySQLManager getMySQLManager() {
        return mySQLManager;
    }
    public MiningLevelManager getSkillsManager() {return miningLevelManager;}
    public PermissionManager getPermissionManager() {
        return permissionManager;
    }
    public MongoDBManager getMongoDBManager() {
        return mongoDBManager;
    }
    public WorldBorderManager getWorldBorderManager() {
        return this.worldBorderManager;
    }
    public UserManager getUserManager() {
        return this.userManager;
    }
}