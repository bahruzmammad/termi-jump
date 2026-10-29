package com.termijump;

import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

/**
 * Coordinates the game loop and the objects that make up one game run.
 *
 * <p>Input is read through {@link InputSource}, while rendering and platform
 * generation are delegated to their own classes. This keeps frame timing and
 * game-state transitions in one place.</p>
 */
final class Game {
    /** Target interval between game-loop iterations (30 frames per second). */
    private static final double FRAME_SECONDS = 1.0 / 30.0;
    /** Conversion factor used when measuring frame duration with {@link System#nanoTime()}. */
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long FRAME_NANOS = Math.round(FRAME_SECONDS * NANOS_PER_SECOND);
    /** Prevents a long pause from moving the player by an unrealistic amount in one update. */
    private static final double MAX_DELTA_SECONDS = 0.05;
    private static final double START_HEIGHT = 1;
    /** The camera follows the player after it reaches this fraction of the visible height. */
    private static final double CAMERA_PLAYER_HEIGHT_RATIO = 0.65;
    /** Extra platforms below the viewport are retained for collision checks. */
    private static final double PLATFORM_RETENTION_BELOW_CAMERA = 5;
    /** Falling this far below the camera ends the current run. */
    private static final double GAME_OVER_FALL_DISTANCE = 2;

    /** Reads player commands without tying the game loop to a specific input implementation. */
    private final InputSource input;
    /** Draws the current game state and provides the playable terminal dimensions. */
    private final TerminalRenderer renderer;
    /** Creates and stores platforms in the world. */
    private final PlatformGenerator generator;
    /** Keeps current-run and best-score values. */
    private final ScoreTracker scoreTracker = new ScoreTracker(START_HEIGHT);
    private Player player;
    private GameState state = GameState.PLAYING;
    /** World height at the bottom of the visible camera area. */
    private double cameraBottom;

    /** Creates a game with a randomly seeded platform generator. */
    Game(InputSource input, TerminalRenderer renderer) {
        this(input, renderer, new PlatformGenerator());
    }

    /** Creates a game with injectable platform generation for repeatable tests. */
    Game(InputSource input, TerminalRenderer renderer, PlatformGenerator generator) {
        this.input = input;
        this.renderer = renderer;
        this.generator = generator;
        resetGame();
    }

    /** Runs timed frames until input or another event changes the state to {@code QUIT}. */
    void run() throws IOException {
        long previousTime = System.nanoTime();
        long nextFrame = previousTime;
        while (state != GameState.QUIT) {
            // Honor interruption so callers can stop the loop without sending a key.
            if (Thread.interrupted()) {
                state = GameState.QUIT;
                break;
            }
            long now = System.nanoTime();
            // Cap long pauses so returning to the game cannot cause a large physics jump.
            double deltaSeconds = Math.min(MAX_DELTA_SECONDS,
                    (now - previousTime) / (double) NANOS_PER_SECOND);
            previousTime = now;
            renderer.refreshSize();

            // Update and render once, then wait for the next target frame time.
            tick(deltaSeconds);
            if (state != GameState.QUIT) {
                renderer.render(player, generator.platforms(), cameraBottom, scoreTracker.score(),
                        scoreTracker.height(), scoreTracker.bestScore(), state);
            }

            nextFrame += FRAME_NANOS;
            long sleepNanos = nextFrame - System.nanoTime();
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            } else {
                nextFrame = System.nanoTime();
            }
        }
    }

    /** Processes quit/restart input and updates one frame when the game is active. */
    void tick(double deltaSeconds) throws IOException {
        if (state == GameState.QUIT) {
            return;
        }
        if (input.isQuitRequested()) {
            state = GameState.QUIT;
            return;
        }
        if (state == GameState.GAME_OVER) {
            if (input.consumeRestartRequest()) {
                resetGame();
            }
            return;
        }
        // Restart is only meaningful after game over; discard it during normal play.
        input.consumeRestartRequest();

        update(deltaSeconds);
    }

    /** Advances physics, scoring, camera tracking, and the platform window by one frame. */
    private void update(double deltaSeconds) {
        double worldWidth = renderer.playWidth();
        player.update(deltaSeconds, input.isLeftPressed(), input.isRightPressed(),
                worldWidth, generator.platforms());
        scoreTracker.update(player.y());

        double visibleHeight = renderer.playHeight();
        // Keep the player near the upper part of the viewport while climbing.
        if (player.y() > cameraBottom + visibleHeight * CAMERA_PLAYER_HEIGHT_RATIO) {
            cameraBottom = player.y() - visibleHeight * CAMERA_PLAYER_HEIGHT_RATIO;
        }

        // Generate ahead of the player so the next platform exists before it is visible.
        generator.generateThrough(player.y() + visibleHeight, worldWidth);
        // Retain a small buffer below the viewport for reliable landing checks.
        generator.discardBelow(cameraBottom - PLATFORM_RETENTION_BELOW_CAMERA);

        if (player.y() < cameraBottom - GAME_OVER_FALL_DISTANCE) {
            state = GameState.GAME_OVER;
        }
    }

    /** Returns the current lifecycle state; package access is used by tests. */
    GameState state() {
        return state;
    }

    /** Returns the current player for rendering and package-level tests. */
    Player player() {
        return player;
    }

    /** Returns the world height currently aligned with the viewport bottom. */
    double cameraBottom() {
        return cameraBottom;
    }

    /** Returns the score for this run. */
    int score() {
        return scoreTracker.score();
    }

    /** Returns the maximum whole-unit height reached in this run. */
    int height() {
        return scoreTracker.height();
    }

    /** Returns the best score since this game instance was created. */
    int bestScore() {
        return scoreTracker.bestScore();
    }

    /** Recreates the starting world and player while preserving the best score. */
    private void resetGame() {
        generator.reset(renderer.playWidth());
        Platform startingPlatform = generator.platforms().get(0);
        player = new Player(startingPlatform.x() + startingPlatform.width() / 2,
                START_HEIGHT, true);
        cameraBottom = 0;
        scoreTracker.resetRun();
        state = GameState.PLAYING;
    }
}
