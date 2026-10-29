package com.termijump;

import java.io.IOError;
import java.io.IOException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Application entry point.
 *
 * <p>Creates the terminal resources, starts the game, and ensures the terminal
 * is restored when the game finishes.</p>
 */
public final class Main {
    /** Prevents instances of this utility-style entry-point class. */
    private Main() {
    }

    /** Opens the terminal and runs the game until the player quits. */
    public static void main(String[] args) {
        // try-with-resources closes each resource in reverse order, even after an exception.
        try (Terminal terminal = TerminalBuilder.builder().system(true).provider("jni").build();
             InputHandler input = new InputHandler(terminal);
             TerminalRenderer renderer = TerminalRenderer.open(System.out, terminal)) {
            new Game(input, renderer).run();
        } catch (IOException | IOError exception) {
            // Report terminal failures to stderr and return a non-zero process status.
            System.err.println("Termi-Jump: " + exception.getMessage());
            System.exit(1);
        }
    }
}
