package ai.aisector.player;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import redis.clients.jedis.JedisPubSub;
import java.util.Set;

public class GlobalPlayerListListener extends JedisPubSub {

    private final Gson gson = new Gson();

    @Override
    public void onMessage(String channel, String message) {
        if (channel.equals("aisector:global_playerlist_update")) {
            Set<String> players = gson.fromJson(message, new TypeToken<Set<String>>(){}.getType());
            GlobalPlayerManager.updatePlayerList(players);
        }
    }
}