package com.termijump;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

final class PlatformGenerator {
    private static final double START_PLATFORM_WIDTH = 10;
    private static final double DIFFICULTY_RAMP_HEIGHT = 300;
    private static final double BASE_PLATFORM_WIDTH = 8.5;
    private static final double MAX_WIDTH_REDUCTION = 3;
    private static final double PLATFORM_WIDTH_VARIATION = 2.5;
    private static final double MIN_PLATFORM_GAP = 4.8;
    private static final double PLATFORM_GAP_VARIATION = 1.3;
    private static final double MAX_DIFFICULTY_GAP = 1.4;
    private static final double MAX_CENTER_SHIFT = 9;
    private static final double RANDOM_RANGE_CENTERED = 2;

    private final Random random;
    private final List<Platform> platforms = new ArrayList<>();
    private List<Platform> platformSnapshot;

    PlatformGenerator() {
        this(new Random());
    }

    PlatformGenerator(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    void reset(double worldWidth) {
        validateWorldWidth(worldWidth);
        platforms.clear();
        double width = Math.min(START_PLATFORM_WIDTH, worldWidth);
        platforms.add(new Platform((worldWidth - width) / 2, 0, width));
        platformSnapshot = null;
    }

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
            // narrower platforms and larger gaps gradually increase the climb difficulty.
            double difficulty = Math.min(1, previous.y() / DIFFICULTY_RAMP_HEIGHT);
            double width = Math.min(worldWidth, BASE_PLATFORM_WIDTH
                    - difficulty * MAX_WIDTH_REDUCTION
                    + random.nextDouble() * PLATFORM_WIDTH_VARIATION);
            double gap = MIN_PLATFORM_GAP + random.nextDouble() * PLATFORM_GAP_VARIATION
                    + difficulty * MAX_DIFFICULTY_GAP;
            // choose a nearby center, then clamp the platform so it stays inside the world.
            double centerShift = (random.nextDouble() * RANDOM_RANGE_CENTERED - 1) * MAX_CENTER_SHIFT;
            double center = previous.x() + previous.width() / 2 + centerShift;
            double x = clamp(center - width / 2, 0, Math.max(0, worldWidth - width));
            platforms.add(new Platform(x, previous.y() + gap, width));
            generated = true;
        }
        if (generated) {
            platformSnapshot = null;
        }
    }

    void discardBelow(double lowestY) {
        if (platforms.removeIf(platform -> platform.y() < lowestY)) {
            platformSnapshot = null;
        }
    }

    List<Platform> platforms() {
        if (platformSnapshot == null) {
            platformSnapshot = List.copyOf(platforms);
        }
        return platformSnapshot;
    }

    private static void validateWorldWidth(double worldWidth) {
        if (!Double.isFinite(worldWidth) || worldWidth <= 0) {
            throw new IllegalArgumentException("world width must be a positive finite value");
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
