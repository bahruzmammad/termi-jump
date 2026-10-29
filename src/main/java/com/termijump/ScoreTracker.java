package com.termijump;

/**
 * Calculates the score from the highest point reached during a run.
 *
 * <p>The best score belongs to this tracker rather than a single run, so
 * resetting a run does not erase it.</p>
 */
final class ScoreTracker {
    /** Number of points awarded for each whole height unit. */
    private static final int POINTS_PER_HEIGHT_UNIT = 10;

    /** Height at which a new run begins; it is subtracted from displayed height. */
    private final double startHeight;
    /** Highest player coordinate observed in the current run. */
    private double maximumHeight;
    /** Highest score observed by this tracker across all its runs. */
    private int bestScore;

    /** Creates a tracker whose first run begins at the given world height. */
    ScoreTracker(double startHeight) {
        this.startHeight = startHeight;
        resetRun();
    }

    /** Starts a fresh run without changing the best score. */
    void resetRun() {
        // The best score intentionally survives a restart.
        maximumHeight = startHeight;
    }

    /** Records a player's height and updates the best score if needed. */
    void update(double playerHeight) {
        maximumHeight = Math.max(maximumHeight, playerHeight);
        bestScore = Math.max(bestScore, score());
    }

    /** Returns the maximum run height above the start, rounded down to an integer. */
    int height() {
        return Math.max(0, (int) Math.floor(maximumHeight - startHeight));
    }

    /** Converts the tracked height into points. */
    int score() {
        return height() * POINTS_PER_HEIGHT_UNIT;
    }

    /** Returns the best score recorded since this tracker was created. */
    int bestScore() {
        return bestScore;
    }
}
