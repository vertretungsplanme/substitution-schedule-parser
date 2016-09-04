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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SVPlanDemoTest extends BaseDemoTest {
    private String html;

    private SVPlanParser parser;

    @Before
    public void setUp() throws JSONException {
        html = readResource("/svplan/svplan.html");
        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject());
        parser = new SVPlanParser(scheduleData, null);
    }

    @Test
    public void demoTest() throws IOException, JSONException {
        List<Document> docs = new ArrayList<>();
        docs.add(Jsoup.parse(html));
        SubstitutionSchedule schedule = parser.parseSVPlanSchedule(docs);

        assertEquals(new LocalDateTime(2016, 9, 2, 11, 10), schedule.getLastChange());
        assertEquals(5, schedule.getDays().size());

        SubstitutionScheduleDay day = schedule.getDays().get(0);

        assertEquals(new LocalDate(2016, 9, 2), day.getDate());
        assertEquals(14, day.getSubstitutions().size());
        assertEquals(1, day.getMessages().size());
        assertEquals("Ordnungsdienst:9BR<br>", day.getMessages().get(0));

        for (Substitution subst : day.getSubstitutions()) {
            assertTrue(subst.getClasses().size() == 1);
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
}
