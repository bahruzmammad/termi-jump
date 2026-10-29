package com.termijump;

/**
 * An immutable horizontal surface in the game world.
 *
 * @param x left edge of the platform
 * @param y height of its top surface
 * @param width horizontal size of the platform
 */
record Platform(double x, double y, double width) {
    Platform {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                || x < 0 || y < 0 || width <= 0) {
            throw new IllegalArgumentException(
                    "platform coordinates must be finite and non-negative, and width must be positive");
        }
    }

    /**
     * Checks whether a player's horizontal coordinate is within the landing area.
     * A half-unit allowance makes the single-character player easier to land.
     *
     * @param playerX player's horizontal world coordinate
     * @return {@code true} when the player overlaps this platform
     */
    boolean contains(double playerX) {
        return playerX >= x - 0.5 && playerX <= x + width + 0.5;
    }
}
