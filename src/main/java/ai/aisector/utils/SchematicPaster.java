package ai.aisector.utils;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;

public class SchematicPaster {

    private final File schemFolder;

    public SchematicPaster(File schemFolder) {
        this.schemFolder = schemFolder;
    }

    public void pasteSchem(Location loc, String schemName) throws Exception {
        if (loc == null || loc.getWorld() == null) return;

        pasteClipboard(loc, readSchem(schemName));
    }

    public Clipboard readSchem(String schemName) throws Exception {
        if (!isValidSchematicName(schemName)) {
            throw new IllegalArgumentException("Invalid schematic name");
        }
        Path root = schemFolder.toPath().toRealPath();
        String filename = schemName.endsWith(".schem") ? schemName : schemName + ".schem";
        Path resolved = root.resolve(filename).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Schematic path escapes the configured folder");
        }
        File file = resolved.toFile().getCanonicalFile();
        if (!file.toPath().startsWith(root)) {
            throw new IllegalArgumentException("Schematic symlink escapes the configured folder");
        }
        if (!file.exists()) throw new IllegalStateException("Brak schem: " + file.getAbsolutePath());

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalStateException("Nieznany format schem: " + file.getName());

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            return reader.read();
        }
    }

    static boolean isValidSchematicName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,64}(?:\\.schem)?");
    }

    public void pasteClipboard(Location loc, Clipboard clipboard) throws Exception {
        if (loc == null || loc.getWorld() == null || clipboard == null) return;

        World weWorld = BukkitAdapter.adapt(loc.getWorld());
        BlockVector3 to = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(true)
                    .build();

            Operations.complete(op);
        }
    }
}
