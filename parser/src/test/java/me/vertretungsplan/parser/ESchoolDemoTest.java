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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ESchoolDemoTest extends BaseDemoTest {
    private String html;

    private ESchoolParser parser;

    @Before
    public void setUp() throws JSONException {
        html = readResource("/eschool/eschool.html");
        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject());
        parser = new ESchoolParser(scheduleData, null);
    }

    @Test
    public void demoTest() throws IOException, JSONException {
        SubstitutionSchedule schedule = parser.parseESchoolSchedule(Jsoup.parse(html));
        assertEquals(new LocalDateTime(2016, 9, 4, 14, 53), schedule.getLastChange());
        assertEquals(1, schedule.getDays().size());

        SubstitutionScheduleDay day = schedule.getDays().get(0);

        assertEquals(new LocalDate(2016, 9, 5), day.getDate());
        assertEquals(6, day.getSubstitutions().size());
        assertEquals(0, day.getMessages().size());

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
