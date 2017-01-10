/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ParserUtilsTest {
    @Test
    public void testNewYear() {
        DateTimeUtils.setCurrentMillisFixed(1450911600000L); // 24.12.2015
        ParserUtils.init();
        assertEquals(2016, ParserUtils.parseDate("1.1. Freitag").getYear());
        assertEquals(2015, ParserUtils.parseDate("31.12. Donnerstag").getYear());
        assertEquals(2016, ParserUtils.parseDateTime("1.1. Freitag 12:00").getYear());
        assertEquals(2015, ParserUtils.parseDateTime("31.12. Donnerstag 12:00").getYear());

        DateTimeUtils.setCurrentMillisFixed(1452034800000L); // 06.01.2016
        ParserUtils.init();
        assertEquals(2016, ParserUtils.parseDate("1.1. Freitag").getYear());
        assertEquals(2015, ParserUtils.parseDate("31.12. Donnerstag").getYear());
        assertEquals(2016, ParserUtils.parseDateTime("1.1. Freitag 12:00").getYear());
        assertEquals(2015, ParserUtils.parseDateTime("31.12. Donnerstag 12:00").getYear());
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisOffset(0);
        ParserUtils.init();
    }
}
