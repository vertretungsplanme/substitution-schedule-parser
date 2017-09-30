/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class IphisDemoTest extends BaseDemoTest {
    private IphisParser parser;
    private JSONArray changes;
    private JSONArray teachers;
    private JSONArray courses;
    private JSONArray messages;

    @Before
    public void setUp() throws JSONException {
        changes = new JSONArray(readResource("/iphis/changes.json"));
        teachers = new JSONArray(readResource("/iphis/teachers.json"));
        courses = new JSONArray(readResource("/iphis/grades.json"));
        messages = new JSONArray(readResource("/iphis/messages.json"));
        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject());
        parser = new IphisParser(scheduleData, null);
        DateTimeUtils.setCurrentMillisFixed(new LocalDate(2017, 9, 29).toDateTimeAtStartOfDay().getMillis());
    }

    @Test
    public void demoTest() throws IOException, JSONException {
        SubstitutionSchedule schedule = new SubstitutionSchedule();
        parser.parseIphis(schedule, changes, courses, teachers, messages);
        assertEquals(2, schedule.getDays().size());
        SubstitutionScheduleDay firstDay = schedule.getDays().get(0);
        assertEquals(new LocalDate(2017, 9, 29), firstDay.getDate());
        assertEquals(4, firstDay.getSubstitutions().size());
    }

    @After
    public void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }
}
