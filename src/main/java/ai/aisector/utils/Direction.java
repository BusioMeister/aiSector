package ai.aisector.utils;

import org.bukkit.Location;

public enum Direction {

    NORTH,
    SOUTH,
    EAST,
    WEST,
    UP_DOWN;

    public Location add(Location base, int toAdd) {
        if (this == NORTH) return base.clone().add(0, 0, -toAdd);
        if (this == SOUTH) return base.clone().add(0, 0, toAdd);
        if (this == WEST) return base.clone().add(-toAdd, 0, 0);
        if (this == EAST) return base.clone().add(toAdd, 0, 0);
        return base;
    }

    public Location add(Location base) {
        return add(base, 1);
    }

    public static Direction fromLocation(Location location) {
        float yaw = location.getYaw();
        yaw = (yaw - 90) % 360;
        if (yaw < 0) yaw += 360.0;

        if (yaw >= 0 && yaw < 45) return SOUTH;
        if (yaw <= 45 && yaw < 135) return WEST;
        if (yaw <= 135 && yaw < 225) return NORTH;
        if (yaw <= 225 && yaw < 315) return EAST;
        if (yaw <= 315 && yaw <= 360) return SOUTH;
        return UP_DOWN;
    }


    public static Direction fromLocations(Location from, Location to) {
        if (to.getBlockZ() < from.getBlockZ()) {
            return Direction.NORTH;
        }
        if (to.getBlockZ() > from.getBlockZ()) {
            return Direction.SOUTH;
        }
        if (to.getBlockX() < from.getBlockX()) {
            return Direction.WEST;
        }
        if (to.getBlockX() > from.getBlockX()) {
            return Direction.EAST;
        }

        return Direction.UP_DOWN;
    }
    public static String cardinal(float yaw) {
        yaw = (yaw % 360 + 360) % 360;

        if (yaw >= 315 || yaw < 45) {
            return "SOUTH";
        } else if (yaw < 135) {
            return "WEST";
        } else if (yaw < 225) {
            return "NORTH";
        } else {
            return "EAST";
        }
    }
}


