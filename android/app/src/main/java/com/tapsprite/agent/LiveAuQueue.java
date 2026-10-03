package com.tapsprite.agent;

/**
 * Short video queue for one live viewer or the phone sender.
 * Capacity is 3 access units. Overflow flushes the queue, drops deltas until
 * the next keyframe, and asks the caller to request a new keyframe. A pending
 * keyframe is not kept in front of a later delta: that delta would reference
 * a frame the decoder never saw.
 */
final class LiveAuQueue {
    static final int CAP = 3;

    private final byte[][] slots = new byte[CAP][];
    private int n;
    private boolean waitKey;

    static boolean isKey(byte[] msg) {
        return msg != null && msg.length >= 2 && msg[0] == 2 && (msg[1] & 1) == 1;
    }

    int size() {
        return n;
    }

    boolean waitingForKey() {
        return waitKey;
    }

    /**
     * @return true when the queue overflowed and the encoder must emit a new keyframe
     */
    boolean offer(byte[] msg) {
        boolean key = isKey(msg);
        if (waitKey) {
            if (!key) {
                return false;
            }
            waitKey = false;
        }
        if (n >= CAP) {
            n = 0;
            if (key) {
                slots[0] = msg;
                n = 1;
                waitKey = false;
            } else {
                waitKey = true;
            }
            return true;
        }
        slots[n++] = msg;
        return false;
    }

    byte[] poll() {
        if (n <= 0) {
            return null;
        }
        byte[] msg = slots[0];
        for (int i = 1; i < n; i++) {
            slots[i - 1] = slots[i];
        }
        slots[--n] = null;
        return msg;
    }

    boolean isEmpty() {
        return n <= 0;
    }

    /** Drop queued frames and ignore deltas until a keyframe arrives. */
    void dropUntilKey() {
        n = 0;
        waitKey = true;
    }
}
