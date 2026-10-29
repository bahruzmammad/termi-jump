package com.termijump;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

final class InputHandler implements InputSource, AutoCloseable {
    private static final long KEY_HOLD_NANOS = 180_000_000L;
    private static final long ESCAPE_TIMEOUT_NANOS = 80_000_000L;
    private static final long READ_TIMEOUT_MILLIS = 20;

    private final Terminal terminal;
    private final Attributes originalAttributes;
    private final NonBlockingReader reader;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean quitRequested = new AtomicBoolean();
    private final AtomicBoolean restartRequested = new AtomicBoolean();
    private volatile long leftUntil;
    private volatile long rightUntil;
    private volatile IOException inputFailure;
    private int escapeState;
    private long escapeStarted;
    private final Thread readerThread;

    InputHandler(Terminal terminal) {
        this.terminal = terminal;
        originalAttributes = terminal.enterRawMode();
        reader = terminal.reader();
        readerThread = new Thread(this::readKeys, "termijump-keyboard");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    @Override
    public boolean isLeftPressed() {
        return System.nanoTime() < leftUntil;
    }

    @Override
    public boolean isRightPressed() {
        return System.nanoTime() < rightUntil;
    }

    @Override
    public boolean isQuitRequested() throws IOException {
        checkInputFailure();
        return quitRequested.get();
    }

    @Override
    public boolean consumeRestartRequest() throws IOException {
        checkInputFailure();
        return restartRequested.getAndSet(false);
    }

    private void readKeys() {
        try {
            while (!closed.get()) {
                int value = reader.read(READ_TIMEOUT_MILLIS);
                long now = System.nanoTime();
                if (value == -1) {
                    quitRequested.set(true);
                    return;
                }
                if (value >= 0) {
                    processByte(value, now);
                }
                // a lone escape quits; a following byte may instead complete an arrow key.
                if (escapeState != 0 && now - escapeStarted > ESCAPE_TIMEOUT_NANOS) {
                    escapeState = 0;
                    quitRequested.set(true);
                }
            }
        } catch (IOException exception) {
            if (!closed.get()) {
                inputFailure = exception;
            }
        }
    }

    private void processByte(int value, long now) {
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

    private void processKey(int value, long now) {
        switch (Character.toLowerCase((char) value)) {
            // terminals report key presses rather than continuous holds, so extend movement briefly.
            case 'a' -> leftUntil = now + KEY_HOLD_NANOS;
            case 'd' -> rightUntil = now + KEY_HOLD_NANOS;
            case 'q' -> quitRequested.set(true);
            case 'r' -> restartRequested.set(true);
            default -> {
            }
        }
    }

    private void checkInputFailure() throws IOException {
        if (inputFailure != null) {
            throw new IOException("Keyboard input failed.", inputFailure);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        readerThread.interrupt();
        try {
            readerThread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping keyboard input.", exception);
        } finally {
            terminal.setAttributes(originalAttributes);
        }
    }
}
