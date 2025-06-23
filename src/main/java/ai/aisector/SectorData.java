package ai.aisector;

public class SectorData {
    private final double centerX;
    private final double centerZ;
    private final double size;

    public SectorData(double centerX, double centerZ, double size) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size = size;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public double getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "SectorData{" +
                "centerX=" + centerX +
                ", centerZ=" + centerZ +
                ", size=" + size +
                '}';
    }
}
