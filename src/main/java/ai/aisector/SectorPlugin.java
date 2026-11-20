package ai.aisector;

import ai.aisector.cobblex.CobbleXListener;
import ai.aisector.cobblex.CobbleXManager;
import ai.aisector.shared.RedisChannels;
import ai.aisector.commands.*;
import ai.aisector.database.MongoDBManager;
import ai.aisector.database.MySQLManager;
import ai.aisector.database.RedisManager;
import ai.aisector.drop.DropGuiListener;
import ai.aisector.drop.StoneDropListener;
import ai.aisector.generators.GeneratorManager;
import ai.aisector.guilds.GuildManager;
import ai.aisector.guilds.GuildTagManager;
import ai.aisector.listeners.*;
import ai.aisector.shared.packet.*;
import ai.aisector.redis.packet.JsonPacketCodec;
import ai.aisector.redis.packet.PacketBus;
import ai.aisector.redis.packet.PacketRegistry;
import ai.aisector.redis.packet.RedisPacketPublisher;
import ai.aisector.sectors.player.*;
import ai.aisector.sectors.BorderInitListener;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.SectorStatsPublisher;
import ai.aisector.sectors.WorldBorderManager;
import ai.aisector.skills.MiningLevelManager;
import ai.aisector.task.ActionBarTask;
import ai.aisector.task.LocalTeleportWarmupTask;
import ai.aisector.task.OnlinePlayersPublisherTask;
import ai.aisector.user.UserManager;
import ai.aisector.scoreboard.ScoreboardManager;

import ai.aisector.utils.GlobalChatPlugin;
import ai.aisector.utils.BukkitTaskScheduler;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SectorPlugin extends JavaPlugin {

    private SectorManager sectorManager;
    private RedisManager redisManager;
    private WorldBorderManager worldBorderManager;
    private VanishManager vanishManager;
    private BukkitTask publisherTask;
    private final Map<UUID, String> playerDeathSectors = new HashMap<>();
    private final Map<UUID, ai.aisector.ranking.DeathNotice> deathNotices = new HashMap<>();
    private MongoDBManager mongoDBManager;
    private UserManager userManager;
    private volatile MySQLManager mySQLManager;
    private final Object mySQLLifecycleLock = new Object();
    private boolean disabling;
    private final ExecutorService schematicLoader = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "aiSector-schematic-loader");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Set<CompletableFuture<Void>> pendingSchematicPastes = ConcurrentHashMap.newKeySet();
    private ScoreboardManager scoreboardManager;
    private GuildManager guildManager;
    private RedisPacketPublisher packetPublisher;


    private MiningLevelManager miningLevelManager;
    private ai.aisector.generators.GeneratorManager generatorManager;
    private CobbleXManager cobbleXManager;
    private GuildTagManager guildTagManager;


    @Override
    public void onEnable() {
        saveDefaultConfig();
        ai.aisector.utils.RuntimeConfigValidation.validate(getConfig().getValues(true));
        if (!new File(getDataFolder(), "permissions.yml").exists()) {
            saveResource("permissions.yml", false);
        }
        getDataFolder().mkdirs();

        File schemFolder = new File(getDataFolder(), "schematics");
        if (!schemFolder.exists()) schemFolder.mkdirs();

        File cxFile = new File(getDataFolder(), "cobblex.yml");
        if (!cxFile.exists()) {
            saveResource("cobblex.yml", false);
        }
        // Inicjalizacja managerów
        if (getConfig().getBoolean("mysql.enabled", false)) {
            String host = getConfig().getString("mysql.host", "127.0.0.1");
            int port = getConfig().getInt("mysql.port", 3306);
            String database = getConfig().getString("mysql.database", "minecraft");
            String username = getConfig().getString("mysql.username", "minecraft");
            String password = getConfig().getString("mysql.password", "");
            initializeMySQLAsync(host, port, database, username, password);
        }

        mongoDBManager = new MongoDBManager(
                getConfig().getString("mongodb.uri", "mongodb://127.0.0.1:27017"),
                getConfig().getString("mongodb.database", "users")
        );
        this.generatorManager = new GeneratorManager(this);
        this.cobbleXManager = new CobbleXManager(this);

        redisManager = new RedisManager(
                getConfig().getString("redis.host", "127.0.0.1"),
                getConfig().getInt("redis.port", 6379),
                getConfig().getString("redis.password", ""),
                message -> getLogger().warning(message)
        );
        sectorManager = new SectorManager(this, redisManager);
        worldBorderManager = new WorldBorderManager(sectorManager::isLockedSpawnBackend);

        userManager = new UserManager(this);
        scoreboardManager = new ScoreboardManager(this);
        vanishManager = new VanishManager(this);
        miningLevelManager = new MiningLevelManager(this); // Przeniesiono tutaj!
        guildManager = new GuildManager(this);
        guildTagManager = new GuildTagManager(this, userManager, guildManager, redisManager);
        guildManager.whenReady().whenComplete((ignored, error) -> {
            if (error != null) {
                getLogger().severe("Guild cache is unavailable: " + error.getMessage());
                return;
            }
            try {
                getServer().getScheduler().runTask(this, () -> guildTagManager.refreshAllOnline());
            } catch (RuntimeException ignoredShutdown) {
                // The plugin may be shutting down while the initial guild cache is loading.
            }
        });
        PacketRegistry.register(GuildTagUpdatePacket.ID, new JsonPacketCodec<>(GuildTagUpdatePacket.class));

// 2) listener, który odpali applyGuildTags
        PacketBus.register(GuildTagUpdatePacket.ID, packet -> {
            GuildTagUpdatePacket p = (GuildTagUpdatePacket) packet;
            Player viewer = Bukkit.getPlayer(UUID.fromString(p.viewerUuid));
            if (viewer != null) {
                guildTagManager.applyGuildTags(viewer, p.playerData);
            }
        });

        // Rejestracja komend
        getCommand("pastetest").setExecutor(new PasteTestCommand(this));
        getCommand("enderchest").setExecutor(new EnderchestCommand());
        getCommand("ci").setExecutor(new ClearInventoryCommand());
        getCommand("repair").setExecutor(new RepairCommand());
        getCommand("naprawkilof").setExecutor(new NaprawKilofCommand());
        getCommand("gamemode").setExecutor(new GameModeCommand());
        getCommand("weather").setExecutor(new WeatherCommand(this));
        getCommand("time").setExecutor(new TimeCommand(this));
        getCommand("tppos").setExecutor(new TpposCommand(this));
        getCommand("backup").setExecutor(new BackupCommand(this, mongoDBManager));
        var statsCommand = new ai.aisector.ranking.AdminStatsCommand(this);
        getCommand("astats").setExecutor(statsCommand);
        getCommand("astats").setTabCompleter(statsCommand);
        getCommand("sethome").setExecutor(new SetHomeCommand(this, mongoDBManager));
        getCommand("home").setExecutor(new HomeCommand(this, mongoDBManager));
        getCommand("lvl").setExecutor(new LevelCommand(this,miningLevelManager));
        getCommand("ct").setExecutor(new CraftingCommand());
        getCommand("ban").setExecutor(new BanCommand(this));
        getCommand("banip").setExecutor(new BanIpCommand(this));
        getCommand("unban").setExecutor(new UnbanCommand(this));
        getCommand("banlist").setExecutor(new BanListCommand(this));
        getCommand("kick").setExecutor(new KickCommand(this));



        getCommand("alert").setExecutor(new AlertCommand(this));
        getCommand("fly").setExecutor(new FlyCommand(this));
        getCommand("speed").setExecutor(new SpeedCommand(this));
        getCommand("heal").setExecutor(new HealCommand());
        getCommand("god").setExecutor(new GodCommand(this));
        getCommand("wyjebane").setExecutor(new WyjebaneCommand(this, redisManager));
        getCommand("v").setExecutor(new VanishCommand(this,vanishManager));
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("drop").setExecutor(new DropCommand(this));
        getCommand("cx").setExecutor(new ai.aisector.cobblex.CobbleXCommand(this));
        getCommand("gildia").setExecutor(new ai.aisector.guilds.GuildCommand(this));
        getCommand("ga").setExecutor(new ai.aisector.guilds.GuildAdminCommand(this));

        getCommand("sectorinfo").setExecutor(new SectorInfoCommand(this));
        getCommand("setspawnsector").setExecutor(new SetSpawnSectorCommand(this));
        getCommand("tp").setExecutor(new TpCommand(this));
        getCommand("s").setExecutor(new SummonCommand(this));
        getCommand("invsee").setExecutor(new InvseeCommand());
        getServer().getPluginManager().registerEvents(new InvseeGuiListener(), this);


        getCommand("tpa").setExecutor(new TpaCommand(this));
        getCommand("tpaccept").setExecutor(new TpacceptCommand(this));
        getCommand("sektor").setExecutor(new SektorCommand(this));
        getCommand("send").setExecutor(new SendCommand(this));
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
            new SectorStatsPublisher(this, redisManager, thisSectorName).runTaskTimer(this, 100L, 100L); // co 5 sekund
        }
        // Rejestracja listenerów eventów Bukkit
        getServer().getPluginManager().registerEvents(new CobbleXListener(this, cobbleXManager), this);
        getServer().getPluginManager().registerEvents(new ai.aisector.guilds.GuildRegionListener(this), this);
        getServer().getPluginManager().registerEvents(new UserDataListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        var tabList = new ai.aisector.tab.NetworkTabList(this, miningLevelManager);
        getServer().getPluginManager().registerEvents(tabList, this);
        tabList.start();
        getServer().getPluginManager().registerEvents(new ai.aisector.sectors.LockedSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiClickListener(), this);
        getServer().getPluginManager().registerEvents(new StoneDropListener(this, miningLevelManager), this);
        getServer().getPluginManager().registerEvents(new DropGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this, redisManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new VanishPlayerListener(this,vanishManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this, mongoDBManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new BackupGuiListener(this, mongoDBManager, redisManager), this);
        getServer().getPluginManager().registerEvents(new SetHomeGuiListener(this, mongoDBManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new HomeGuiListener(mongoDBManager, sectorManager, this), this);
        getServer().getPluginManager().registerEvents(new ai.aisector.guilds.GuildPvpListener(this), this);
        this.packetPublisher = new RedisPacketPublisher(redisManager);
        registerPackets();





        getServer().getPluginManager().registerEvents(
                new ai.aisector.listeners.GeneratorListener(this, this.generatorManager), this);
        ai.aisector.generators.GeneratorItems.registerRecipes(this);
        getServer().getScheduler().runTask(this, () -> this.generatorManager.loadAll());
        getServer().getScheduler().runTask(this, sectorManager::warmGameSeam);
        new GlobalChatPlugin(this).register();
        new ActionBarTask().runTaskTimer(this, 0L, 20L);
        startRedisListeners(); // Uruchomienie wszystkich listenerów Redis w osobnych wątkach
        startPlayerPublisher(); // Uruchomienie wysyłania listy graczy
    }

    private void initializeMySQLAsync(String host, int port, String database, String username, String password) {
        Thread initializer = new Thread(() -> {
            MySQLManager manager = null;
            try {
                synchronized (mySQLLifecycleLock) {
                    if (disabling) return;
                }
                manager = new MySQLManager(host, port, database, username, password);
                manager.createTables();
                synchronized (mySQLLifecycleLock) {
                    if (disabling) {
                        manager.close();
                        manager = null;
                    } else {
                        mySQLManager = manager;
                        manager = null;
                    }
                }
            } catch (RuntimeException exception) {
                getLogger().severe("Could not initialize optional MySQL storage: " + exception.getMessage());
            } finally {
                if (manager != null) manager.close();
            }
        }, "aiSector-mysql-initializer");
        initializer.setDaemon(true);
        initializer.start();
    }

    private void startRedisListeners() {
        // Listener dla komend i odpowiedzi
        String thisSectorName = getConfig().getString("this-sector-name");
        if (thisSectorName == null || thisSectorName.isBlank()) {
            getLogger().severe("Brak this-sector-name w config.yml; listener komend nie został uruchomiony.");
            return;
        }

        redisManager.subscribe(new CommandResponseListener(this, sectorManager, worldBorderManager),
                RedisChannels.PACKET,
                RedisChannels.packetForServer(thisSectorName),
                RedisChannels.TP_EXECUTE_LOCAL,
                RedisChannels.GLOBAL_WEATHER_CHANGE,
                RedisChannels.GLOBAL_TIME_CHANGE,
                RedisChannels.GET_LOCATION_FOR_ADMIN_TP,
                RedisChannels.SAVE_PLAYER_DATA);
        redisManager.subscribe(new GlobalPlayerListListener(), RedisChannels.GLOBAL_PLAYER_LIST);
        redisManager.subscribe(new VanishUpdateListener(this, vanishManager),
                RedisChannels.VANISH_UPDATE, "aisector:admin_chat");
        redisManager.subscribe(new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                try {
                    ai.aisector.guilds.GuildDeletionNotice invalidation =
                            ai.aisector.guilds.GuildDeletionNotice.parse(message);
                    String tag = invalidation.tag();
                    String generation = invalidation.generation();
                    guildManager.refreshAfterRemoteDeletion(tag, generation).whenComplete((guild, error) -> {
                        BukkitTaskScheduler.execute(SectorPlugin.this, () -> {
                            if (error != null) {
                                getLogger().warning("Could not apply remote guild deletion for " + tag + ": "
                                        + error.getMessage());
                                return;
                            }
                            if (guild == null) userManager.clearGuildMembership(tag);
                            guildTagManager.refreshAllOnline();
                        });
                    });
                } catch (RuntimeException exception) {
                    getLogger().warning("Ignoring invalid guild deletion message: " + exception.getMessage());
                }
            }
        }, RedisChannels.GUILD_DELETED);
        redisManager.psubscribe(new GuiDataListener(this, sectorManager), RedisChannels.GUI_DATA_RESPONSE_PATTERN);

        List<String> channels = sectorManager.getSECTORS().stream()
                .map(sector -> "sector-border-init:" + sector.getName())
                .collect(Collectors.toList());
        if (!channels.isEmpty()) {
            redisManager.subscribe(new BorderInitListener(sectorManager, this, worldBorderManager),
                    channels.toArray(new String[0]));
        }
    }

    private void startPlayerPublisher() {
        String thisSectorName = getConfig().getString("this-sector-name");
        if (thisSectorName == null || thisSectorName.isEmpty()) {
            getLogger().severe("Nie ustawiono 'this-sector-name' w config.yml!");
        } else {
            this.publisherTask = new OnlinePlayersPublisherTask(this, redisManager, thisSectorName)
                    .runTaskTimer(this, 0L, 100L);
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
        PacketRegistry.register(2, new JsonPacketCodec<>(SendMessagePacket.class));
        PacketBus.register(2, (SendMessagePacket p) -> {
            Player player = Bukkit.getPlayer(p.playerName);
            if (player != null)
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(p.message));


        });

        PacketRegistry.register(3, new JsonPacketCodec<>(AlertPacket.class));
        PacketBus.register(3, (AlertPacket p) -> {
            Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(p.message));
        });
        PacketRegistry.register(4, new JsonPacketCodec<>(LocalTpaTeleportPacket.class));
        PacketBus.register(4, (LocalTpaTeleportPacket p) -> {

            Player playerToTeleport = Bukkit.getPlayer(p.playerToTeleportName);
            if (playerToTeleport == null) return;

            World world = Bukkit.getWorld(p.world);
            if (world == null) return;

            Location targetLocation = new Location(world, p.x, p.y, p.z, p.yaw, p.pitch);

            new LocalTeleportWarmupTask(
                    playerToTeleport,
                    targetLocation,
                    (p.message != null && !p.message.isEmpty()) ? p.message : "§aZostałeś przeteleportowany.",
                    this.sectorManager,
                    this.worldBorderManager,
                    this
            ).start();
        });

    }

    @Override
    public void onDisable() {
        MySQLManager mysqlToClose;
        synchronized (mySQLLifecycleLock) {
            disabling = true;
            mysqlToClose = mySQLManager;
            mySQLManager = null;
        }
        IllegalStateException schematicShutdown = new IllegalStateException("Plugin disabled during schematic paste");
        pendingSchematicPastes.forEach(future -> future.completeExceptionally(schematicShutdown));
        shutdownStep("schematic loader", schematicLoader::shutdownNow);
        shutdownStep("teleport warmups", ai.aisector.task.TeleportWarmupRegistry::clearAll);
        if (sectorManager != null) shutdownStep("seam chunk tickets", sectorManager::stopWarmingGameSeam);

        if (this.publisherTask != null && !this.publisherTask.isCancelled()) {
            shutdownStep("online player publisher", this.publisherTask::cancel);
        }
        if (worldBorderManager != null) {
            shutdownStep("world border cache", worldBorderManager::clearAll);
        }
        if (userManager != null) {
            shutdownStep("online user snapshots", userManager::saveOnlineUsers);
            shutdownStep("user persistence queue", userManager::close);
        }
        if (guildManager != null) shutdownStep("guild persistence queue", guildManager::saveAll);
        if (this.generatorManager != null) {
            shutdownStep("generator persistence queue", this.generatorManager::saveAll);
        }
        if (mysqlToClose != null) shutdownStep("MySQL", mysqlToClose::close);
        if (redisManager != null) shutdownStep("Redis", redisManager::close);
        if (mongoDBManager != null) shutdownStep("MongoDB", mongoDBManager::close);
    }

    private void shutdownStep(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            getLogger().severe("Shutdown step '" + step + "' failed; continuing cleanup: "
                    + exception.getMessage());
        }
    }




    // Gettery
    public void pasteSchematic(Player player, String schematicName) throws Exception {
        pasteSchematic(player.getLocation(), schematicName);
    }

    public void pasteSchematic(Location location, String schematicName) throws Exception {
        Class<?> pasterClass;
        try {
            pasterClass = Class.forName("ai.aisector.utils.SchematicPaster");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Schematic support is unavailable. Install a compatible WorldEdit build.", exception);
        }
        Object paster = pasterClass.getConstructor(File.class)
                .newInstance(new File(getDataFolder(), "schematics"));
        pasterClass.getMethod("pasteSchem", Location.class, String.class)
                .invoke(paster, location, schematicName);
    }

    /** Reads schematic data off-thread, then performs the Bukkit/WorldEdit paste on the server thread. */
    public CompletableFuture<Void> pasteSchematicAsync(Location location, String schematicName) {
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Schematic target must have a world"));
        }
        if (!getServer().getPluginManager().isPluginEnabled("WorldEdit")) {
            return CompletableFuture.failedFuture(new IllegalStateException("WorldEdit is not enabled"));
        }

        final Class<?> pasterClass;
        final Object paster;
        final java.lang.reflect.Method readMethod;
        final java.lang.reflect.Method pasteMethod;
        final Location target = location.clone();
        try {
            pasterClass = Class.forName("ai.aisector.utils.SchematicPaster");
            paster = pasterClass.getConstructor(File.class)
                    .newInstance(new File(getDataFolder(), "schematics"));
            readMethod = pasterClass.getMethod("readSchem", String.class);
            pasteMethod = java.util.Arrays.stream(pasterClass.getMethods())
                    .filter(method -> method.getName().equals("pasteClipboard")
                            && method.getParameterCount() == 2
                            && method.getParameterTypes()[0] == Location.class)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("pasteClipboard(Location, Clipboard)"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Schematic support is unavailable. Install a compatible WorldEdit build.", exception));
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        pendingSchematicPastes.add(result);
        result.whenComplete((ignored, error) -> pendingSchematicPastes.remove(result));

        CompletableFuture<Object> loaded;
        try {
            loaded = CompletableFuture.supplyAsync(() -> {
                try {
                    return readMethod.invoke(paster, schematicName);
                } catch (ReflectiveOperationException exception) {
                    throw new java.util.concurrent.CompletionException(exception.getCause() == null
                            ? exception : exception.getCause());
                }
            }, schematicLoader);
        } catch (RejectedExecutionException exception) {
            result.completeExceptionally(exception);
            return result;
        }

        loaded.whenComplete((clipboard, loadError) -> {
            if (result.isDone()) return;
            try {
                getServer().getScheduler().runTask(this, () -> {
                    if (result.isDone()) return;
                    if (loadError != null) {
                        result.completeExceptionally(unwrapCompletionError(loadError));
                        return;
                    }
                    try {
                        pasteMethod.invoke(paster, target, clipboard);
                        result.complete(null);
                    } catch (ReflectiveOperationException exception) {
                        result.completeExceptionally(exception.getCause() == null ? exception : exception.getCause());
                    }
                });
            } catch (RuntimeException schedulerError) {
                result.completeExceptionally(schedulerError);
            }
        });
        return result;
    }

    private Throwable unwrapCompletionError(Throwable error) {
        Throwable current = error;
        while (current instanceof java.util.concurrent.CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
    public Map<UUID, String> getPlayerDeathSectors() { return playerDeathSectors; }
    public Map<UUID, ai.aisector.ranking.DeathNotice> getDeathNotices() { return deathNotices; }
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
    public GuildTagManager getGuildTagManager() {return guildTagManager;}
}
