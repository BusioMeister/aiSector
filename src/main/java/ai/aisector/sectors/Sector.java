package ai.aisector.sectors;

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
    public double distanceToBorder(int x, int z) {
        int dx = Math.min(Math.abs(x - xMin), Math.abs(x - xMax));
        int dz = Math.min(Math.abs(z - zMin), Math.abs(z - zMax));
        return Math.min(dx, dz);
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
        return zMin; // poprawione
    }

    public int getMaxZ() {
        return zMax;
    }
}
