package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PinsTest {
    @Test public void darkScenesKeepTheThemesStrength() {
        assertEquals(0.55f, Pins.dim(0.2f, 0.55f), 1e-6);
        assertEquals(0.55f, Pins.dim(0.3f, 0.55f), 1e-6);
    }

    @Test public void brightScenesAreDimmedDownToFortyPercent() {
        assertEquals(0.275f, Pins.dim(0.6f, 0.55f), 1e-6);
        assertEquals(0.22f, Pins.dim(1f, 0.55f), 1e-6);
        assertEquals(0.22f, Pins.dim(0.9f, 0.55f), 1e-6);
    }
}
