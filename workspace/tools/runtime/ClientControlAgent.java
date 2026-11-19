import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.ContainerInput;

/** Drives an official test client on its own game thread; never handles credentials. */
public class ClientControlAgent {
    public static void agentmain(String args, Instrumentation instrumentation) {
        String[] fields = args.split("\\|", 3);
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getUser().getName().matches("RwS[A-Za-z0-9]{4,13}"))
            throw new IllegalStateException("Not a test client");
        String action = fields[0];
        String value = new String(Base64.getDecoder().decode(fields[1]), StandardCharsets.UTF_8);
        Path output = Path.of(fields[2]);
        mc.execute(() -> {
            try {
                if (action.equals("command")) {
                    if (mc.player == null) throw new IllegalStateException("Not connected");
                    mc.player.connection.sendCommand(value);
                } else if (action.equals("chat")) {
                    mc.player.connection.sendChat(value);
                } else if (action.equals("screenshot")) {
                    net.minecraft.client.Screenshot.grab(mc, false);
                } else if (action.equals("block") || action.equals("trace-block")) {
                    String[] xyz = value.split(",");
                    var pos = new net.minecraft.core.BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                    var hit = new net.minecraft.world.phys.BlockHitResult(
                            new net.minecraft.world.phys.Vec3(pos.getX() + .5, pos.getY() + .4, pos.getZ() + .5),
                            net.minecraft.core.Direction.UP, pos, false);
                    if (action.equals("trace-block")) {
                        sampleScreens(mc, output, System.nanoTime(), new java.util.ArrayList<>());
                    }
                    mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    if (action.equals("trace-block")) return;
                } else if (action.equals("move-to")) {
                    double x = Double.parseDouble(value);
                    mc.player.setPos(x, mc.player.getY(), mc.player.getZ());
                } else if (action.equals("border-state")) {
                    var border = mc.level.getWorldBorder();
                    Files.writeString(output, border.getCenterX() + " " + border.getCenterZ() + " " + border.getSize());
                    return;
                } else if (action.equals("boundary-bars")) {
                    var field = net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredField("events");
                    field.setAccessible(true);
                    var events = (java.util.Map<?, ?>) field.get(mc.gui.hud.getBossOverlay());
                    long count = events.values().stream().map(net.minecraft.world.BossEvent.class::cast)
                            .filter(bar -> bar.getName().getString().contains("Granica sektora")).count();
                    Files.writeString(output, Long.toString(count));
                    return;
                } else if (action.equals("trace-seam")) {
                    double x = Double.parseDouble(value);
                    long started = System.nanoTime();
                    var trace = new java.util.ArrayList<String>();
                    sampleScreens(mc, output, started, trace);
                    mc.player.setPos(x, mc.player.getY(), mc.player.getZ());
                    sampleSeamPosition(mc, output.resolveSibling(output.getFileName() + ".x"), started,
                            new java.util.ArrayList<>());
                    return;
                } else if (action.equals("trace-command")) {
                    long started = System.nanoTime();
                    var trace = new java.util.ArrayList<String>();
                    sampleScreens(mc, output, started, trace);
                    mc.player.connection.sendCommand(value);
                    return;
                } else if (action.equals("trace-flight")) {
                    mc.player.setYRot(0);
                    mc.player.setXRot(0);
                    sampleFlight(mc, output, System.nanoTime(), new java.util.ArrayList<>());
                    return;
                } else if (action.equals("click")) {
                    String[] click = value.split(":");
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, Integer.parseInt(click[0]),
                            Integer.parseInt(click[1]), ContainerInput.PICKUP, mc.player);
                } else if (action.equals("close")) {
                    mc.player.closeContainer();
                } else if (action.equals("respawn")) {
                    mc.player.respawn();
                } else if (action.equals("disconnect") || action.equals("reconnect")) {
                    if (mc.player != null) mc.player.connection.getConnection()
                            .disconnect(net.minecraft.network.chat.Component.literal("Local reconnect smoke"));
                    mc.disconnect(new TitleScreen(), false);
                    if (action.equals("reconnect")) java.util.concurrent.CompletableFuture.delayedExecutor(750, java.util.concurrent.TimeUnit.MILLISECONDS)
                            .execute(() -> mc.execute(() -> ConnectScreen.startConnecting(new TitleScreen(), mc,
                                    ServerAddress.parseString("127.0.0.1:25565"),
                                    new ServerData("Local rewrite smoke", "127.0.0.1:25565", ServerData.Type.OTHER), false, null)));
                } else if (action.equals("exit")) {
                    mc.stop();
                } else throw new IllegalArgumentException(action);
                Files.writeString(output, "OK");
            } catch (Throwable error) {
                try { Files.writeString(output, "ERROR:" + error); }
                catch (Exception writeError) { throw new RuntimeException(writeError); }
            }
        });
    }

    private static void sampleScreens(Minecraft mc, Path output, long started, java.util.List<String> trace) {
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        var visible = mc.gui.screen();
        String screen = visible == null ? "PLAY" : visible.getClass().getSimpleName();
        if (trace.isEmpty() || !trace.getLast().endsWith(" " + screen)) trace.add(elapsed + " " + screen);
        if (elapsed >= 6000) {
            try { Files.writeString(output, String.join("\n", trace)); }
            catch (java.io.IOException error) { throw new IllegalStateException(error); }
        } else java.util.concurrent.CompletableFuture.delayedExecutor(25, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> mc.execute(() -> sampleScreens(mc, output, started, trace)));
    }

    private static void sampleSeamPosition(Minecraft mc, Path output, long started, java.util.List<String> trace) {
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (mc.player != null) trace.add(elapsed + " " + mc.player.getX());
        if (elapsed >= 4000) {
            try { Files.writeString(output, String.join("\n", trace)); }
            catch (java.io.IOException error) { throw new IllegalStateException(error); }
        } else java.util.concurrent.CompletableFuture.delayedExecutor(25, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> mc.execute(() -> sampleSeamPosition(mc, output, started, trace)));
    }

    private static void sampleFlight(Minecraft mc, Path output, long started, java.util.List<String> trace) {
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (mc.player != null) trace.add(elapsed + " " + mc.player.getZ() + " " + mc.player.getAbilities().flying);
        if (elapsed >= 6500 || mc.player == null) {
            mc.options.keyUp.setDown(false);
            try { Files.writeString(output, String.join("\n", trace)); }
            catch (java.io.IOException error) { throw new IllegalStateException(error); }
        } else {
            mc.options.keyUp.setDown(true);
            java.util.concurrent.CompletableFuture.delayedExecutor(25, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> mc.execute(() -> sampleFlight(mc, output, started, trace)));
        }
    }
}
