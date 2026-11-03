package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.Instant;

public class SleepSceneTest {
    private static final long NOW = Instant.parse("2026-11-03T06:00:00Z").toEpochMilli();

    @Test
    public void noteShowsWhenFresh() throws Exception {
        assertEquals("Rick and Morty's new season drops Sunday.", SleepScene.parseNote(
                "{\"note\":{\"text\":\" Rick and Morty's new season drops Sunday. \",\"updated\":\"2026-11-02T15:04:00Z\"},\"later\":1}", NOW));
    }

    @Test
    public void noNoteWhenNullEmptyOrStale() throws Exception {
        assertNull(SleepScene.parseNote("{\"note\":null}", NOW));
        assertNull(SleepScene.parseNote("{\"note\":{\"text\":\"  \"}}", NOW));
        assertNull(SleepScene.parseNote("{}", NOW));
        // a day and a minute old: a stalled job must not leave it up
        assertNull(SleepScene.parseNote("{\"note\":{\"text\":\"old\",\"updated\":\"2026-11-02T05:59:00Z\"}}", NOW));
    }

    @Test(expected = org.json.JSONException.class)
    public void notJsonKeepsTheLastNote() throws Exception {
        SleepScene.parseNote("<!doctype html><title>Chester</title>", NOW);   // the feed not deployed yet
    }

    @Test
    public void weatherFromOpenMeteo() throws Exception {
        SleepScene.Weather w = SleepScene.parseWeather("{\"current\":{\"temperature_2m\":67.6,\"weather_code\":61,\"is_day\":0},"
                + "\"daily\":{\"temperature_2m_max\":[74.2],\"temperature_2m_min\":[61.5]}}");
        assertEquals(68, w.temp);
        assertEquals(74, w.hi);
        assertEquals(62, w.lo);
        assertEquals("rain", w.icon);
    }

    @Test
    public void skyCodes() {
        assertArrayEquals(new String[]{"night", "Clear"}, SleepScene.skyOf(0, false));
        assertArrayEquals(new String[]{"partly", "Partly cloudy"}, SleepScene.skyOf(2, true));
        assertArrayEquals(new String[]{"snow", "Snow"}, SleepScene.skyOf(73, true));
        assertArrayEquals(new String[]{"storm", "Storms"}, SleepScene.skyOf(95, true));
    }
}
