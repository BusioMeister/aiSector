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

    public double distanceToEdge(double x, double z) {
        return Math.min(Math.min(x - xMin, (double) xMax + 1 - x),
                Math.min(z - zMin, (double) zMax + 1 - z));
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
    // 🔥 DODANE METODY
    /**
     * Oblicza szerokość sektora (wzdłuż osi X).
     * @return Szerokość w blokach.
     */
    public int getWidth() {
        return this.xMax - this.xMin + 1;
    }

    /**
     * Oblicza głębokość/długość sektora (wzdłuż osi Z).
     * @return Głębokość w blokach.
     */
    public int getDepth() {
        return this.zMax - this.zMin + 1;
    }
}

