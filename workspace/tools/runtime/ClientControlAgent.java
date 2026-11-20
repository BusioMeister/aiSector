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
                } else if (action.equals("boundary-bars") || action.equals("boundary-title")) {
                    var field = net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredField("events");
                    field.setAccessible(true);
                    var events = (java.util.Map<?, ?>) field.get(mc.gui.hud.getBossOverlay());
                    var titles = events.values().stream().map(net.minecraft.world.BossEvent.class::cast)
                            .map(bar -> bar.getName().getString())
                            .filter(title -> title.contains("Granica sektora") || title.contains("Granica mapy")
                                    || title.contains("Zbliżasz się do sektora")).toList();
                    Files.writeString(output, action.equals("boundary-bars") ? Integer.toString(titles.size())
                            : String.join("\n", titles));
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
                } else if (action.equals("trace-command-walk")) {
                    String[] parts = value.split("\\|", 2);
                    long started = System.nanoTime();
                    sampleScreens(mc, output, started, new java.util.ArrayList<>());
                    mc.player.connection.sendCommand(parts[0]);
                    sampleWalk(mc, output.resolveSibling(output.getFileName() + ".walk"), started,
                            Double.parseDouble(parts[1]), new java.util.ArrayList<>());
                    return;
                } else if (action.equals("trace-cooldown-push")) {
                    long started = System.nanoTime();
                    sampleScreens(mc, output, started, new java.util.ArrayList<>());
                    sampleCooldownPush(mc, output.resolveSibling(output.getFileName() + ".walk"), started,
                            Float.parseFloat(value), -1, new java.util.ArrayList<>());
                    return;
                } else if (action.equals("trace-border-walk") || action.equals("trace-edge-walk") || action.equals("trace-boat-walk")) {
                    long started = System.nanoTime();
                    sampleScreens(mc, output, started, new java.util.ArrayList<>());
                    sampleBorderWalk(mc, output.resolveSibling(output.getFileName() + ".walk"), started,
                            Float.parseFloat(value), action.equals("trace-edge-walk"), action.equals("trace-boat-walk"),
                            new java.util.ArrayList<>());
                    return;
                } else if (action.equals("rotate-head")) {
                    rotateHead(mc, System.nanoTime());
                } else if (action.equals("actionbar")) {
                    Files.writeString(output, actionBarText(mc));
                    return;
                } else if (action.equals("client-state")) {
                    Files.writeString(output, mc.player != null && mc.level != null && mc.gui.screen() == null
                            ? "PLAY" : "NOT_PLAY");
                    return;
                } else if (action.equals("flight-state")) {
                    var abilities = mc.player.getAbilities();
                    Files.writeString(output, (abilities.mayfly ? "true" : "false") + " " + abilities.flying);
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

    private static void sampleWalk(Minecraft mc, Path output, long started, double targetX, java.util.List<String> trace) {
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (mc.player != null && (Math.abs(mc.player.getX() - targetX) < .75 || !trace.isEmpty())) {
            mc.player.setYRot(0);
            mc.player.setXRot(0);
            mc.options.keyUp.setDown(true);
            trace.add(elapsed + " " + mc.player.getZ() + " " + mc.player.getAbilities().getWalkingSpeed());
        }
        if (elapsed >= 4500 || mc.player == null) {
            mc.options.keyUp.setDown(false);
            try { Files.writeString(output, String.join("\n", trace)); }
            catch (java.io.IOException error) { throw new IllegalStateException(error); }
        } else {
            java.util.concurrent.CompletableFuture.delayedExecutor(25, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> mc.execute(() -> sampleWalk(mc, output, started, targetX, trace)));
        }
    }

    private static void sampleBorderWalk(Minecraft mc, Path output, long started, float yaw, boolean edge, boolean boat,
                                         java.util.List<String> trace) {
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (mc.player != null && mc.level != null) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(0);
            var border = mc.level.getWorldBorder();
            trace.add(elapsed + " " + mc.player.getX() + " " + mc.player.getY() + " " + mc.player.getZ()
                    + " " + border.getCenterX() + " " + border.getSize() + (edge ? " " + mapEdgeAlert(mc) : ""));
        }
        if (elapsed >= (boat ? 1500 : 4000) || mc.player == null) {
            mc.options.keyUp.setDown(false);
            try { Files.writeString(output, String.join("\n", trace)); }
            catch (java.io.IOException error) { throw new IllegalStateException(error); }
        } else {
            mc.options.keyUp.setDown(!boat || mc.player.isPassenger());
            java.util.concurrent.CompletableFuture.delayedExecutor(25, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> mc.execute(() -> sampleBorderWalk(mc, output, started, yaw, edge, boat, trace)));
        }
    }

    private static boolean mapEdgeAlert(Minecraft mc) {
        return actionBarText(mc).equals("Jesteś na granicy mapy!");
    }

    private static String actionBarText(Minecraft mc) {
        try {
            var field = net.minecraft.client.gui.Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            var message = (net.minecraft.network.chat.Component) field.get(mc.gui.hud);
            return message == null ? "" : message.getString();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void sampleCooldownPush(Minecraft mc, Path output, long started, float yaw, long crossed,
                                           java.util.List<String> trace) {
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (mc.player != null) {
            double side = yaw < 0 ? 1 : -1;
            double x = mc.player.getX() * side;
            if (crossed < 0 && x >= .25) crossed = elapsed;
            boolean reverse = crossed >= 0 && elapsed - crossed >= 450;
            mc.player.setYRot(reverse ? yaw + 180 : yaw);
            mc.player.setXRot(0);
            mc.options.keyUp.setDown(!reverse || x < 4.75);
            trace.add(elapsed + " " + mc.player.getX() + " " + mc.player.getY());
            if (!reverse || x < 4.75) {
                if (elapsed < 3000) {
                    long firstCross = crossed;
                    java.util.concurrent.CompletableFuture.delayedExecutor(25, java.util.concurrent.TimeUnit.MILLISECONDS)
                            .execute(() -> mc.execute(() -> sampleCooldownPush(mc, output, started, yaw, firstCross, trace)));
                    return;
                }
            }
        }
        mc.options.keyUp.setDown(false);
        try { Files.writeString(output, String.join("\n", trace)); }
        catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }

    private static void rotateHead(Minecraft mc, long started) {
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (elapsed > 50_000 || mc.player == null) return;
        mc.player.setYRot((elapsed % 1700) * (360f / 1700));
        mc.player.setXRot((float) (Math.sin(elapsed / 210.0) * 35));
        java.util.concurrent.CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> mc.execute(() -> rotateHead(mc, started)));
    }
}
