package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class ChesterLooksTest {
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final double LAT = 38.9536, LNG = -94.7336;   // Lenexa, KS

    private static int[] at(String isoLocal) {
        return ChesterLooks.resolve(ZonedDateTime.of(LocalDateTime.parse(isoLocal), CHICAGO), LAT, LNG);
    }

    @Test public void weekdayRunsMorningDayEveningNight() {
        // Tuesday 3 Nov 2026: sunrise about 06:58, sunset about 17:10
        assertArrayEquals(ChesterLooks.HARBOR_MORNING, at("2026-11-03T08:00"));
        assertArrayEquals(ChesterLooks.STUDY_DAY, at("2026-11-03T12:00"));
        assertArrayEquals(ChesterLooks.WORKPLACE_EVENING, at("2026-11-03T19:00"));
        assertArrayEquals(ChesterLooks.HARBOR_NIGHT, at("2026-11-03T23:30"));
        assertArrayEquals(ChesterLooks.HARBOR_NIGHT, at("2026-11-03T03:00"));
    }

    @Test public void weekendIsTheParlorFromFridayAfternoonToSundayNight() {
        assertArrayEquals(ChesterLooks.STUDY_DAY, at("2026-11-06T14:00"));     // Friday before 15:00
        assertArrayEquals(ChesterLooks.PARLOR_DAY, at("2026-11-06T16:00"));
        assertArrayEquals(ChesterLooks.PARLOR_DAY, at("2026-11-07T13:00"));    // Saturday
        assertArrayEquals(ChesterLooks.PARLOR_NIGHT, at("2026-11-07T21:00"));
        assertArrayEquals(ChesterLooks.HARBOR_NIGHT, at("2026-11-08T23:30"));  // Sunday after 22:30
    }

    @Test public void fadesForThirtyMinutesAcrossABoundary() {
        assertArrayEquals(ChesterLooks.WORKPLACE_EVENING, at("2026-11-03T22:14"));
        int[] mid = at("2026-11-03T22:30");
        assertNotEquals(ChesterLooks.WORKPLACE_EVENING[0], mid[0]);
        assertNotEquals(ChesterLooks.HARBOR_NIGHT[0], mid[0]);
        assertArrayEquals(ChesterLooks.HARBOR_NIGHT, at("2026-11-03T22:46"));
    }

    @Test public void fixedClockWithoutALocation() {
        int[] noon = ChesterLooks.resolve(ZonedDateTime.of(LocalDateTime.parse("2026-11-03T12:00"), CHICAGO),
                Double.NaN, Double.NaN);
        assertArrayEquals(ChesterLooks.STUDY_DAY, noon);
    }

    @Test public void holidaySkinsOverrideTheRingAndSometimesTheBackground() {
        int[] halloween = at("2026-10-31T13:00");   // a Saturday: parlor, skinned
        assertEquals(0x0e0c10, halloween[0]);
        assertEquals(0xe08a52, halloween[3]);
        assertEquals(ChesterLooks.PARLOR_DAY[1], halloween[1]);
        int[] christmas = at("2026-12-28T12:00");   // a Monday: study day, ring only
        assertEquals(ChesterLooks.STUDY_DAY[0], christmas[0]);
        assertEquals(0xcf8b7d, christmas[3]);
    }

    @Test public void calendarMatchesChester() {
        assertEquals("christmas-eve", ChesterLooks.specialDay(LocalDate.of(2026, 12, 24)));
        assertEquals("christmas", ChesterLooks.specialDay(LocalDate.of(2026, 12, 30)));
        assertEquals("nye", ChesterLooks.specialDay(LocalDate.of(2026, 12, 31)));
        assertEquals("nyd", ChesterLooks.specialDay(LocalDate.of(2027, 1, 1)));
        assertEquals("birthday", ChesterLooks.specialDay(LocalDate.of(2026, 10, 16)));
        assertEquals(LocalDate.of(2026, 4, 5), ChesterLooks.easterSunday(2026));
        assertEquals(LocalDate.of(2027, 3, 28), ChesterLooks.easterSunday(2027));
        assertEquals("easter", ChesterLooks.specialDay(LocalDate.of(2027, 3, 28)));
        assertNull(ChesterLooks.specialDay(LocalDate.of(2026, 11, 3)));
    }

    @Test public void oklchMixKeepsItsEnds() {
        int a = ChesterLooks.WORKPLACE_EVENING[3], b = ChesterLooks.HARBOR_NIGHT[3];
        assertEquals(a, ChesterLooks.mix(a, b, 0));
        assertEquals(b, ChesterLooks.mix(a, b, 1));
        for (int[] look : new int[][]{ChesterLooks.STUDY_DAY, ChesterLooks.PARLOR_NIGHT}) {
            for (int c : look) {
                double[] o = ChesterLooks.oklch(c);
                assertEquals(c, ChesterLooks.rgb(o[0], o[1], o[2]));
            }
        }
    }

    @Test public void locationParsing() {
        assertArrayEquals(new double[]{38.9536, -94.7336}, ChesterLooks.parseLocation("38.9536, -94.7336"), 1e-9);
        assertNull(ChesterLooks.parseLocation(""));
        assertNull(ChesterLooks.parseLocation("Lenexa"));
        assertNull(ChesterLooks.parseLocation("91,0"));
    }
}
