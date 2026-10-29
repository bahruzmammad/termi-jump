package com.termijump;

record Platform(double x, double y, double width) {
    Platform {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                || x < 0 || y < 0 || width <= 0) {
            throw new IllegalArgumentException(
                    "platform coordinates must be finite and non-negative, and width must be positive");
        }
    }

    boolean contains(double playerX) {
        return playerX >= x - 0.5 && playerX <= x + width + 0.5;
    }
}
