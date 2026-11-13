package io.github.refinedchicken.tailcraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TailcraftAddressTest {
    /** A real address produced by tailcraft-helper (throwaway key). */
    static final String REAL = "tcpGFwWCBzTJ9SkvyjxZegO0oxpSqSs1oFK280E81M9cIJyj07J2FrWCDdk_rJb4LULuZLn3QV3BUBujUsensmCFcX9TJTGCQ3GGFxWCDWUybtANWPlp7ChCee5em8rVBKGwjC0YibXz7_3YKsUWFpGQEt";

    @Test void acceptsRealAddress() { assertTrue(TailcraftAddress.isAddress(REAL)); }
    @Test void acceptsSurroundingWhitespace() { assertTrue(TailcraftAddress.isAddress("  " + REAL + "\n")); }
    @Test void rejectsHostnames() {
        assertFalse(TailcraftAddress.isAddress("localhost"));
        assertFalse(TailcraftAddress.isAddress("tcraft.net"));
        assertFalse(TailcraftAddress.isAddress("tc"));
        assertFalse(TailcraftAddress.isAddress("tc" + "a".repeat(100)));
        assertFalse(TailcraftAddress.isAddress(REAL + ".example.com"));
        assertFalse(TailcraftAddress.isAddress(REAL + ":25565"));
        assertFalse(TailcraftAddress.isAddress(null));
    }
}
