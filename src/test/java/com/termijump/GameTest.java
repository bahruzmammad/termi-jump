package com.termijump;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Covers game rules and terminal rendering without requiring a live keyboard. */
public final class GameTest {
    /** Verifies initial player values and horizontal movement limits. */
    @Test
    void playerInitializesAndMovesWithinBounds() {
        Player player = new Player(5, 10, false);
        equal(5, player.x(), "Player initial x");
        equal(10, player.y(), "Player initial y");
        equal(0, player.velocityY(), "Stationary initial velocity");

        player.update(0.1, false, true, 12, List.of());
        check(player.x() > 5, "Right input should move the player");
        player.update(1, false, true, 12, List.of());
        check(player.x() <= 11, "Player should remain inside the right edge");
        player.update(1, true, false, 12, List.of());
        check(player.x() >= 0, "Player should remain inside the left edge");
    }

    /** Checks that gravity changes vertical speed and an initial jump moves upward. */
    @Test
    void gravityAndJumpVelocityAreApplied() {
        Player player = new Player(5, 4, true);
        equal(24, player.velocityY(), "Initial jump velocity");
        player.update(0.1, false, false, 20, List.of());
        check(player.velocityY() < 24, "Gravity should reduce upward velocity");
        check(player.y() > 4, "Player should rise while jump velocity remains positive");

        Player falling = new Player(5, 4, false);
        falling.update(0.1, false, false, 20, List.of());
        check(falling.velocityY() < 0, "Gravity should make a stationary player fall");
        check(falling.y() < 4, "Falling player should descend");
    }

    /** Confirms that crossing a platform while falling starts another jump. */
    @Test
    void landingTriggersAnotherJump() {
        Player player = new Player(4, 1, false);
        List<Platform> platforms = List.of(new Platform(0, 0, 8));
        player.update(0.1, false, false, 10, platforms);
        player.update(0.1, false, false, 10, platforms);
        equal(0, player.y(), "Player should land on the platform");
        check(player.velocityY() > 0, "Landing should immediately start the next jump");
    }

    /** Prevents landings through a platform or outside its horizontal edges. */
    @Test
    void playerCannotLandOnPlatformFromBelowOrOutsideItsEdge() {
        Player below = new Player(4, -1, false);
        below.update(0.05, false, false, 10, List.of(new Platform(0, 0, 8)));
        check(below.y() < 0, "Player below a platform must not snap through it");

        Player outside = new Player(9, 1, false);
        outside.update(0.1, false, false, 12, List.of(new Platform(0, 0, 8)));
        outside.update(0.1, false, false, 12, List.of(new Platform(0, 0, 8)));
        check(outside.y() < 0, "Player outside a platform edge should fall past it");
    }

    /** Ensures score follows the run's peak and the best survives a restart. */
    @Test
    void scoreTracksMaximumHeightAndKeepsBestAcrossRuns() {
        ScoreTracker score = new ScoreTracker(1);
        score.update(14.9);
        equal(130, score.score(), "Score at maximum height");
        equal(13, score.height(), "Maximum height");
        score.update(5);
        equal(130, score.score(), "Score must not decrease during a fall");
        score.resetRun();
        equal(0, score.score(), "Current run score after restart");
        equal(130, score.bestScore(), "Best score survives restart");
    }

    /** Checks generated platform bounds, vertical spacing, and jump reachability. */
    @Test
    void platformsGenerateWithinReachableGapsAndWorldBounds() {
        // A fixed seed keeps the generated layout stable so reachability checks are repeatable.
        PlatformGenerator generator = new PlatformGenerator(new Random(5));
        generator.reset(50);
        generator.generateThrough(500, 50);
        List<Platform> platforms = generator.platforms();
        check(platforms.size() > 30, "Generator should create platforms ahead");
        for (int index = 1; index < platforms.size(); index++) {
            Platform previous = platforms.get(index - 1);
            Platform platform = platforms.get(index);
            double gap = platform.y() - previous.y();
            check(gap <= 7.5, "Generated vertical gap should remain jumpable");
            check(gap >= 4.8, "Generated vertical gap should provide progression");
            check(platform.x() >= 0, "Generated platform should not start out of bounds");
            check(platform.x() + platform.width() <= 50.001,
                    "Generated platform should fit the world");
            double horizontalGap = Math.max(0, Math.max(
                    platform.x() - (previous.x() + previous.width()),
                    previous.x() - (platform.x() + platform.width())));
            // Use the jump's descending flight time to bound how far the player can move.
            double landingTime = (24 + Math.sqrt(24 * 24 - 2 * 34 * gap)) / 34;
            check(horizontalGap <= 13 * landingTime + 0.001,
                    "Generated platform should be reachable with horizontal movement");
        }
    }

    /** Verifies that old platforms are removed instead of accumulating forever. */
    @Test
    void discardedPlatformsDoNotAccumulate() {
        PlatformGenerator generator = new PlatformGenerator(new Random(5));
        generator.reset(50);
        generator.generateThrough(500, 50);
        int generatedCount = generator.platforms().size();
        generator.discardBelow(450);
        check(generator.platforms().size() < generatedCount,
                "Platforms below the retained world window should be discarded");
        check(generator.platforms().get(0).y() >= 450,
                "Discarding old platforms should retain current/upcoming platforms");
    }

    /** Confirms callers receive immutable snapshots that do not change later. */
    @Test
    void platformSnapshotsDoNotExposeGeneratorChanges() {
        PlatformGenerator generator = new PlatformGenerator(new Random(5));
        generator.reset(50);
        List<Platform> initialPlatforms = generator.platforms();
        int initialCount = initialPlatforms.size();

        generator.generateThrough(100, 50);
        check(initialPlatforms.size() == initialCount,
                "Existing platform snapshots should not change when the generator advances");
        check(generator.platforms().size() > initialCount,
                "A new snapshot should include generated platforms");

        boolean immutable = false;
        try {
            initialPlatforms.clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "Platform snapshots should not allow callers to mutate generator state");
    }

    /** Rejects invalid coordinate values before they can enter game state. */
    @Test
    void invalidPlatformCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Platform(Double.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Player(0, Double.POSITIVE_INFINITY, false));
    }

    /** Requires initialization before the generator can append new platforms. */
    @Test
    void generatorMustBeResetBeforeGeneratingPlatforms() {
        PlatformGenerator generator = new PlatformGenerator(new Random(5));

        assertThrows(IllegalStateException.class, () -> generator.generateThrough(10, 50));
    }

    /** Simulates several updates and checks that the camera follows the climb. */
    @Test
    void cameraFollowsThePlayerUpward() throws IOException {
        FakeInput input = new FakeInput();
        TerminalRenderer renderer = renderer(40, 10);
        Game game = new Game(input, renderer, new PlatformGenerator(new Random(2)));
        for (int step = 0; step < 20; step++) {
            game.tick(0.05);
        }
        check(game.cameraBottom() > 0, "Camera should scroll as the player climbs");
        renderer.close();
    }

    /** Holds the fake player away from platforms until the game-over state is reached. */
    @Test
    void fallingBelowCameraTriggersGameOver() throws IOException {
        FakeInput input = new FakeInput();
        input.left = true;
        TerminalRenderer renderer = renderer(40, 24);
        Game game = new Game(input, renderer, new PlatformGenerator(new Random(1)));
        for (int step = 0; step < 400 && game.state() == GameState.PLAYING; step++) {
            game.tick(0.05);
        }
        equal(GameState.GAME_OVER, game.state(), "Falling below the camera should end the game");
        renderer.close();
    }

    /** Confirms restart resets the run but preserves its session best score. */
    @Test
    void restartResetsRunWithoutErasingBestScore() throws IOException {
        FakeInput input = new FakeInput();
        input.left = true;
        TerminalRenderer renderer = renderer(40, 24);
        Game game = new Game(input, renderer, new PlatformGenerator(new Random(1)));
        for (int step = 0; step < 400 && game.state() == GameState.PLAYING; step++) {
            game.tick(0.05);
        }
        equal(GameState.GAME_OVER, game.state(), "Game should reach Game Over before restart");
        int bestBeforeRestart = game.bestScore();
        check(bestBeforeRestart > 0, "Climbing should establish a best score");
        input.left = false;
        input.restart = true;
        game.tick(0.05);
        equal(GameState.PLAYING, game.state(), "R should restart the game");
        equal(0, game.score(), "Restart should reset current score");
        equal(0, game.height(), "Restart should reset current height");
        equal(0, game.cameraBottom(), "Restart should reset camera");
        equal(1, game.player().y(), "Restart should return player to the starting platform");
        equal(bestBeforeRestart, game.bestScore(), "Restart should preserve the best score");
        renderer.close();
    }

    /** Checks that a quit request moves the game to its terminal state. */
    @Test
    void quitInputChangesGameState() throws IOException {
        FakeInput input = new FakeInput();
        TerminalRenderer renderer = renderer(40, 16);
        Game game = new Game(input, renderer, new PlatformGenerator(new Random(4)));
        input.quit = true;
        game.tick(0.03);
        equal(GameState.QUIT, game.state(), "Q should quit the game");
        renderer.close();
    }

    /** Captures plain-text output and checks visible platforms and status labels. */
    @Test
    void rendererShowsEveryVisiblePlatformAndHud() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TerminalRenderer renderer = new TerminalRenderer(
                new PrintStream(output, true, StandardCharsets.UTF_8), 40, 16, false);
        List<Platform> platforms = List.of(
                new Platform(3, 0, 5),
                new Platform(20, 2, 6),
                new Platform(10, 7, 4));
        renderer.render(new Player(5, 1, false), platforms, 0, 120, 12, 180,
                GameState.PLAYING);
        String frame = output.toString(StandardCharsets.UTF_8);
        check(count(frame, '=') >= 15, "Every visible platform should be rendered");
        check(frame.contains("Score: 120"), "HUD should include the score");
        check(frame.contains("Height: 12"), "HUD should include the height");
        check(frame.contains("Best: 180"), "HUD should include the best score");
        check(frame.contains("Controls:"), "HUD should include controls");
        renderer.close();
    }

    /** Ensures rendering remains within the minimum supported terminal size. */
    @Test
    void rendererHandlesSmallTerminalDimensions() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TerminalRenderer renderer = new TerminalRenderer(
                new PrintStream(output, true, StandardCharsets.UTF_8), 10, 8, false);
        renderer.render(new Player(0, 0, false),
                List.of(new Platform(0, 0, 3)), 0, 0, 0, 0, GameState.GAME_OVER);
        String[] lines = output.toString(StandardCharsets.UTF_8).split("\\R");
        String frameLine = lines[1].isEmpty() ? lines[2] : lines[1];
        check(frameLine.length() <= 10, "Small terminal render should respect width");
        check(count(output.toString(StandardCharsets.UTF_8), '=') > 0,
                "Small terminal should still render the platform");
        renderer.close();
    }

    /** Checks that world heights are drawn relative to the camera's bottom edge. */
    @Test
    void rendererMapsPlatformsRelativeToCamera() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TerminalRenderer renderer = new TerminalRenderer(
                new PrintStream(output, true, StandardCharsets.UTF_8), 30, 14, false);
        renderer.render(new Player(5, 6, false),
                List.of(new Platform(2, 5, 4), new Platform(14, 8, 5)),
                5, 10, 1, 10, GameState.PLAYING);
        String[] lines = output.toString(StandardCharsets.UTF_8).split("\\R");
        check(lines[1 + 8].charAt(3) == '=', "Platform at camera base should map to viewport bottom");
        check(lines[1 + 5].charAt(15) == '=', "Higher platform should retain its relative screen position");
        renderer.close();
    }

    /** Confirms ANSI output positions the cursor rather than scrolling the screen. */
    @Test
    void ansiRenderingUsesCursorPositioningWithoutScrollingLines() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TerminalRenderer renderer = new TerminalRenderer(
                new PrintStream(output, true, StandardCharsets.UTF_8), 30, 12, true);
        renderer.render(new Player(5, 1, false), List.of(new Platform(2, 0, 5)),
                0, 0, 0, 0, GameState.PLAYING);
        String frame = output.toString(StandardCharsets.UTF_8);
        check(frame.contains("\u001b[1;1H"), "ANSI rendering should position the cursor per row");
        check(!frame.contains("\n"), "ANSI frame should not scroll with newline output");
        renderer.close();
    }

    /** Checks that color sequences and a measured non-zero FPS appear in the frame. */
    @Test
    void rendererShowsColorsAndMeasuredFps() throws InterruptedException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TerminalRenderer renderer = new TerminalRenderer(
                new PrintStream(output, true, StandardCharsets.UTF_8), 50, 16, true);
        Thread.sleep(1_050);
        renderer.render(new Player(5, 1, false), List.of(new Platform(2, 0, 5)),
                0, 10, 1, 10, GameState.PLAYING);
        String frame = output.toString(StandardCharsets.UTF_8);
        check(frame.contains("\u001b[1;96m"), "Title and border should use ANSI color");
        check(frame.contains("\u001b[1;32m"), "Platforms should use a separate color");
        check(frame.matches("(?s).*FPS: [1-9][0-9]*.*"), "HUD should show measured FPS");
        renderer.close();
    }

    /** Creates a renderer backed by memory so game tests do not write to the console. */
    private static TerminalRenderer renderer(int columns, int rows) {
        return new TerminalRenderer(new PrintStream(new ByteArrayOutputStream()), columns, rows, false);
    }

    /** Counts a character in captured terminal output. */
    private static int count(String text, char character) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == character) {
                count++;
            }
        }
        return count;
    }

    /** Gives assertions a short name while keeping a readable failure message. */
    private static void check(boolean condition, String message) {
        assertTrue(condition, message);
    }

    /** Compares floating-point coordinates with a small tolerance for physics calculations. */
    private static void equal(double expected, double actual, String message) {
        assertEquals(expected, actual, 0.0001, message);
    }

    /** Compares enum and other object values using JUnit's standard equality assertion. */
    private static void equal(Object expected, Object actual, String message) {
        assertEquals(expected, actual, message);
    }

    /** In-memory input implementation that lets each test control keys directly. */
    private static final class FakeInput implements InputSource {
        boolean left;
        boolean right;
        boolean quit;
        boolean restart;

        /** Returns the test's current left-key setting. */
        @Override
        public boolean isLeftPressed() {
            return left;
        }

        /** Returns the test's current right-key setting. */
        @Override
        public boolean isRightPressed() {
            return right;
        }

        /** Returns whether the test has requested a quit. */
        @Override
        public boolean isQuitRequested() {
            return quit;
        }

        /** Returns and clears the test's restart request, like the real input handler. */
        @Override
        public boolean consumeRestartRequest() {
            boolean requested = restart;
            restart = false;
            return requested;
        }
    }
}
