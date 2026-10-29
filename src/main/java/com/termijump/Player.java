package com.termijump;

import java.util.List;

/**
 * Stores the player's world position and updates its movement and vertical physics.
 *
 * <p>The positive vertical direction points upward. Gravity reduces vertical
 * velocity each update, and a landing resets it to the upward jump speed.</p>
 */
final class Player {
    /** Downward acceleration applied each second. */
    private static final double GRAVITY = 34.0;
    /** Upward speed assigned at the start of every jump. */
    private static final double JUMP_SPEED = 24.0;
    /** Horizontal speed while exactly one direction is active. */
    private static final double MOVE_SPEED = 13.0;
    /** Space kept between the player's coordinate and the right world edge. */
    private static final double WORLD_EDGE_MARGIN = 1;

    private double x;
    private double y;
    private double velocityY;

    /** Creates a player at a finite position, optionally starting in a jump. */
    Player(double x, double y, boolean jumpImmediately) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("player coordinates must be finite");
        }
        this.x = x;
        this.y = y;
        velocityY = jumpImmediately ? JUMP_SPEED : 0;
    }

    /**
     * Applies one time step of movement and physics.
     *
     * @param deltaSeconds elapsed time since the previous update
     * @param left whether left movement is active
     * @param right whether right movement is active
     * @param worldWidth horizontal size of the playable world
     * @param platforms platforms that may be landed on during this update
     */
    void update(double deltaSeconds, boolean left, boolean right, double worldWidth,
                List<Platform> platforms) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("delta time must be a non-negative finite value");
        }
        if (!Double.isFinite(worldWidth) || worldWidth <= 0) {
            throw new IllegalArgumentException("world width must be a positive finite value");
        }
        // Opposing inputs cancel; equal input values therefore mean no horizontal movement.
        double movementDirection = left == right ? 0 : (left ? -1 : 1);
        x = clamp(x + movementDirection * MOVE_SPEED * deltaSeconds,
                0, Math.max(0, worldWidth - WORLD_EDGE_MARGIN));

        // Keep the previous position to detect a platform crossed during this update.
        double previousY = y;
        // Apply acceleration before position so each update uses the new vertical speed.
        velocityY -= GRAVITY * deltaSeconds;
        y += velocityY * deltaSeconds;

        if (velocityY < 0) {
            for (Platform platform : platforms) {
                // Detect surface crossings so landings are not missed between frames.
                if (previousY >= platform.y() && y <= platform.y() && platform.contains(x)) {
                    y = platform.y();
                    // Landing automatically starts the next jump.
                    velocityY = JUMP_SPEED;
                    break;
                }
            }
        }
    }

    /** Returns the player's horizontal world coordinate. */
    double x() {
        return x;
    }

    /** Returns the player's vertical world coordinate. */
    double y() {
        return y;
    }

    /** Returns vertical speed; positive is upward and negative is downward. */
    double velocityY() {
        return velocityY;
    }

    /** Keeps a number within the supplied minimum and maximum bounds. */
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
