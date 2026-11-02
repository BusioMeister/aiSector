package ai.aisector.listeners;

import ai.aisector.shared.RedisChannels;
import ai.aisector.sectors.player.GlobalPlayerManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redis.clients.jedis.JedisPubSub;
import java.util.Set;

public class GlobalPlayerListListener extends JedisPubSub {
    private static final int MAX_MESSAGE_CHARS = 1_048_576;
    private static final int MAX_PLAYERS = 65_536;
    private static final java.util.regex.Pattern PLAYER_NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final Gson gson = new Gson();

    @Override
    public void onMessage(String channel, String message) {
        if (channel.equals(RedisChannels.GLOBAL_PLAYER_LIST)) {
            if (message == null || message.length() > MAX_MESSAGE_CHARS) return;
            try {
                Set<String> players = gson.fromJson(message, new TypeToken<Set<String>>(){}.getType());
                if (players == null || players.size() > MAX_PLAYERS
                        || players.stream().anyMatch(player -> player == null
                        || !PLAYER_NAME.matcher(player).matches())) return;
                GlobalPlayerManager.updatePlayerList(players);
            } catch (RuntimeException exception) {
                // Ignore malformed presence messages without dropping this Pub/Sub subscription.
            }
        }
    }
}
