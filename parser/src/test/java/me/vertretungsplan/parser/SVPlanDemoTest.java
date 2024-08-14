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
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SVPlanDemoTest extends BaseDemoTest {
    private String html1;
    private String html2;
    private String html3;
    private String html4;
    private String html5;

    private SVPlanParser parser;
    private SVPlanParser parserWithoutRepeat;

    @Before
    public void setUp() throws JSONException {
        html1 = readResource("/svplan/svplan1.html");
        html2 = readResource("/svplan/svplan2.html");
        html3 = readResource("/svplan/svplan3.html");
        html4 = readResource("/svplan/svplan4.html");
        html5 = readResource("/svplan/svplan5.html");
        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject());

        SubstitutionScheduleData scheduleData2 = new SubstitutionScheduleData();
        scheduleData2.setData(new JSONObject("{\"repeatClass\": false}"));

        parser = new SVPlanParser(scheduleData, null);
        parserWithoutRepeat = new SVPlanParser(scheduleData2, null);
    }

    @Test
    public void demoTest1() throws IOException, JSONException {
        List<Document> docs = new ArrayList<>();
        docs.add(Jsoup.parse(html1));
        SubstitutionSchedule schedule = parser.parseSVPlanSchedule(docs);

        assertEquals(new LocalDateTime(2016, 9, 2, 11, 10), schedule.getLastChange());
        assertEquals(5, schedule.getDays().size());

        SubstitutionScheduleDay day = schedule.getDays().get(0);

        assertEquals(new LocalDate(2016, 9, 2), day.getDate());
        assertEquals(16, day.getSubstitutions().size());
        assertEquals(1, day.getMessages().size());
        assertEquals("Ordnungsdienst:9BR<br>", day.getMessages().get(0));

        for (Substitution subst : day.getSubstitutions()) {
            assertNotEmpty(subst.getLesson());
            assertNullOrNotEmpty(subst.getPreviousSubject());
            assertNullOrNotEmpty(subst.getRoom());
            assertNullOrNotEmpty(subst.getTeacher());
            assertNullOrNotEmpty(subst.getPreviousTeacher());
            assertNullOrNotEmpty(subst.getDesc());
            assertNotEmpty(subst.getType());
            if (!subst.getType().equals("Pausenaufsicht")) {
                assertEquals(1, subst.getClasses().size());
                assertNotEmpty(subst.getSubject());
            }
        }
    }

    @Test
    public void demoTest2() throws IOException, JSONException {
        List<Document> docs = new ArrayList<>();
        docs.add(Jsoup.parse(html2));
        SubstitutionSchedule schedule = parser.parseSVPlanSchedule(docs);

        assertEquals(new LocalDateTime(2016, 11, 6, 19, 36, 18), schedule.getLastChange());
        assertEquals(1, schedule.getDays().size());

        SubstitutionScheduleDay day = schedule.getDays().get(0);

        assertEquals(new LocalDate(2016, 11, 7), day.getDate());
        assertEquals(19, day.getSubstitutions().size());
        assertEquals(1, day.getMessages().size());
        assertEquals("Sprechtag Frau Fildebrandt (Klasse 9).", day.getMessages().get(0));

        for (Substitution subst : day.getSubstitutions()) {
            assertEquals(1, subst.getClasses().size());
            assertNotEmpty(subst.getLesson());
            assertNullOrNotEmpty(subst.getPreviousSubject());
            assertNotEmpty(subst.getSubject());
            assertNullOrNotEmpty(subst.getRoom());
            assertNullOrNotEmpty(subst.getTeacher());
            assertNullOrNotEmpty(subst.getPreviousTeacher());
            assertNullOrNotEmpty(subst.getDesc());
            assertNotEmpty(subst.getType());
        }
    }

    @Test
    public void demoTest3() throws IOException, JSONException {
        List<Document> docs = new ArrayList<>();
        docs.add(Jsoup.parse(html3));
        SubstitutionSchedule schedule = parser.parseSVPlanSchedule(docs);

        assertEquals(new LocalDateTime(2017, 5, 2, 7, 19), schedule.getLastChange());
        assertEquals(1, schedule.getDays().size());

        SubstitutionScheduleDay day = schedule.getDays().get(0);

        assertEquals(new LocalDate(2017, 5, 2), day.getDate());
        assertEquals(32, day.getSubstitutions().size());
        assertEquals(0, day.getMessages().size());

        for (Substitution subst : day.getSubstitutions()) {
            if (!subst.getSubject().equals("Profi")) assertFalse(!subst.getClasses().isEmpty());
            assertNotEmpty(subst.getLesson());
            assertNullOrNotEmpty(subst.getPreviousSubject());
            assertNotEmpty(subst.getSubject());
            assertNullOrNotEmpty(subst.getRoom());
            assertNullOrNotEmpty(subst.getTeacher());
            assertNullOrNotEmpty(subst.getPreviousTeacher());
            assertNullOrNotEmpty(subst.getDesc());
            assertNotEmpty(subst.getType());
        }
    }

    @Test
    public void demoTest4() throws IOException, JSONException {
        List<Document> docs = new ArrayList<>();
        docs.add(Jsoup.parse(html4));
        SubstitutionSchedule schedule = parser.parseSVPlanSchedule(docs);

        for (Substitution subst : schedule.getDays().get(0).getSubstitutions()) {
            assertEquals(1, subst.getClasses().size());
        }

        SubstitutionSchedule schedule2 = parserWithoutRepeat.parseSVPlanSchedule(docs);

        for (Substitution subst : schedule2.getDays().get(0).getSubstitutions()) {
            if (subst.getSubject().equals("Netzw")) {
                assertTrue(subst.getClasses().isEmpty());
            } else {
                assertEquals(1, subst.getClasses().size());
            }
        }
    }

    @Test
    public void demoTest5() throws IOException, JSONException {
        List<Document> docs = new ArrayList<>();
        docs.add(Jsoup.parse(html5));
        SubstitutionSchedule schedule = parser.parseSVPlanSchedule(docs);

        assertEquals(new LocalDate(2019, 3, 28), schedule.getDays().get(0).getDate());
        assertEquals(new LocalDate(2019, 3, 29), schedule.getDays().get(1).getDate());
    }
}
