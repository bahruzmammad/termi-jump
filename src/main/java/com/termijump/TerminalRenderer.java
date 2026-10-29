package com.termijump;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;

final class TerminalRenderer implements AutoCloseable {
    private static final int MIN_COLUMNS = 10;
    private static final int MAX_COLUMNS = 160;
    private static final int MIN_ROWS = 8;
    private static final int MAX_ROWS = 60;
    private static final int TITLE_ROW = 1;
    private static final int PLAY_TOP_ROW = 2;
    private static final int PLAY_BOTTOM_ROW_OFFSET = 6;
    private static final int SCORE_ROW_OFFSET = 4;
    private static final int CONTROLS_ROW_OFFSET = 3;
    private static final int MESSAGE_ROW_OFFSET = 2;
    private static final long PLAIN_RENDER_INTERVAL_NANOS = 800_000_000L;
    private static final long SIZE_REFRESH_INTERVAL_NANOS = 1_000_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static final int DEFAULT_STYLE = 0;
    private static final int BORDER_STYLE = 1;
    private static final int TITLE_STYLE = 2;
    private static final int PLATFORM_STYLE = 3;
    private static final int PLAYER_STYLE = 4;
    private static final int SCORE_STYLE = 5;
    private static final int CONTROL_STYLE = 6;
    private static final int GAME_OVER_STYLE = 7;
    private static final String[] ANSI_STYLES = {
            "\033[0m", "\033[90m", "\033[1;96m", "\033[1;32m",
            "\033[1;95m", "\033[1;93m", "\033[2;37m", "\033[1;91m"
    };

    private final PrintStream output;
    private final boolean ansi;
    private final Terminal terminal;
    private final StringBuilder frame = new StringBuilder(10_000);
    private int columns;
    private int rows;
    private char[][] buffer;
    private int[][] styles;
    private long lastSizeCheck;
    private long lastPlainRender;
    private long fpsWindowStart = System.nanoTime();
    private int fpsFrames;
    private int framesPerSecond;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread shutdownHook;

    TerminalRenderer(PrintStream output, int columns, int rows, boolean ansi) {
        this(output, columns, rows, ansi, null);
    }

    private TerminalRenderer(PrintStream output, int columns, int rows, boolean ansi, Terminal terminal) {
        this.output = output;
        this.columns = clamp(columns, MIN_COLUMNS, MAX_COLUMNS);
        this.rows = clamp(rows, MIN_ROWS, MAX_ROWS);
        this.ansi = ansi;
        this.terminal = terminal;
        allocateBuffers();
        if (ansi) {
            output.print("\033[?1049h\033[?25l\033[2J\033[H");
            output.flush();
        }
        shutdownHook = new Thread(this::restoreDisplay, "termijump-display-restore");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    static TerminalRenderer open(PrintStream output, Terminal terminal) {
        Size size = terminal.getSize();
        boolean ansi = !"dumb".equalsIgnoreCase(terminal.getType())
                && System.getenv("NO_COLOR") == null;
        return new TerminalRenderer(output, size.getColumns(), size.getRows(), ansi, terminal);
    }

    int playWidth() {
        return columns - 2;
    }

    int playHeight() {
        return Math.max(3, rows - 8);
    }

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

    void render(Player player, List<Platform> platforms, double cameraBottom,
                int score, int height, int best, GameState state) {
        if (!ansi && state == GameState.PLAYING
                && System.nanoTime() - lastPlainRender < PLAIN_RENDER_INTERVAL_NANOS) {
            return;
        }
        updateFrameRate();
        lastPlainRender = System.nanoTime();

        for (int row = 0; row < rows; row++) {
            // reuse the frame arrays while clearing the previous frame's contents.
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
        for (Platform platform : platforms) {
            // convert world height into a row measured upward from the viewport bottom.
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

        int playerRow = playBottom - (int) Math.round(player.y() - cameraBottom) - 1;
        int playerColumn = 1 + (int) Math.round(player.x());
        if (playerRow >= playTop && playerRow <= playBottom
                && playerColumn > 0 && playerColumn < columns - 1) {
            buffer[playerRow][playerColumn] = 'O';
            styles[playerRow][playerColumn] = PLAYER_STYLE;
        }

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

        frame.setLength(0);
        if (ansi) {
            for (int row = 0; row < buffer.length; row++) {
                frame.append("\033[").append(row + 1).append(";1H");
                appendStyledRow(frame, buffer[row], styles[row]);
            }
        } else {
            // plain terminals cannot redraw in place, so separate frames with a blank line.
            frame.append('\n');
            for (char[] line : buffer) {
                frame.append(line).append('\n');
            }
        }
        output.print(frame);
        output.flush();
    }

    @Override
    public void close() {
        restoreDisplay();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // the jvm is already shutting down; the registered hook restores the display.
        }
    }

    private void restoreDisplay() {
        if (closed.compareAndSet(false, true) && ansi) {
            output.print("\033[?25h\033[?1049l");
            output.flush();
        }
    }

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

    private void allocateBuffers() {
        buffer = new char[rows][columns];
        styles = new int[rows][columns];
    }

    private void writeCentered(char[][] buffer, int[][] styles, int row, String text, int style) {
        int column = Math.max(1, (columns - text.length()) / 2);
        writeText(buffer, styles, row, column, text, style);
    }

    private void writeText(char[][] buffer, int[][] styles, int row, String text, int style) {
        writeText(buffer, styles, row, 1, text, style);
    }

    private void writeText(char[][] buffer, int[][] styles, int row, int startColumn, String text, int style) {
        if (row < 0 || row >= buffer.length) {
            return;
        }
        for (int index = 0; index < text.length() && startColumn + index < columns - 1; index++) {
            buffer[row][startColumn + index] = text.charAt(index);
            styles[row][startColumn + index] = style;
        }
    }

    private static void fillCorners(char[] line, char value) {
        line[0] = value;
        line[line.length - 1] = value;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
