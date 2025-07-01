package ai.aisector.Tab;

import ai.aisector.database.RedisManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import redis.clients.jedis.JedisPubSub;

import java.util.HashMap;
import java.util.Map;

public class TablistManager {

    private final RedisManager redisManager;
    private final Map<Player, Scoreboard> playerScoreboards = new HashMap<>();
    private final String HEADER = ChatColor.RED + "" + ChatColor.BOLD + "Lastplay I";
    private final String FOOTER = ChatColor.YELLOW + "Czas: ";

    public TablistManager(RedisManager redisManager) {
        this.redisManager = redisManager;
        startUpdating();
        listenForRedisUpdates();
    }

    // Tworzenie tablisty dla gracza
    public void createTablist(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = scoreboard.registerNewObjective("tab", "dummy", Component.text(" "));
        obj.setDisplaySlot(DisplaySlot.PLAYER_LIST);

        player.setScoreboard(scoreboard);
        playerScoreboards.put(player, scoreboard);
        updateTab(player);
    }

    // Przykładowe dane - można zastąpić z Redis lub Mongo
    private void updateTab(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) return;

        Objective obj = scoreboard.getObjective(DisplaySlot.PLAYER_LIST);
        if (obj == null) return;

        String[] entries = {
                ChatColor.RED + "Ranking: " + ChatColor.GOLD + "1",
                ChatColor.RED + "Punkty: " + ChatColor.GOLD + "500",
                ChatColor.RED + "Poziom: " + ChatColor.GOLD + "5",
                ChatColor.RED + "Sektor: " + ChatColor.GOLD + "1",
                ChatColor.RED + "Staty K/D: " + ChatColor.GREEN + "10/2",
                ChatColor.RED + "Gildia: " + ChatColor.YELLOW + "derp",
                ChatColor.RED + "DC: " + ChatColor.GRAY + "discord.gg/Lastplay",
                ChatColor.RED + "Strona: " + ChatColor.GRAY + "Lastplay.pl",
                ChatColor.RED + "Ping: " + ChatColor.GREEN + player.getPing(),
                ChatColor.RED + "Czas: " + ChatColor.YELLOW + java.time.LocalTime.now().withNano(0)
        };

        int score = entries.length;
        for (String entry : entries) {
            if (scoreboard.getEntries().contains(entry)) continue;

            Score scoreLine = obj.getScore(entry);
            scoreLine.setScore(score--);
        }
    }

    // Odświeżanie tablisty co 2 sekundy
    private void startUpdating() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateTab(player);
                }
            }
        }.runTaskTimerAsynchronously(Bukkit.getPluginManager().getPlugin("TwojPlugin"), 40L, 40L);
    }

    // Nasłuchiwanie danych z Redisa
    private void listenForRedisUpdates() {
        redisManager.subscribe(new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if (channel.equals("tablist-update")) {
                    Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("TwojPlugin"), () -> {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            updateTab(player);
                        }
                    });
                }
            }
        }, "tablist-update");
    }
}
