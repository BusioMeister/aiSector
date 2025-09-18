package ai.aisector.commands;

import ai.aisector.SectorPlugin;
import ai.aisector.ranks.Rank;
import ai.aisector.ranks.RankManager;
import ai.aisector.ranks.gui.PermissionGui;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class RankCommand implements CommandExecutor {

    private final SectorPlugin plugin;
    private final RankManager rankManager;

    public RankCommand(SectorPlugin plugin) {
        this.plugin = plugin;
        this.rankManager = plugin.getRankManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aisector.command.rank")) {
            sender.sendMessage("§cNie masz uprawnień do tej komendy.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /rang <gracz> <ranga>  LUB  /rang perms <ranga>");
            return true;
        }
        if (args[0].equalsIgnoreCase("perms")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Zarządzanie permisjami jest dostępne tylko z poziomu gry.");
                return true;
            }
            Player admin = (Player) sender;
            Rank rank = rankManager.getRank(args[1]);
            if (rank == null) {
                admin.sendMessage("§cRanga o nazwie '" + args[1] + "' nie istnieje.");
                return true;
            }
            // Otwórz GUI (stworzymy je w następnym kroku)
            new PermissionGui(plugin, admin, rank, 0).open();
            return true;
        }

        String targetName = args[0];
        String rankName = args[1];
        Rank rank = rankManager.getRank(rankName);
        if (rank == null) {
            sender.sendMessage("§cRanga o nazwie '" + rankName + "' nie istnieje.");
            return true;
        }

        sender.sendMessage("§7Wyszukiwanie gracza " + targetName + "...");

        // Uruchamiamy resztę w osobnym wątku, aby nie lagować serwera
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Player targetPlayer = Bukkit.getPlayer(targetName);
            UUID targetUUID;

            if (targetPlayer != null) {
                // Gracz jest online, mamy jego UUID od razu
                targetUUID = targetPlayer.getUniqueId();
            } else {
                // Gracz jest offline, musimy zapytać serwery Mojang o jego UUID
                targetUUID = getUUIDFromMojang(targetName);
            }

            if (targetUUID == null) {
                sender.sendMessage("§cNie znaleziono gracza o nicku '" + targetName + "'.");
                return;
            }

            // Mamy UUID, możemy ustawić rangę w bazie danych
            rankManager.setPlayerRank(targetUUID, rank, targetName);

            // Wysyłamy sygnał do wszystkich serwerów przez Redis, aby odświeżyły rangę gracza
            // po rankManager.setPlayerRank(targetUUID, rank, targetName);
            try (redis.clients.jedis.Jedis jedis = plugin.getRedisManager().getJedis()) {
                boolean canBypass = plugin.getRankManager()
                        .getEffectivePermissions(rank)
                        .contains("aisector.wyjebane.bypass");
                String key = "bypass_pm:" + targetUUID;
                if (canBypass) jedis.set(key, "1");
                else jedis.del(key);
            }


            sender.sendMessage("§aPomyślnie ustawiono rangę §e" + rank.getName() + " §adla gracza §e" + targetName + "§a.");
        });

        return true;
    }

    // Metoda pomocnicza do pobierania UUID od Mojang
    private UUID getUUIDFromMojang(String playerName) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            if (connection.getResponseCode() == 200) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject();
                String uuidWithDashes = json.get("id").getAsString()
                        .replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                return UUID.fromString(uuidWithDashes);
            }
        } catch (Exception e) {
            // Ignorujemy błędy, np. gdy gracz nie istnieje
        }
        return null;
    }
}