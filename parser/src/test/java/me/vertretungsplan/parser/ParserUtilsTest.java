/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import org.joda.time.DateTimeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

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

    @Test
    public void testGetClassesRegex() throws JSONException {
        final JSONObject data = new JSONObject();
        data.put("classes", "(0[5-9]|1[1-2])[A-F]");

        List<String> classes = ParserUtils.getClassesFromJson(data);
        assertEquals(Arrays.asList(
                "05A", "05B", "05C", "05D", "05E", "05F", "06A", "06B", "06C", "06D", "06E", "06F", "07A",
                "07B", "07C", "07D", "07E", "07F", "08A", "08B", "08C", "08D", "08E", "08F", "09A", "09B",
                "09C", "09D", "09E", "09F", "11A", "11B", "11C", "11D", "11E", "11F", "12A", "12B", "12C",
                "12D", "12E", "12F"), classes);
    }

    @Test
    public void testGetClassesArray() throws JSONException {
        final JSONObject data = new JSONObject();
        final JSONArray array = new JSONArray();
        array.put("05A").put("05B").put("05C");
        data.put("classes", array);

        List<String> classes = ParserUtils.getClassesFromJson(data);
        assertEquals(Arrays.asList("05A", "05B", "05C"), classes);
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisOffset(0);
        ParserUtils.init();
    }
}
