package com.termijump;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Generates the starting platform and the platforms above it.
 *
 * <p>Platform positions include random variation, but their vertical gaps and
 * center shifts are limited so they remain reachable by the player. A cached,
 * immutable snapshot lets game updates read platforms safely without exposing
 * the generator's mutable list.</p>
 */
final class PlatformGenerator {
    /** Width of the first platform before limiting it to the world width. */
    private static final double START_PLATFORM_WIDTH = 10;
    /** Height over which the difficulty rises from its minimum to its maximum. */
    private static final double DIFFICULTY_RAMP_HEIGHT = 300;
    private static final double BASE_PLATFORM_WIDTH = 8.5;
    private static final double MAX_WIDTH_REDUCTION = 3;
    private static final double PLATFORM_WIDTH_VARIATION = 2.5;
    private static final double MIN_PLATFORM_GAP = 4.8;
    private static final double PLATFORM_GAP_VARIATION = 1.3;
    private static final double MAX_DIFFICULTY_GAP = 1.4;
    private static final double MAX_CENTER_SHIFT = 9;
    private static final double RANDOM_RANGE_CENTERED = 2;

    /** Random source; tests can inject a fixed seed for repeatable layouts. */
    private final Random random;
    /** Mutable internal list, kept in ascending height order. */
    private final List<Platform> platforms = new ArrayList<>();
    /** Read-only cached view rebuilt only after the internal list changes. */
    private List<Platform> platformSnapshot;

    /** Creates a generator with a non-deterministic random source. */
    PlatformGenerator() {
        this(new Random());
    }

    /** Creates a generator using the supplied random source. */
    PlatformGenerator(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Clears old platforms and centers a new starting platform at height zero. */
    void reset(double worldWidth) {
        validateWorldWidth(worldWidth);
        platforms.clear();
        double width = Math.min(START_PLATFORM_WIDTH, worldWidth);
        platforms.add(new Platform((worldWidth - width) / 2, 0, width));
        platformSnapshot = null;
    }

    /**
     * Adds platforms until the last one reaches or passes the requested height.
     *
     * <p>Difficulty is calculated from the previous platform's height. Width,
     * gap, and horizontal position are then varied within bounded ranges.</p>
     */
    void generateThrough(double worldY, double worldWidth) {
        validateWorldWidth(worldWidth);
        if (!Double.isFinite(worldY)) {
            throw new IllegalArgumentException("world height must be finite");
        }
        if (platforms.isEmpty()) {
            throw new IllegalStateException("reset the platform generator before generating platforms");
        }
        boolean generated = false;
        while (platforms.get(platforms.size() - 1).y() < worldY) {
            Platform previous = platforms.get(platforms.size() - 1);
            // Narrower platforms and larger gaps gradually increase the climbing difficulty.
            // Normalize difficulty to 0..1 so it stops increasing after the ramp.
            double difficulty = Math.min(1, previous.y() / DIFFICULTY_RAMP_HEIGHT);
            double width = Math.min(worldWidth, BASE_PLATFORM_WIDTH
                    - difficulty * MAX_WIDTH_REDUCTION
                    + random.nextDouble() * PLATFORM_WIDTH_VARIATION);
            double gap = MIN_PLATFORM_GAP + random.nextDouble() * PLATFORM_GAP_VARIATION
                    + difficulty * MAX_DIFFICULTY_GAP;
            // Choose a nearby center, then clamp the platform to the world boundaries.
            // Keep the new platform near the previous one, then clamp it to the world.
            double centerShift = (random.nextDouble() * RANDOM_RANGE_CENTERED - 1) * MAX_CENTER_SHIFT;
            double center = previous.x() + previous.width() / 2 + centerShift;
            double x = clamp(center - width / 2, 0, Math.max(0, worldWidth - width));
            platforms.add(new Platform(x, previous.y() + gap, width));
            generated = true;
        }
        // A changed list requires a new snapshot the next time it is requested.
        if (generated) {
            platformSnapshot = null;
        }
    }

    /** Removes platforms below a safe cutoff to keep the active world small. */
    void discardBelow(double lowestY) {
        if (platforms.removeIf(platform -> platform.y() < lowestY)) {
            platformSnapshot = null;
        }
    }

    /** Returns an immutable snapshot so callers cannot mutate generator state. */
    List<Platform> platforms() {
        // Reuse an immutable snapshot until the platform list changes.
        if (platformSnapshot == null) {
            platformSnapshot = List.copyOf(platforms);
        }
        return platformSnapshot;
    }

    /** Rejects widths that cannot define a usable horizontal world. */
    private static void validateWorldWidth(double worldWidth) {
        if (!Double.isFinite(worldWidth) || worldWidth <= 0) {
            throw new IllegalArgumentException("world width must be a positive finite value");
        }
    }

    /** Restricts a value to the inclusive interval between the two bounds. */
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
