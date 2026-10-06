package com.kakuaudit.observe.observers;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;

public class BlueprintStoreTest {
    @Test public void parsesCandidatesAndFallsBack() {
        BlueprintStore.setForTests(
                "{\"billingCandidates\":[\"a.B\",\"b.C\"],\"billingMethodPattern\":\"(x)\"}");
        assertEquals(Arrays.asList("a.B", "b.C"),
                BlueprintStore.candidates("billingCandidates", "d.E"));
        assertEquals("(x)", BlueprintStore.pattern("billingMethodPattern", "def"));
        // Missing key -> defaults (old installs never crash).
        assertEquals(Arrays.asList("d.E"),
                BlueprintStore.candidates("nope", "d.E"));
        assertEquals("def", BlueprintStore.pattern("nope", "def"));
        BlueprintStore.setForTests("");
    }

    @Test public void registryListsObservers() {
        assertTrue(ObserverRegistry.names().contains("StoreObserver"));
        assertTrue(ObserverRegistry.names().contains("LifecycleObserver"));
        assertTrue(ObserverRegistry.names().contains("NativeNetObserver"));
    }

    @Test public void configSwitches() {
        Config.setForTests("{\"observers\":{"
                + "\"StoreObserver\": true,"
                + "\"NativeNetObserver\": false}}");
        assertTrue(Config.observerEnabled("StoreObserver", false));
        assertFalse(Config.observerEnabled("NativeNetObserver", true));
        assertTrue(Config.observerEnabled("Missing", true)); // default wins
        Config.resetForTests();
    }
}
