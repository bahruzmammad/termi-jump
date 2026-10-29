package com.termijump;

/** Describes whether the game is active, finished, or shutting down. */
enum GameState {
    /** The player can move and each frame updates the world. */
    PLAYING,
    /** The run has ended; the player may restart or quit. */
    GAME_OVER,
    /** The main loop has received a request to stop. */
    QUIT
}
