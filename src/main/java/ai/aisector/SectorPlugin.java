package ai.aisector;

import ai.aisector.cobblex.CobbleXListener;
import ai.aisector.cobblex.CobbleXManager;
import ai.aisector.commands.*;
import ai.aisector.database.MongoDBManager;
import ai.aisector.database.MySQLManager;
import ai.aisector.database.RedisManager;
import ai.aisector.drop.DropGuiListener;
import ai.aisector.drop.StoneDropListener;
import ai.aisector.generators.GeneratorManager;
import ai.aisector.guilds.GuildManager;
import ai.aisector.listeners.*;
import ai.aisector.redis.packet.JsonPacketCodec;
import ai.aisector.redis.packet.PacketBus;
import ai.aisector.redis.packet.PacketRegistry;
import ai.aisector.redis.packet.RedisPacketPublisher;
import ai.aisector.redis.packet.impl.TpaInitiateWarmupPacket;
import ai.aisector.sectors.player.*;
import ai.aisector.sectors.BorderInitListener;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.SectorStatsPublisher;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.skills.MiningLevelManager;
import ai.aisector.task.ActionBarTask;
import ai.aisector.task.OnlinePlayersPublisherTask;
import ai.aisector.user.UserManager;
import ai.aisector.scoreboard.ScoreboardManager;

import ai.aisector.utils.GlobalChatPlugin;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import redis.clients.jedis.Jedis;

import java.io.File;
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
    private ScoreboardManager scoreboardManager;
    private GuildManager guildManager;
    private RedisPacketPublisher packetPublisher;


    private MySQLManager mySQLManager; // <-- DODAJ POLE
    private MiningLevelManager miningLevelManager;
    private ai.aisector.generators.GeneratorManager generatorManager;
    private CobbleXManager cobbleXManager;



    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("permissions.yml", false); // <-- DODAJ TĘ LINIĘ
        getDataFolder().mkdirs();
        File cxFile = new File(getDataFolder(), "cobblex.yml");
        if (!cxFile.exists()) {
            saveResource("cobblex.yml", false);
        }
        // Inicjalizacja managerów
        mySQLManager = new MySQLManager("localhost", 3306, "minecraft", "root", "root");
        mySQLManager.createTables(); // Tworzy tabele, jeśli nie istnieją

        mongoDBManager = new MongoDBManager("mongodb://localhost:27017", "users"); // Użyj poprawnej nazwy bazy danych, jeśli jest inna
        this.generatorManager = new GeneratorManager(this);
        this.cobbleXManager = new CobbleXManager(this);

        redisManager = new RedisManager("localhost", 6379);
        sectorManager = new SectorManager(redisManager);
        worldBorderManager = new WorldBorderManager();

        userManager = new UserManager(this);
        scoreboardManager = new ScoreboardManager(this);
        vanishManager = new VanishManager(this);
        miningLevelManager = new MiningLevelManager(this); // Przeniesiono tutaj!
        guildManager = new GuildManager(this);


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
        getCommand("ct").setExecutor(new CraftingCommand());
        getCommand("ban").setExecutor(new BanCommand(this));
        getCommand("banip").setExecutor(new BanIpCommand(this));
        getCommand("unban").setExecutor(new UnbanCommand(this));
        getCommand("banlist").setExecutor(new BanListCommand(this));
        getCommand("kick").setExecutor(new KickCommand(this));



        getCommand("alert").setExecutor(new AlertCommand(redisManager));
        getCommand("fly").setExecutor(new FlyCommand(this));
        getCommand("speed").setExecutor(new SpeedCommand(this));
        getCommand("heal").setExecutor(new HealCommand());
        getCommand("god").setExecutor(new GodCommand(this));
        getCommand("wyjebane").setExecutor(new WyjebaneCommand(redisManager));
        getCommand("v").setExecutor(new VanishCommand(this,vanishManager));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("drop").setExecutor(new DropCommand(this));
        getCommand("cx").setExecutor(new ai.aisector.cobblex.CobbleXCommand(this));
        getCommand("gildia").setExecutor(new ai.aisector.guilds.GuildCommand(this));
        getCommand("ga").setExecutor(new ai.aisector.guilds.GuildAdminCommand(this));

        getCommand("sectorinfo").setExecutor(new SectorInfoCommand(redisManager));
        getCommand("setspawnsector").setExecutor(new SetSpawnSectorCommand(sectorManager, redisManager));
        getCommand("tp").setExecutor(new TpCommand(this));
        getCommand("s").setExecutor(new SummonCommand(this));
        getCommand("invsee").setExecutor(new InvseeCommand());
        getServer().getPluginManager().registerEvents(new InvseeGuiListener(), this);


        getCommand("tpa").setExecutor(new TpaCommand(redisManager));
        getCommand("tpaccept").setExecutor(new TpacceptCommand(redisManager));
        getCommand("sektor").setExecutor(new SektorCommand(redisManager));
        getCommand("send").setExecutor(new SendCommand(this));
        getCommand("lvl").setExecutor(new LevelCommand(this, miningLevelManager)); // Ta linia jest teraz bezpieczna
        getCommand("vserver").setExecutor(new ServerCommand(this));

        getCommand("tp").setTabCompleter(new TpTabCompleter());
        getCommand("s").setTabCompleter(new TpTabCompleter());
        getCommand("tpa").setTabCompleter(new TpTabCompleter());
        getCommand("sektor").setTabCompleter(new TpTabCompleter());

        getCommand("ban").setTabCompleter(new TpTabCompleter());
        getCommand("banip").setTabCompleter(new TpTabCompleter());
        getCommand("unban").setTabCompleter(new TpTabCompleter());
        getCommand("kick").setTabCompleter(new TpTabCompleter());
        String thisSectorName = getConfig().getString("this-sector-name");
        if(thisSectorName != null && !thisSectorName.isEmpty()){
            new SectorStatsPublisher(this, redisManager, thisSectorName).runTaskTimerAsynchronously(this, 100L, 100L); // co 5 sekund
        }
        // Rejestracja listenerów eventów Bukkit
        getServer().getPluginManager().registerEvents(new CobbleXListener(this, cobbleXManager), this);

        getServer().getPluginManager().registerEvents(new UserDataListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiClickListener(), this);
        getServer().getPluginManager().registerEvents(new StoneDropListener(this, miningLevelManager), this);
        getServer().getPluginManager().registerEvents(new DropGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this, redisManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new VanishPlayerListener(this,vanishManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this, mongoDBManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new BackupGuiListener(mongoDBManager, redisManager), this);
        getServer().getPluginManager().registerEvents(new SetHomeGuiListener(mongoDBManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new HomeGuiListener(mongoDBManager, sectorManager, this), this);
        getServer().getPluginManager().registerEvents(new ai.aisector.guilds.GuildPvpListener(this), this);
        this.packetPublisher = new RedisPacketPublisher(redisManager);
        registerPackets();





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
                        "player:force_sector_spawn:"
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
    private void registerPackets() {
        PacketRegistry.register(1, new JsonPacketCodec<>(TpaInitiateWarmupPacket.class));

        PacketBus.register(1, (TpaInitiateWarmupPacket p) -> {
            Player requester = Bukkit.getPlayer(p.requesterName);
            if (requester == null) return;

            JsonObject targetLocation = new JsonObject();
            targetLocation.addProperty("world", p.world);
            targetLocation.addProperty("x", p.x);
            targetLocation.addProperty("y", p.y);
            targetLocation.addProperty("z", p.z);
            targetLocation.addProperty("yaw", p.yaw);
            targetLocation.addProperty("pitch", p.pitch);

            new ai.aisector.task.TeleportWarmupTask(requester, targetLocation, p.targetServerName, this).start();
        });
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
    public MySQLManager getMySQLManager() {
        return mySQLManager;
    }
    public MiningLevelManager getSkillsManager() {return miningLevelManager;}
    public MongoDBManager getMongoDBManager() {
        return mongoDBManager;
    }
    public WorldBorderManager getWorldBorderManager() {
        return this.worldBorderManager;
    }
    public UserManager getUserManager() {
        return this.userManager;
    }
    public ai.aisector.cobblex.CobbleXManager getCobbleXManager() { return cobbleXManager; }
    public ScoreboardManager getScoreboardManager() {return scoreboardManager;}
    public GuildManager getGuildManager() {
        return guildManager;
    }

}