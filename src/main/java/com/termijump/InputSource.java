package com.termijump;

import java.io.IOException;

/**
 * Small interface between game logic and keyboard input.
 *
 * <p>The real implementation reads a terminal. Tests can implement this
 * interface with simple boolean fields instead.</p>
 */
interface InputSource {
    /** Returns whether the left movement key is currently active. */
    boolean isLeftPressed();

    /** Returns whether the right movement key is currently active. */
    boolean isRightPressed();

    /** Returns whether the player has requested that the game quit. */
    boolean isQuitRequested() throws IOException;

    /** Returns and clears a pending restart request so it is handled only once. */
    boolean consumeRestartRequest() throws IOException;
}
