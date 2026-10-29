package com.termijump;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

/**
 * Reads terminal input on a background thread and exposes it as game commands.
 *
 * <p>Raw mode makes key presses available immediately. Arrow keys arrive as
 * multi-byte escape sequences, while letter keys arrive as single bytes.</p>
 */
final class InputHandler implements InputSource, AutoCloseable {
    /** How long a direction remains active after one key press. */
    private static final long KEY_HOLD_NANOS = 180_000_000L;
    /** Maximum wait for the rest of an Escape or arrow-key sequence. */
    private static final long ESCAPE_TIMEOUT_NANOS = 80_000_000L;
    /** Short blocking read interval so shutdown and key processing stay responsive. */
    private static final long READ_TIMEOUT_MILLIS = 20;

    /** Terminal whose mode and keyboard reader are managed by this handler. */
    private final Terminal terminal;
    /** Terminal attributes saved before entering raw mode, for restoration on close. */
    private final Attributes originalAttributes;
    /** JLine reader used to receive key bytes without waiting for a full line. */
    private final NonBlockingReader reader;
    /** Thread-safe lifecycle and command flags shared with the game thread. */
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean quitRequested = new AtomicBoolean();
    private final AtomicBoolean restartRequested = new AtomicBoolean();
    /** Latest times when a left or right key press should stop affecting movement. */
    private volatile long leftUntil;
    private volatile long rightUntil;
    /** Captures reader errors so the game thread can report them rather than hiding them. */
    private volatile IOException inputFailure;
    /** Parser state: 0 means normal input, 1 means Escape seen, 2 means '[' seen. */
    private int escapeState;
    /** Time when the current Escape sequence began. */
    private long escapeStarted;
    /** Daemon thread that reads keys while the game loop runs. */
    private final Thread readerThread;

    /** Saves terminal settings, enters raw mode, and starts the input reader. */
    InputHandler(Terminal terminal) {
        this.terminal = terminal;
        originalAttributes = terminal.enterRawMode();
        reader = terminal.reader();
        readerThread = new Thread(this::readKeys, "termijump-keyboard");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /** Treats a direction key as active until its short hold window expires. */
    @Override
    public boolean isLeftPressed() {
        return System.nanoTime() < leftUntil;
    }

    /** Treats a direction key as active until its short hold window expires. */
    @Override
    public boolean isRightPressed() {
        return System.nanoTime() < rightUntil;
    }

    /** Reports a quit command, first forwarding any reader-thread failure. */
    @Override
    public boolean isQuitRequested() throws IOException {
        checkInputFailure();
        return quitRequested.get();
    }

    /** Reads and clears the restart flag so one press causes only one restart. */
    @Override
    public boolean consumeRestartRequest() throws IOException {
        checkInputFailure();
        return restartRequested.getAndSet(false);
    }

    /** Repeatedly reads bytes and converts them into movement or lifecycle requests. */
    private void readKeys() {
        try {
            while (!closed.get()) {
                int value = reader.read(READ_TIMEOUT_MILLIS);
                long now = System.nanoTime();
                // End-of-input is treated as a quit request, like closing the terminal.
                if (value == -1) {
                    quitRequested.set(true);
                    return;
                }
                if (value >= 0) {
                    processByte(value, now);
                }
                // A lone Escape key quits; a following byte may complete an arrow-key sequence.
                if (escapeState != 0 && now - escapeStarted > ESCAPE_TIMEOUT_NANOS) {
                    escapeState = 0;
                    quitRequested.set(true);
                }
            }
        } catch (IOException exception) {
            if (!closed.get()) {
                // Surface asynchronous read errors on the next game-thread input check.
                inputFailure = exception;
            }
        }
    }

    /** Advances the Escape-sequence parser or handles a regular key byte. */
    private void processByte(int value, long now) {
        // Arrow keys arrive as an Escape-prefixed sequence rather than a single character.
        if (escapeState == 1) {
            if (value == '[') {
                escapeState = 2;
            } else {
                escapeState = 0;
                if (value == 27) {
                    quitRequested.set(true);
                } else {
                    processKey(value, now);
                }
            }
            return;
        }
        if (escapeState == 2) {
            escapeState = 0;
            if (value == 'D') {
                leftUntil = now + KEY_HOLD_NANOS;
            } else if (value == 'C') {
                rightUntil = now + KEY_HOLD_NANOS;
            }
            return;
        }
        if (value == 27) {
            escapeState = 1;
            escapeStarted = now;
        } else {
            processKey(value, now);
        }
    }

    /** Maps supported letter keys to commands and ignores all other keys. */
    private void processKey(int value, long now) {
        switch (Character.toLowerCase((char) value)) {
            // Terminals report key presses rather than continuous holds, so extend movement briefly.
            case 'a' -> leftUntil = now + KEY_HOLD_NANOS;
            case 'd' -> rightUntil = now + KEY_HOLD_NANOS;
            case 'q' -> quitRequested.set(true);
            case 'r' -> restartRequested.set(true);
            default -> {
            }
        }
    }

    /** Re-throws a background read error on the thread that owns the game loop. */
    private void checkInputFailure() throws IOException {
        if (inputFailure != null) {
            throw new IOException("Keyboard input failed.", inputFailure);
        }
    }

    /** Stops input, waits for the reader, and restores the terminal's saved mode. */
    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        readerThread.interrupt();
        try {
            readerThread.join();
        } catch (InterruptedException exception) {
            // Preserve the caller's interruption signal after reporting the failed wait.
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping keyboard input.", exception);
        } finally {
            terminal.setAttributes(originalAttributes);
        }
    }
}
