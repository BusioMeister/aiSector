package ai.aisector;

import ai.aisector.commands.*;
import ai.aisector.database.RedisManager;
import ai.aisector.player.*;
import ai.aisector.sectors.BorderInitListener;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import org.bukkit.plugin.java.JavaPlugin;
import ai.aisector.player.PlayerDeathListener;
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


    private final Map<UUID, String> playerDeathSectors = new HashMap<>();

    @Override
    public void onEnable() {
        // Ta linia powinna być na samej górze, aby config był dostępny od razu
        saveDefaultConfig();

        redisManager = new RedisManager("localhost", 6379);
        sectorManager = new SectorManager(redisManager);
        worldBorderManager = new WorldBorderManager();
        this.vanishManager = new VanishManager(this);

        // Rejestracja listenerów
        getCommand("fly").setExecutor(new FlyCommand());
        getCommand("speed").setExecutor(new SpeedCommand());
        getCommand("heal").setExecutor(new HealCommand());
        getCommand("god").setExecutor(new GodCommand());
        getCommand("wyjebane").setExecutor(new WyjebaneCommand(redisManager));
        getCommand("v").setExecutor(new VanishCommand(redisManager));
        getCommand("spawn").setExecutor(new SpawnCommand(this, redisManager, sectorManager, worldBorderManager));
        getCommand("sectorinfo").setExecutor(new SectorInfoCommand(sectorManager));
        getCommand("setspawnsector").setExecutor(new SetSpawnSectorCommand(sectorManager, redisManager));
        getCommand("tp").setTabCompleter(new TpTabCompleter());
        getCommand("tp").setExecutor(new TpCommand(this, redisManager, sectorManager, worldBorderManager));
        getCommand("s").setExecutor(new SummonCommand(this, redisManager, sectorManager, worldBorderManager));
        getCommand("s").setTabCompleter(new TpTabCompleter());

        getServer().getPluginManager().registerEvents(new GodCommand(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(sectorManager, redisManager,worldBorderManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, sectorManager, redisManager, worldBorderManager), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this, redisManager, sectorManager), this);
        getServer().getPluginManager().registerEvents(new VanishPlayerListener(vanishManager, redisManager), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this, sectorManager), this);


        redisManager.subscribe(new CommandResponseListener(this), "aisector:tp_execute_local", "aisector:send_message");
        redisManager.subscribe(new GlobalPlayerListListener(), "aisector:global_playerlist_update");

        new Thread(() -> {
            try (Jedis jedis = redisManager.getJedis()) {
                jedis.subscribe(new VanishUpdateListener(this, vanishManager), "aisector:vanish_update", "aisector:admin_chat");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Redis-Vanish-Subscriber-Thread").start();
        new ActionBarTask().runTaskTimer(this, 0L, 20L); // 20L = 1 sekunda


        // Odczyt nazwy sektora i uruchomienie JEDNEGO zadania raportującego
        String thisSectorName = getConfig().getString("this-sector-name");

        if (thisSectorName == null || thisSectorName.isEmpty()) {
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().severe("Nie ustawiono 'this-sector-name' w config.yml!");
            getLogger().severe("Plugin nie będzie poprawnie raportował graczy.");
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        } else {
            // Uruchom tylko JEDNO zadanie dla TEGO sektora
            new OnlinePlayersPublisherTask(redisManager, thisSectorName).runTaskTimer(this, 0L, 100L); // 100 ticks = 5 sekund
            getLogger().info("Uruchomiono raportowanie graczy dla sektora: " + thisSectorName);
        }

        // Reszta Twojej logiki
        List<String> channels = sectorManager.getSECTORS().stream()
                .map(sector -> "sector-border-init:" + sector.getName())
                .collect(Collectors.toList());

        BorderInitListener listener = new BorderInitListener(sectorManager, this);
        redisManager.subscribe(listener, channels.toArray(new String[0]));

        GlobalChatPlugin globalChat = new GlobalChatPlugin(this);
        globalChat.register();
    }
    public Map<UUID, String> getPlayerDeathSectors() {
        return playerDeathSectors;
    }

    @Override
    public void onDisable() {
        if (redisManager != null) {
            redisManager.unsubscribe();
            redisManager.closePool();
        }
    }
}