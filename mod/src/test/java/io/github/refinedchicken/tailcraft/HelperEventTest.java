package io.github.refinedchicken.tailcraft;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class HelperEventTest {
    @Test void parsesHostReady() throws Exception {
        assertEquals(TailcraftAddressTest.REAL, HelperEvent.parse("{\"type\":\"ready\",\"mode\":\"host\",\"address\":\"" + TailcraftAddressTest.REAL + "\"}").address());
    }
    @Test void rejectsHostReadyWithoutAddress() { assertThrows(IOException.class, () -> HelperEvent.parse("{\"type\":\"ready\",\"mode\":\"host\",\"address\":\"example.com\"}")); }
    @Test void parsesJoinReady() throws Exception { assertEquals("127.0.0.1:5", HelperEvent.parse("{\"type\":\"ready\",\"mode\":\"join\",\"address\":\"127.0.0.1:5\"}").address()); }
    @Test void rejectsUnknownFields() { assertThrows(IOException.class, () -> HelperEvent.parse("{\"type\":\"stopped\",\"extra\":1}")); }
    @Test void rejectsNonLoopbackJoin() { assertThrows(IOException.class, () -> HelperEvent.parse("{\"type\":\"ready\",\"mode\":\"join\",\"address\":\"0.0.0.0:1\"}")); }
    @Test void rejectsMalformed() { assertThrows(IOException.class, () -> HelperEvent.parse("not json")); }
}
