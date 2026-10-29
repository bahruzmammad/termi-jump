package com.termijump;

import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

final class Game {
    private static final double FRAME_SECONDS = 1.0 / 30.0;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long FRAME_NANOS = Math.round(FRAME_SECONDS * NANOS_PER_SECOND);
    private static final double MAX_DELTA_SECONDS = 0.05;
    private static final double START_HEIGHT = 1;
    private static final double CAMERA_PLAYER_HEIGHT_RATIO = 0.65;
    private static final double PLATFORM_RETENTION_BELOW_CAMERA = 5;
    private static final double GAME_OVER_FALL_DISTANCE = 2;

    private final InputSource input;
    private final TerminalRenderer renderer;
    private final PlatformGenerator generator;
    private final ScoreTracker scoreTracker = new ScoreTracker(START_HEIGHT);
    private Player player;
    private GameState state = GameState.PLAYING;
    private double cameraBottom;

    Game(InputSource input, TerminalRenderer renderer) {
        this(input, renderer, new PlatformGenerator());
    }

    Game(InputSource input, TerminalRenderer renderer, PlatformGenerator generator) {
        this.input = input;
        this.renderer = renderer;
        this.generator = generator;
        resetGame();
    }

    void run() throws IOException {
        long previousTime = System.nanoTime();
        long nextFrame = previousTime;
        while (state != GameState.QUIT) {
            if (Thread.interrupted()) {
                state = GameState.QUIT;
                break;
            }
            long now = System.nanoTime();
            // cap long pauses so returning to the game cannot cause a large physics jump.
            double deltaSeconds = Math.min(MAX_DELTA_SECONDS,
                    (now - previousTime) / (double) NANOS_PER_SECOND);
            previousTime = now;
            renderer.refreshSize();

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
        input.consumeRestartRequest();

        update(deltaSeconds);
    }

    private void update(double deltaSeconds) {
        double worldWidth = renderer.playWidth();
        player.update(deltaSeconds, input.isLeftPressed(), input.isRightPressed(),
                worldWidth, generator.platforms());
        scoreTracker.update(player.y());

        double visibleHeight = renderer.playHeight();
        // keep the player above the lower part of the screen while climbing.
        if (player.y() > cameraBottom + visibleHeight * CAMERA_PLAYER_HEIGHT_RATIO) {
            cameraBottom = player.y() - visibleHeight * CAMERA_PLAYER_HEIGHT_RATIO;
        }

        generator.generateThrough(player.y() + visibleHeight, worldWidth);
        // retain a small buffer below the viewport for reliable landing checks.
        generator.discardBelow(cameraBottom - PLATFORM_RETENTION_BELOW_CAMERA);

        if (player.y() < cameraBottom - GAME_OVER_FALL_DISTANCE) {
            state = GameState.GAME_OVER;
        }
    }

    GameState state() {
        return state;
    }

    Player player() {
        return player;
    }

    double cameraBottom() {
        return cameraBottom;
    }

    int score() {
        return scoreTracker.score();
    }

    int height() {
        return scoreTracker.height();
    }

    int bestScore() {
        return scoreTracker.bestScore();
    }

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
