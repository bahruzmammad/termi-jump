package com.termijump;

import java.io.IOException;

interface InputSource {
    boolean isLeftPressed();

    boolean isRightPressed();

    boolean isQuitRequested() throws IOException;

    boolean consumeRestartRequest() throws IOException;
}
