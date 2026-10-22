/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.vertretungsplan.objects.AdditionalInfo;
import me.vertretungsplan.utils.TimeZones;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The ical parsers define "today" as the day that has started in {@link TimeZones#BERLIN}. This test
 * pins the JVM default zone to UTC and fixes the clock to a moment where Berlin is already on the
 * next day, so a parser that mixed the default zone into that calculation would include an already
 * expired event.
 */
public class BaseIcalParserTest {
    private TimeZone originalDefaultTimeZone;

    @Before
    public void setUp() {
        originalDefaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(originalDefaultTimeZone);
        BaseIcalParser.clock = Clock.system(TimeZones.BERLIN);
    }

    @Test
    public void todayBoundaryUsesBerlinZone() throws IOException {
        // 23.10.2026 00:30 Berlin = 22.10.2026 22:30 UTC: in UTC still the 22nd, in Berlin already the 23rd
        BaseIcalParser.clock = Clock.fixed(Instant.parse("2026-10-22T22:30:00Z"), TimeZones.BERLIN);

        AdditionalInfo info = new FixtureIcalParser().getAdditionalInfo();
        String text = info.getText();

        // ends 22.10. 23:00 Berlin, i.e. before "today" (23.10. 00:00 Berlin)
        assertFalse(text, text.contains("Vergangener Termin"));
        // starts exactly at 23.10. 00:00 Berlin and is therefore still upcoming
        assertTrue(text, text.contains("Termin um Berliner Mitternacht"));
        assertTrue(text, text.contains("Termin in zwei Tagen"));
    }

    private static class FixtureIcalParser extends BaseIcalParser {
        @Override
        protected String getIcalUrl() {
            return "http://example.com/cal.ics";
        }

        @Override
        protected String httpGet(String url, String encoding) throws IOException {
            try (InputStream in = getClass().getResourceAsStream("/additionalinfo/cal.ics")) {
                if (in == null) {
                    throw new IOException("missing test resource /additionalinfo/cal.ics");
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
