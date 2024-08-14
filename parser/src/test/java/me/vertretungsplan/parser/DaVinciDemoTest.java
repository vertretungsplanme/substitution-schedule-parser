/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class DaVinciDemoTest extends BaseDemoTest {
    private static final String EXAMPLE_URL = "http://example.com";

    private String htmlSingle;
    private String htmlSingleDaVinci5;
    private String htmlDayIndex;
    private String htmlMonth;
    private String htmlClasses;

    private DaVinciParser parser;

    @Before
    public void setUp() throws JSONException {
        htmlSingle = readResource("/davinci/single.html");
        htmlSingleDaVinci5 = readResource("/davinci/single_5.html");
        htmlDayIndex = readResource("/davinci/dayIndex.html");
        htmlMonth = readResource("/davinci/month.html");
        htmlClasses = readResource("/davinci/classes.html");
        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject());
        parser = new DaVinciParser(scheduleData, null);
    }

    @Test
    public void singleTest6() throws IOException, JSONException {
        SubstitutionSchedule schedule = new SubstitutionSchedule();
        DaVinciParser.parsePage(Jsoup.parse(htmlSingle), schedule, parser.colorProvider);
        SubstitutionScheduleDay day = schedule.getDays().get(0);
        assertEquals(new LocalDate(2016, 9, 5), day.getDate());
        assertEquals(new LocalDateTime(2016, 9, 2, 13, 32), day.getLastChange());
        assertEquals(23, day.getSubstitutions().size());
        assertEquals(0, day.getMessages().size());

        checkSubstitutions(day);
    }

    @Test
    public void singleTest5() throws IOException, JSONException {
        SubstitutionSchedule schedule = new SubstitutionSchedule();
        DaVinciParser.parsePage(Jsoup.parse(htmlSingleDaVinci5), schedule, parser.colorProvider);
        assertEquals(3, schedule.getDays().size());
        assertEquals(new LocalDateTime(2017, 1, 17, 7, 32), schedule.getLastChange());

        SubstitutionScheduleDay day = schedule.getDays().get(0);
        assertEquals(new LocalDate(2017, 1, 17), day.getDate());
        assertEquals(2, day.getSubstitutions().size());
        assertEquals(0, day.getMessages().size());
        checkSubstitutions(day);
    }

    private void checkSubstitutions(SubstitutionScheduleDay day) {
        for (Substitution subst : day.getSubstitutions()) {
            assertFalse(subst.getClasses().isEmpty());
            assertNotEmpty(subst.getLesson());
            assertNotEmpty(subst.getPreviousSubject());
            assertNullOrNotEmpty(subst.getSubject());
            assertNullOrNotEmpty(subst.getRoom());
            assertNullOrNotEmpty(subst.getTeacher());
            assertNullOrNotEmpty(subst.getDesc());
            assertNotEmpty(subst.getType());
        }
    }

    @Test
    public void testSingle() throws IOException {
        List<String> urls = DaVinciParser.getDayUrls(EXAMPLE_URL, Jsoup.parse(htmlSingle));
        assertEquals(1, urls.size());
        assertEquals(EXAMPLE_URL, urls.get(0));
    }

    @Test
    public void testDayIndex() throws IOException {
        List<String> urls = DaVinciParser.getDayUrls(EXAMPLE_URL, Jsoup.parse(htmlDayIndex));
        assertEquals(7, urls.size());
        for (int i = 0; i < urls.size(); i++) {
            assertEquals(EXAMPLE_URL + "/V_DC_00" + (i + 1) + ".html", urls.get(i));
        }
    }

    @Test
    public void testMonth() throws IOException {
        List<String> urls = DaVinciParser.getDayUrls(EXAMPLE_URL, Jsoup.parse(htmlMonth));
        assertEquals(1, urls.size());
        assertEquals(EXAMPLE_URL + "/V_DC_001.html", urls.get(0));
    }

    @Test
    public void testClasses() throws IOException {
        List<String> urls = DaVinciParser.getDayUrls(EXAMPLE_URL, Jsoup.parse(htmlClasses));
        assertEquals(5, urls.size());
        assertEquals(EXAMPLE_URL + "/V_CL_58575B54-F93E-4A92-B10A-B3BF19A08F69.html", urls.get(0));
        assertEquals(EXAMPLE_URL + "/V_CL_261DEB89-5010-44D1-90C3-1EE56337146E.html", urls.get(1));
        assertEquals(EXAMPLE_URL + "/V_CL_09331E7C-4830-4B3F-B1F3-9F76A5735685.html", urls.get(2));
        assertEquals(EXAMPLE_URL + "/V_CL_96110190-1050-41A6-AD43-C5F97CC664E9.html", urls.get(3));
        assertEquals(EXAMPLE_URL + "/V_CL_FAA7DDCA-574B-4A1F-BEA8-A13CF1BC2C7C.html", urls.get(4));
    }
}
