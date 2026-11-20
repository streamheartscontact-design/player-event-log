package playereventlog.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionMapTest {
    @Test
    void supportedVersionsMapToTheirReader() {
        assertEquals("v1_16_5", PelFabric.implementationFor("1.16.5"));
        assertEquals("v1_18_2", PelFabric.implementationFor("1.18.2"));
        assertEquals("v1_20_1", PelFabric.implementationFor("1.20"));
        assertEquals("v1_20_1", PelFabric.implementationFor("1.20.1"));
        assertEquals("v1_21_1", PelFabric.implementationFor("1.21"));
        assertEquals("v1_21_1", PelFabric.implementationFor("1.21.1"));
        assertEquals("v1_21_11", PelFabric.implementationFor("1.21.11"));
        assertEquals("v26_3", PelFabric.implementationFor("26.3"));
    }

    @Test
    void otherVersionsAreSwitchedOff() {
        for (String v : new String[] {"1.16.4", "1.17.1", "1.19.2", "1.20.4", "1.21.4", "1.21.10", "26.1", "27.1", "unknown", "", null}) {
            assertNull(PelFabric.implementationFor(v), String.valueOf(v));
        }
    }
}
