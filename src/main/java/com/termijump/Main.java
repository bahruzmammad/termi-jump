package com.termijump;

import java.io.IOError;
import java.io.IOException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try (Terminal terminal = TerminalBuilder.builder().system(true).provider("jni").build();
             InputHandler input = new InputHandler(terminal);
             TerminalRenderer renderer = TerminalRenderer.open(System.out, terminal)) {
            new Game(input, renderer).run();
        } catch (IOException | IOError exception) {
            System.err.println("Termi-Jump: " + exception.getMessage());
            System.exit(1);
        }
    }
}
