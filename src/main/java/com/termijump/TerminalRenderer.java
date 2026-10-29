package com.termijump;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;

/**
 * Converts game-world objects into characters and styles displayed by a terminal.
 *
 * <p>Each frame is first assembled in reusable arrays. ANSI-capable terminals
 * are updated in place; plain terminals receive separate text frames.</p>
 */
final class TerminalRenderer implements AutoCloseable {
    /** Restrict buffers so unusually small or large terminal reports stay manageable. */
    private static final int MIN_COLUMNS = 10;
    private static final int MAX_COLUMNS = 160;
    private static final int MIN_ROWS = 8;
    private static final int MAX_ROWS = 60;
    /** Row and offset positions reserved for the title, play area, and status text. */
    private static final int TITLE_ROW = 1;
    private static final int PLAY_TOP_ROW = 2;
    private static final int PLAY_BOTTOM_ROW_OFFSET = 6;
    private static final int SCORE_ROW_OFFSET = 4;
    private static final int CONTROLS_ROW_OFFSET = 3;
    private static final int MESSAGE_ROW_OFFSET = 2;
    /** Plain output is throttled because it cannot erase and redraw the same frame. */
    private static final long PLAIN_RENDER_INTERVAL_NANOS = 800_000_000L;
    /** Avoid querying terminal dimensions on every game frame. */
    private static final long SIZE_REFRESH_INTERVAL_NANOS = 1_000_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** Style indexes used by the per-character style buffer and ANSI color table. */
    private static final int DEFAULT_STYLE = 0;
    private static final int BORDER_STYLE = 1;
    private static final int TITLE_STYLE = 2;
    private static final int PLATFORM_STYLE = 3;
    private static final int PLAYER_STYLE = 4;
    private static final int SCORE_STYLE = 5;
    private static final int CONTROL_STYLE = 6;
    private static final int GAME_OVER_STYLE = 7;
    /** ANSI escape sequences corresponding to the style indexes above. */
    private static final String[] ANSI_STYLES = {
            "\033[0m", "\033[90m", "\033[1;96m", "\033[1;32m",
            "\033[1;95m", "\033[1;93m", "\033[2;37m", "\033[1;91m"
    };

    /** Destination for rendered characters. */
    private final PrintStream output;
    /** Whether cursor positioning and colored ANSI sequences may be used. */
    private final boolean ansi;
    /** Live terminal, or null when rendering into a fixed-size test output. */
    private final Terminal terminal;
    /** Reused output assembly buffer to avoid creating a frame-sized string repeatedly. */
    private final StringBuilder frame = new StringBuilder(10_000);
    /** Current clamped dimensions and the reusable character/style grids. */
    private int columns;
    private int rows;
    private char[][] buffer;
    private int[][] styles;
    /** Time markers and counters used to refresh size, throttle output, and measure FPS. */
    private long lastSizeCheck;
    private long lastPlainRender;
    private long fpsWindowStart = System.nanoTime();
    private int fpsFrames;
    private int framesPerSecond;
    /** Ensures terminal restoration happens at most once, even during shutdown. */
    private final AtomicBoolean closed = new AtomicBoolean();
    /** Backup restoration in case the JVM exits before normal resource cleanup. */
    private final Thread shutdownHook;

    /** Creates a renderer with fixed dimensions, mainly for tests. */
    TerminalRenderer(PrintStream output, int columns, int rows, boolean ansi) {
        this(output, columns, rows, ansi, null);
    }

    /** Creates a renderer and allocates its reusable buffers for the given dimensions. */
    private TerminalRenderer(PrintStream output, int columns, int rows, boolean ansi, Terminal terminal) {
        this.output = output;
        this.columns = clamp(columns, MIN_COLUMNS, MAX_COLUMNS);
        this.rows = clamp(rows, MIN_ROWS, MAX_ROWS);
        this.ansi = ansi;
        this.terminal = terminal;
        allocateBuffers();
        // Switch to the alternate screen and hide the cursor when ANSI is enabled.
        if (ansi) {
            output.print("\033[?1049h\033[?25l\033[2J\033[H");
            output.flush();
        }
        shutdownHook = new Thread(this::restoreDisplay, "termijump-display-restore");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /** Builds a renderer from the current terminal's size and capabilities. */
    static TerminalRenderer open(PrintStream output, Terminal terminal) {
        Size size = terminal.getSize();
        boolean ansi = !"dumb".equalsIgnoreCase(terminal.getType())
                && System.getenv("NO_COLOR") == null;
        return new TerminalRenderer(output, size.getColumns(), size.getRows(), ansi, terminal);
    }

    /** Returns the usable width after subtracting the left and right borders. */
    int playWidth() {
        return columns - 2;
    }

    /** Returns the rows available to the game after reserving the status area. */
    int playHeight() {
        return Math.max(3, rows - 8);
    }

    /** Refreshes terminal dimensions occasionally and reallocates buffers if needed. */
    void refreshSize() {
        long now = System.nanoTime();
        if (terminal != null && now - lastSizeCheck > SIZE_REFRESH_INTERVAL_NANOS) {
            Size size = terminal.getSize();
            int newColumns = clamp(size.getColumns(), MIN_COLUMNS, MAX_COLUMNS);
            int newRows = clamp(size.getRows(), MIN_ROWS, MAX_ROWS);
            if (columns != newColumns || rows != newRows) {
                columns = newColumns;
                rows = newRows;
                allocateBuffers();
            }
            lastSizeCheck = now;
        }
    }

    /**
     * Builds and writes one frame containing the world, player, and status area.
     *
     * @param player current player position
     * @param platforms platforms currently retained by the game
     * @param cameraBottom world height aligned with the viewport bottom
     * @param score current run score
     * @param height maximum height reached in this run
     * @param best best score for this game session
     * @param state current game lifecycle state
     */
    void render(Player player, List<Platform> platforms, double cameraBottom,
                int score, int height, int best, GameState state) {
        // Limit plain output to avoid flooding logs when ANSI redraw is unavailable.
        if (!ansi && state == GameState.PLAYING
                && System.nanoTime() - lastPlainRender < PLAIN_RENDER_INTERVAL_NANOS) {
            return;
        }
        updateFrameRate();
        lastPlainRender = System.nanoTime();

        // Start from a blank grid before drawing this frame's border and contents.
        for (int row = 0; row < rows; row++) {
            // Reuse the frame arrays while clearing the previous frame's contents.
            char[] line = buffer[row];
            java.util.Arrays.fill(line, ' ');
            java.util.Arrays.fill(styles[row], DEFAULT_STYLE);
        }

        fillCorners(buffer[0], '+');
        fillCorners(buffer[rows - 1], '+');
        for (int column = 1; column < columns - 1; column++) {
            buffer[0][column] = '-';
            buffer[rows - 1][column] = '-';
            styles[0][column] = BORDER_STYLE;
            styles[rows - 1][column] = BORDER_STYLE;
        }
        for (int row = 1; row < rows - 1; row++) {
            buffer[row][0] = '|';
            buffer[row][columns - 1] = '|';
            styles[row][0] = BORDER_STYLE;
            styles[row][columns - 1] = BORDER_STYLE;
        }
        styles[0][0] = BORDER_STYLE;
        styles[0][columns - 1] = BORDER_STYLE;
        styles[rows - 1][0] = BORDER_STYLE;
        styles[rows - 1][columns - 1] = BORDER_STYLE;

        writeCentered(buffer, styles, TITLE_ROW, "TERMI-JUMP", TITLE_STYLE);
        int playTop = PLAY_TOP_ROW;
        int playBottom = Math.max(playTop, rows - PLAY_BOTTOM_ROW_OFFSET);
        // Draw visible platforms by mapping their world height into screen rows.
        for (Platform platform : platforms) {
            // Convert world height into a row measured upward from the viewport bottom.
            int screenY = playBottom - (int) Math.round(platform.y() - cameraBottom);
            if (screenY >= playTop && screenY <= playBottom) {
                int start = 1 + (int) Math.floor(platform.x());
                int end = 1 + (int) Math.ceil(platform.x() + platform.width());
                for (int column = Math.max(1, start); column < Math.min(columns - 1, end); column++) {
                    buffer[screenY][column] = '=';
                    styles[screenY][column] = PLATFORM_STYLE;
                }
            }
        }

        // Draw the player only when its mapped position is inside the play area.
        int playerRow = playBottom - (int) Math.round(player.y() - cameraBottom) - 1;
        int playerColumn = 1 + (int) Math.round(player.x());
        if (playerRow >= playTop && playerRow <= playBottom
                && playerColumn > 0 && playerColumn < columns - 1) {
            buffer[playerRow][playerColumn] = 'O';
            styles[playerRow][playerColumn] = PLAYER_STYLE;
        }

        // Place score and control information in fixed rows at the bottom of the frame.
        String fps = framesPerSecond == 0 ? "--" : Integer.toString(framesPerSecond);
        writeText(buffer, styles, rows - SCORE_ROW_OFFSET,
                "Score: " + score + "   Height: " + height + "   Best: " + best + "   FPS: " + fps,
                SCORE_STYLE);
        writeText(buffer, styles, rows - CONTROLS_ROW_OFFSET,
                "Controls: A/Left  D/Right  Q/Esc", CONTROL_STYLE);
        writeText(buffer, styles, rows - MESSAGE_ROW_OFFSET, state == GameState.GAME_OVER
                ? "GAME OVER - R: restart   Q: quit"
                : "Reach the highest platform!",
                state == GameState.GAME_OVER ? GAME_OVER_STYLE : CONTROL_STYLE);

        // ANSI output moves the cursor for each row; plain output starts a new frame.
        frame.setLength(0);
        if (ansi) {
            for (int row = 0; row < buffer.length; row++) {
                frame.append("\033[").append(row + 1).append(";1H");
                appendStyledRow(frame, buffer[row], styles[row]);
            }
        } else {
            // Plain terminals cannot redraw in place, so separate frames with a blank line.
            frame.append('\n');
            for (char[] line : buffer) {
                frame.append(line).append('\n');
            }
        }
        output.print(frame);
        output.flush();
    }

    /** Restores the display and unregisters the fallback shutdown hook. */
    @Override
    public void close() {
        restoreDisplay();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // The JVM is already shutting down; the registered hook restores the display.
        }
    }

    /** Restores the cursor and previous screen once, whether called normally or at shutdown. */
    private void restoreDisplay() {
        if (closed.compareAndSet(false, true) && ansi) {
            output.print("\033[?25h\033[?1049l");
            output.flush();
        }
    }

    /** Updates the displayed frame rate using a rolling one-second measurement window. */
    private void updateFrameRate() {
        long now = System.nanoTime();
        fpsFrames++;
        long elapsed = now - fpsWindowStart;
        if (elapsed >= NANOS_PER_SECOND) {
            framesPerSecond = (int) Math.round(fpsFrames * (double) NANOS_PER_SECOND / elapsed);
            fpsFrames = 0;
            fpsWindowStart = now;
        }
    }

    /** Writes a row while emitting ANSI style changes only when the style changes. */
    private void appendStyledRow(StringBuilder output, char[] characters, int[] styles) {
        int activeStyle = DEFAULT_STYLE;
        for (int column = 0; column < characters.length; column++) {
            int nextStyle = styles[column];
            if (nextStyle != activeStyle) {
                output.append(ANSI_STYLES[nextStyle]);
                activeStyle = nextStyle;
            }
            output.append(characters[column]);
        }
        if (activeStyle != DEFAULT_STYLE) {
            output.append(ANSI_STYLES[DEFAULT_STYLE]);
        }
    }

    /** Recreates the character and style grids after terminal dimensions change. */
    private void allocateBuffers() {
        buffer = new char[rows][columns];
        styles = new int[rows][columns];
    }

    /** Centers text within the terminal width and delegates clipping to writeText. */
    private void writeCentered(char[][] buffer, int[][] styles, int row, String text, int style) {
        int column = Math.max(1, (columns - text.length()) / 2);
        writeText(buffer, styles, row, column, text, style);
    }

    /** Writes text at the first interior column, keeping it away from the border. */
    private void writeText(char[][] buffer, int[][] styles, int row, String text, int style) {
        writeText(buffer, styles, row, 1, text, style);
    }

    /** Writes text at a chosen position and clips it at the right border. */
    private void writeText(char[][] buffer, int[][] styles, int row, int startColumn, String text, int style) {
        if (row < 0 || row >= buffer.length) {
            return;
        }
        for (int index = 0; index < text.length() && startColumn + index < columns - 1; index++) {
            buffer[row][startColumn + index] = text.charAt(index);
            styles[row][startColumn + index] = style;
        }
    }

    /** Sets the first and last characters of a row to the requested corner symbol. */
    private static void fillCorners(char[] line, char value) {
        line[0] = value;
        line[line.length - 1] = value;
    }

    /** Restricts a dimension to the supported minimum and maximum. */
    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
