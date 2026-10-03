package com.tapsprite.agent;

/**
 * Codec buffers to Annex-B. A length-prefixed (AVCC) access unit is converted
 * NAL by NAL. A raw NAL gets one start code. An existing start code is kept.
 */
final class LiveAnnex {
    private LiveAnnex() {
    }

    static byte[] toAnnexB(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return raw == null ? new byte[0] : raw;
        }
        if (hasStart(raw)) {
            return raw;
        }
        byte[] avcc = avccToAnnexB(raw);
        if (avcc != null) {
            return avcc;
        }
        byte[] out = new byte[raw.length + 4];
        out[3] = 1;
        System.arraycopy(raw, 0, out, 4, raw.length);
        return out;
    }

    static boolean hasStart(byte[] d) {
        if (d.length >= 4 && d[0] == 0 && d[1] == 0 && (d[2] == 1 || (d[2] == 0 && d[3] == 1))) {
            return true;
        }
        return d.length >= 3 && d[0] == 0 && d[1] == 0 && d[2] == 1;
    }

    /** @return Annex-B, or null when {@code raw} is not a whole AVCC buffer */
    static byte[] avccToAnnexB(byte[] raw) {
        int off = 0;
        int nals = 0;
        while (off + 4 <= raw.length) {
            int len = ((raw[off] & 0xff) << 24)
                    | ((raw[off + 1] & 0xff) << 16)
                    | ((raw[off + 2] & 0xff) << 8)
                    | (raw[off + 3] & 0xff);
            if (len <= 0 || off + 4 + len > raw.length) {
                return null;
            }
            int hdr = raw[off + 4] & 0xff;
            if ((hdr & 0x80) != 0) {
                return null;
            }
            if ((hdr & 0x1f) == 0) {
                return null;
            }
            off += 4 + len;
            nals++;
        }
        if (off != raw.length || nals == 0) {
            return null;
        }
        // Each 4-byte length is replaced by a 4-byte start code, so the size is unchanged.
        byte[] out = new byte[raw.length];
        int w = 0;
        int r = 0;
        while (r + 4 <= raw.length) {
            int len = ((raw[r] & 0xff) << 24)
                    | ((raw[r + 1] & 0xff) << 16)
                    | ((raw[r + 2] & 0xff) << 8)
                    | (raw[r + 3] & 0xff);
            out[w] = 0;
            out[w + 1] = 0;
            out[w + 2] = 0;
            out[w + 3] = 1;
            System.arraycopy(raw, r + 4, out, w + 4, len);
            w += 4 + len;
            r += 4 + len;
        }
        return out;
    }
}
