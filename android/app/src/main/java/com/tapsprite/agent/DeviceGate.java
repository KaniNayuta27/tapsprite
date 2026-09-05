package com.tapsprite.agent;

/**
 * Serializes Android touch / screenshot at the Java boundary.
 * Parallel Lua states must not share a VM bytecode lock; they only contend here.
 */
final class DeviceGate {
    static final Object LOCK = new Object();

    private DeviceGate() {
    }
}
