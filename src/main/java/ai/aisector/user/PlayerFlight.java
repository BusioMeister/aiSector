package ai.aisector.user;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Profile /fly grants permission in addition to vanilla game-mode abilities. */
public record PlayerFlight(boolean allowed, boolean flying) {
    public static PlayerFlight resolve(GameMode mode, boolean profileFly, Boolean activeFlight) {
        boolean allowed = profileFly || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
        boolean flying = mode == GameMode.SPECTATOR || (activeFlight == null ? profileFly : activeFlight);
        return new PlayerFlight(allowed, allowed && flying);
    }

    public void apply(Player player) {
        if (!flying) player.setFlying(false);
        player.setAllowFlight(allowed);
        if (flying) player.setFlying(true);
    }
}
