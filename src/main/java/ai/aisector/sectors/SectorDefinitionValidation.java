package ai.aisector.sectors;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class SectorDefinitionValidation {
    private SectorDefinitionValidation() { }

    static String validate(Collection<Sector> sectors) {
        Set<String> names = new HashSet<>();
        for (Sector sector : sectors) {
            if (sector == null || sector.getName() == null
                    || !sector.getName().matches("[A-Za-z0-9_-]{1,64}")) return "invalid sector name";
            if (!names.add(sector.getName().toLowerCase(Locale.ROOT))) return "duplicate sector name";
            if (sector.getMinX() > sector.getMaxX() || sector.getMinZ() > sector.getMaxZ())
                return "inverted sector bounds: " + sector.getName();
            if (Math.abs((long) sector.getMinX()) > 30_000_000L
                    || Math.abs((long) sector.getMaxX()) > 30_000_000L
                    || Math.abs((long) sector.getMinZ()) > 30_000_000L
                    || Math.abs((long) sector.getMaxZ()) > 30_000_000L)
                return "sector bounds outside world limits: " + sector.getName();
        }
        Sector[] definitions = sectors.toArray(Sector[]::new);
        for (int i = 0; i < definitions.length; i++) {
            for (int j = i + 1; j < definitions.length; j++) {
                Sector first = definitions[i];
                Sector second = definitions[j];
                boolean intersects = first.getMinX() <= second.getMaxX()
                        && first.getMaxX() >= second.getMinX()
                        && first.getMinZ() <= second.getMaxZ()
                        && first.getMaxZ() >= second.getMinZ();
                if (intersects) return "overlapping sectors: " + first.getName() + " and " + second.getName();
            }
        }
        return null;
    }
}
