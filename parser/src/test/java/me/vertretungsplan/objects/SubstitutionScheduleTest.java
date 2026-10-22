/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SubstitutionScheduleTest {

    @Test
    public void setLastChangeFromInstantUsesBerlinZone() {
        SubstitutionSchedule schedule = new SubstitutionSchedule();
        schedule.setLastChangeFromInstant(Instant.parse("2026-10-22T10:15:30Z"));

        // Berlin is on CEST (+02:00) on 22 October
        assertEquals(LocalDateTime.of(2026, 10, 22, 12, 15, 30), schedule.getLastChange());
        assertEquals("22.10.2026 12:15", schedule.getLastChangeString());
    }

    @Test
    public void setLastChangeFromInstantUsesBerlinZoneInWinter() {
        SubstitutionSchedule schedule = new SubstitutionSchedule();
        schedule.setLastChangeFromInstant(Instant.parse("2026-01-15T10:15:30Z"));

        // Berlin is on CET (+01:00) on 15 January
        assertEquals(LocalDateTime.of(2026, 1, 15, 11, 15, 30), schedule.getLastChange());
    }

    @Test
    public void setLastChangeFromDateUsesBerlinZone() {
        SubstitutionSchedule schedule = new SubstitutionSchedule();
        schedule.setLastChangeFromDate(Date.from(Instant.parse("2026-10-22T10:15:30Z")));

        assertEquals(LocalDateTime.of(2026, 10, 22, 12, 15, 30), schedule.getLastChange());
    }

    @Test
    public void getLastChangeStringIsNullWithoutData() {
        assertNull(new SubstitutionSchedule().getLastChangeString());
    }

    @Test
    public void explicitLastChangeStringWins() {
        SubstitutionSchedule schedule = new SubstitutionSchedule();
        schedule.setLastChangeFromInstant(Instant.parse("2026-10-22T10:15:30Z"));
        schedule.setLastChangeString("Datum unbekannt");

        assertEquals("Datum unbekannt", schedule.getLastChangeString());
    }
}
