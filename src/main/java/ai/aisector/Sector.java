package ai.aisector;

import org.bukkit.World;
import org.bukkit.WorldBorder;

public class Sector {
    private final String name;
    private final int xMin, xMax, zMin, zMax;

    public Sector(String name, int xMin, int xMax, int zMin, int zMax) {
        this.name = name;
        this.xMin = xMin;
        this.xMax = xMax;
        this.zMin = zMin;
        this.zMax = zMax;
    }

    public String getName() {
        return name;
    }

    public boolean isInside(int x, int z) {
        return x >= xMin && x <= xMax && z >= zMin && z <= zMax;
    }

    public int getMinX() {
        return xMin;
    }

    public int getMaxX() {
        return xMax;
    }

    public int getMinZ() {
        return xMin;
    }

    public int getMaxZ() {
        return zMax;
    }

}
