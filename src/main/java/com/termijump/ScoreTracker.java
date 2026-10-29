package com.termijump;

final class ScoreTracker {
    private static final int POINTS_PER_HEIGHT_UNIT = 10;

    private final double startHeight;
    private double maximumHeight;
    private int bestScore;

    ScoreTracker(double startHeight) {
        this.startHeight = startHeight;
        resetRun();
    }

    void resetRun() {
        // bestScore intentionally survives a restart.
        maximumHeight = startHeight;
    }

    void update(double playerHeight) {
        maximumHeight = Math.max(maximumHeight, playerHeight);
        bestScore = Math.max(bestScore, score());
    }

    int height() {
        return Math.max(0, (int) Math.floor(maximumHeight - startHeight));
    }

    int score() {
        return height() * POINTS_PER_HEIGHT_UNIT;
    }

    int bestScore() {
        return bestScore;
    }
}
