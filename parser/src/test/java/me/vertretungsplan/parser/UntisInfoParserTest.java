/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UntisInfoParserTest extends BaseDemoTest {

    private String html;

    @Before
    public void setUp() {
        html = readResource("/untis-info/navbar.html");
    }

    @Test
    public void testGetClasses() throws Exception {
        List<String> classes = UntisInfoParser.parseClasses(html, new JSONObject());
        assertEquals(
                Arrays.asList(new String[]{"UAEG2", "UAEG", "SL", "VW", "PB", "AEG1", "13", "12", "11", "10E", "10D",
                        "10C", "10B", "10A", "09D", "09C", "09B", "09A", "08F", "08E", "08D", "08C", "08B", "08A",
                        "07F", "07E", "07D",
                        "07C", "07B", "07A", "06G2", "06G1", "06D", "06C", "06B", "06A", "05G2", "05G1", "05C", "05B",
                        "05A", "ESL"}),
                classes);
    }

    @Test
    public void testGetClassesRegex() throws Exception {
        final JSONObject data = new JSONObject();
        data.put(UntisInfoParser.PARAM_CLASS_SELECT_REGEX, "\\d+");
        List<String> classes = UntisInfoParser.parseClasses(html, data);
        assertEquals(Arrays.asList(
                new String[]{"2", "UAEG", "SL", "VW", "PB", "1", "13", "12", "11", "10", "10", "10", "10", "10",
                        "09", "09", "09", "09", "08", "08", "08", "08", "08", "08", "07", "07", "07", "07", "07", "07",
                        "06", "06", "06",
                        "06", "06", "06", "05", "05", "05", "05", "05", "ESL"}),
                classes);
    }

    @Test
    public void testGetClassesRegexRemove() throws Exception {
        final JSONObject data = new JSONObject();
        data.put(UntisInfoParser.PARAM_CLASS_SELECT_REGEX, "^\\d+\\w?\\d?$");
        data.put(UntisInfoParser.PARAM_REMOVE_NON_MATCHING_CLASSES, true);
        List<String> classes = UntisInfoParser.parseClasses(html, data);
        assertEquals(Arrays.asList(
                new String[]{"13", "12", "11", "10E", "10D", "10C", "10B", "10A", "09D", "09C", "09B", "09A",
                        "08F", "08E", "08D", "08C", "08B", "08A", "07F", "07E", "07D", "07C", "07B", "07A", "06G2",
                        "06G1", "06D",
                        "06C", "06B", "06A", "05G2", "05G1", "05C", "05B", "05A"}),
                classes);
    }

    @Test
    public void testGetScheduleUrl() throws Exception {
        final JSONObject data = new JSONObject();
        data.put(UntisInfoParser.PARAM_BASEURL, "http://example.com");
        assertEquals("http://example.com/w/1/w00000.htm", UntisInfoParser.getScheduleUrl("1", 0, data));

        data.put(UntisInfoParser.PARAM_W_AFTER_NUMBER, true);
        assertEquals("http://example.com/1/w/w00000.htm", UntisInfoParser.getScheduleUrl("1", 0, data));
    }
}
