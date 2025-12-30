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

public class SchematicPaster {

    private final File schemFolder;

    public SchematicPaster(File schemFolder) {
        this.schemFolder = schemFolder;
    }

    public void pasteSchem(Location loc, String schemName) throws Exception {
        if (loc == null || loc.getWorld() == null) return;

        File file = new File(schemFolder, schemName.endsWith(".schem") ? schemName : (schemName + ".schem"));
        if (!file.exists()) throw new IllegalStateException("Brak schem: " + file.getAbsolutePath());

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalStateException("Nieznany format schem: " + file.getName());

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        }

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
