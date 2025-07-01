package ai.aisector;

import ai.aisector.commands.SectorInfoCommand;
import ai.aisector.database.RedisManager;
import ai.aisector.player.GlobalChatPlugin;
import ai.aisector.player.OnlinePlayersPublisherTask;
import ai.aisector.player.PlayerJoinListener;
import ai.aisector.player.PlayerListener;

import ai.aisector.sectors.BorderInitListener;
import ai.aisector.sectors.SectorManager;
import ai.aisector.sectors.WorldBorderManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

public class SectorPlugin extends JavaPlugin {

    private SectorManager sectorManager;
    private RedisManager redisManager;
    private WorldBorderManager worldBorderManager;

    @Override
    public void onEnable() {
        redisManager = new RedisManager("127.0.0.1", 6379);
        sectorManager = new SectorManager(redisManager);
        worldBorderManager = new WorldBorderManager();
        RedisManager redisManager = new RedisManager("localhost", 6379); // Podstaw swoje dane
        PlayerListener playerListener = new PlayerListener(sectorManager, redisManager, worldBorderManager);
        PlayerJoinListener playerJoinListener = new PlayerJoinListener(this, sectorManager, redisManager, worldBorderManager);
        getServer().getPluginManager().registerEvents(playerJoinListener, this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getCommand("sectorinfo").setExecutor(new SectorInfoCommand(sectorManager));
        new OnlinePlayersPublisherTask(redisManager, "Sector1").runTaskTimer(this, 0L, 100L); // 100 ticks = 5 sekund
        new OnlinePlayersPublisherTask(redisManager, "Sector2").runTaskTimer(this, 0L, 100L);

        List<String> channels = sectorManager.getSECTORS().stream()
                .map(sector -> "sector-border-init:" + sector.getName())
                .collect(Collectors.toList());

        BorderInitListener listener = new BorderInitListener(sectorManager, this);
        redisManager.subscribe(listener, channels.toArray(new String[0]));

        GlobalChatPlugin globalChat = new GlobalChatPlugin(this);
        globalChat.register();
    }


    @Override
    public void onDisable() {
        if (redisManager != null) {
            redisManager.unsubscribe();
            redisManager.closePool();
        }
    }
}
