package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProjectorBarTest {
    @Test
    public void sleepStepsCycleFromTheOneChosen() {
        assertEquals(30, ProjectorBar.nextSleepStep(0));
        assertEquals(60, ProjectorBar.nextSleepStep(30));
        assertEquals(90, ProjectorBar.nextSleepStep(60));
        assertEquals(0, ProjectorBar.nextSleepStep(90));    // then off
        assertEquals(0, ProjectorBar.nextSleepStep(120));   // a longer timer set in XGIMI's menu: off
    }

    @Test
    public void dialMovesToTheNearestNodeThatWay() {
        // the mock's layout: the centre, then five nodes from the top, clockwise
        float[] x = new float[6], y = new float[6];
        x[0] = 960;
        y[0] = 500;
        for (int i = 1; i < 6; i++) {
            double a = Math.toRadians(-90 + 72 * (i - 1));
            x[i] = (float) (960 + 250 * Math.cos(a));
            y[i] = (float) (500 + 250 * Math.sin(a));
        }
        assertEquals(1, ProjectorDial.nearest(x, y, 0, 0, -1));   // up from the centre: focus, at the top
        assertEquals(0, ProjectorDial.nearest(x, y, 1, 0, 1));    // and back down
        assertEquals(2, ProjectorDial.nearest(x, y, 0, 1, 0));    // right: zoom
        assertEquals(5, ProjectorDial.nearest(x, y, 0, -1, 0));   // left: shift
        assertEquals(-1, ProjectorDial.nearest(x, y, 1, 0, -1));  // nothing above the top node
    }
}
