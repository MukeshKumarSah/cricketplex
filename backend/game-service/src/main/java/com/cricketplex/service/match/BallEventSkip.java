package com.cricketplex.service.match;

public final class BallEventSkip {
    private static final ThreadLocal<Boolean> SKIP_BALL_EVENTS = ThreadLocal.withInitial(() -> false);

    private BallEventSkip() {}

    public static void setSkipBallEvents(boolean skip) { SKIP_BALL_EVENTS.set(skip); }

    public static boolean isSkip() { return Boolean.TRUE.equals(SKIP_BALL_EVENTS.get()); }
}
