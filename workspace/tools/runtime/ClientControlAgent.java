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
}
