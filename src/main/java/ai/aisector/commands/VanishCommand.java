package ai.aisector.commands;

import ai.aisector.utils.Messages;

import ai.aisector.SectorPlugin;
import ai.aisector.sectors.player.VanishManager;
import ai.aisector.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;

public class VanishCommand implements CommandExecutor {
    private final SectorPlugin plugin;
    private final VanishManager vanishManager;

    public VanishCommand(SectorPlugin plugin, VanishManager vanishManager) {
        this.plugin = plugin;
        this.vanishManager = vanishManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        if (!player.hasPermission("aisector.command.vanish")) {
            player.sendMessage(Messages.text("vanish-command.nie-masz-uprawnien"));
            return true;
        }
        User user = plugin.getUserManager().getUser(player);
        if (user == null) {
            player.sendMessage(Messages.text("vanish-command.wystapil-blad-wczytywania-twoich-danych"));
            return true;
        }

        // Zmień stan w obiekcie User (aby zapisał się w bazie)
        user.setVanished(!user.isVanished());

        // Poproś VanishManager o wykonanie akcji
        if (user.isVanished()) {
            vanishManager.vanish(player);
            // po vanish/unvanish:
            plugin.getGuildTagManager().updateTagsFor(player);

            player.sendMessage(Messages.text("vanish-command.vanish-zostal-wlaczony"));
        } else {
            vanishManager.unvanish(player);
            // po vanish/unvanish:
            plugin.getGuildTagManager().updateTagsFor(player);
            playRevealEffect(player);

            player.sendMessage(Messages.text("vanish-command.vanish-zostal-wylaczony"));
        }
        return true;
    }

    private void playRevealEffect(Player player) {
        Location origin = player.getLocation();
        var world = origin.getWorld();
        world.strikeLightningEffect(origin.clone().add(.8, 0, 0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getWorld() == world && !VanishManager.isVanished(player)) {
                world.strikeLightningEffect(origin.clone().add(-.8, 0, 0));
            }
        }, 5L);

        Location center = origin.clone().add(0, .9, 0);
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            world.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(Math.cos(angle) * 1.15, 0, Math.sin(angle) * 1.15),
                    3, .14, .32, .14, .025);
        }
        world.spawnParticle(Particle.SMOKE, center, 28, .55, .4, .55, .035);

        var bats = new ArrayList<Bat>(5);
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2 / 5;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            Bat bat = world.spawn(center.clone().add(dx * .55, .4, dz * .55), Bat.class, spawned -> {
                spawned.setAwake(true);
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setCollidable(false);
            });
            bat.setVelocity(new Vector(dx * .38, .29, dz * .38));
            bats.add(bat);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> bats.forEach(Bat::remove), 45L);
    }
}
