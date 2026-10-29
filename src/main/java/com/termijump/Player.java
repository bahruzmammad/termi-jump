package com.termijump;

import java.util.List;

final class Player {
    private static final double GRAVITY = 34.0;
    private static final double JUMP_SPEED = 24.0;
    private static final double MOVE_SPEED = 13.0;
    private static final double WORLD_EDGE_MARGIN = 1;

    private double x;
    private double y;
    private double velocityY;

    Player(double x, double y, boolean jumpImmediately) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("player coordinates must be finite");
        }
        this.x = x;
        this.y = y;
        velocityY = jumpImmediately ? JUMP_SPEED : 0;
    }

    void update(double deltaSeconds, boolean left, boolean right, double worldWidth,
                List<Platform> platforms) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("delta time must be a non-negative finite value");
        }
        if (!Double.isFinite(worldWidth) || worldWidth <= 0) {
            throw new IllegalArgumentException("world width must be a positive finite value");
        }
        double movementDirection = left == right ? 0 : (left ? -1 : 1);
        x = clamp(x + movementDirection * MOVE_SPEED * deltaSeconds,
                0, Math.max(0, worldWidth - WORLD_EDGE_MARGIN));

        double previousY = y;
        velocityY -= GRAVITY * deltaSeconds;
        y += velocityY * deltaSeconds;

        if (velocityY < 0) {
            for (Platform platform : platforms) {
                // detect crossing the platform surface to avoid missing landings between frames.
                if (previousY >= platform.y() && y <= platform.y() && platform.contains(x)) {
                    y = platform.y();
                    // landing automatically starts the next jump.
                    velocityY = JUMP_SPEED;
                    break;
                }
            }
        }
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double velocityY() {
        return velocityY;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
