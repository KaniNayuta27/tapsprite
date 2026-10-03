package com.tapsprite.agent;

/* loaded from: classes.dex */
public final class PointXY {
    public int x = -1;
    public int y = -1;

    public void clear() {
        this.x = -1;
        this.y = -1;
    }

    public boolean found() {
        return this.x >= 0 && this.y >= 0;
    }

    public String toString() {
        return this.x + "," + this.y;
    }
}
