package com.tapsprite.agent;

/** 30 or 60. Pure so unit tests can pin the {op:fps} choice. */
final class LiveFps {
    static final int LOW = 30;
    static final int HIGH = 60;

    private LiveFps() {
    }

    /** Anything at or above 60 is the high mode. Every other value, including missing, is 30. */
    static int normalize(int requested) {
        return requested >= HIGH ? HIGH : LOW;
    }
}
