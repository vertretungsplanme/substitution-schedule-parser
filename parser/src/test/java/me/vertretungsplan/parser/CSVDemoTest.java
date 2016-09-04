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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class CSVDemoTest extends BaseDemoTest {
    private String csv;
    private CSVParser parser;

    @Before
    public void setUp() throws JSONException {
        csv = readResource("/csv/csv.csv");
        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        JSONObject data = new JSONObject();
        data.put("skipLines", 1);
        data.put("separator", "\\|");
        JSONArray columns = new JSONArray();
        columns.put("ignore").put("day").put("stand").put("class").put("lesson").put("subject").put("teacher").put
                ("room").put("desc-type");
        data.put("columns", columns);
        data.put("classes", new JSONArray());
        scheduleData.setData(data);
        parser = new CSVParser(scheduleData, null);
    }

    @Test
    public void demoTest() throws IOException, JSONException {
        SubstitutionSchedule schedule = parser.parseCSV(csv);
        assertEquals(2, schedule.getDays().size());

        SubstitutionScheduleDay day = schedule.getDays().get(0);
        assertEquals(new LocalDate(2016, 9, 5), day.getDate());
        assertEquals(new LocalDateTime(2016, 9, 2, 8, 16), day.getLastChange());
        assertEquals(1, day.getSubstitutions().size());
        assertEquals(0, day.getMessages().size());

        Substitution subst = day.getSubstitutions().iterator().next();
        assertEquals(1, subst.getClasses().size());
        assertEquals("05A", subst.getClasses().iterator().next());
        assertEquals("6", subst.getLesson());
        assertEquals("!Ma", subst.getSubject());
        assertEquals("!DROE", subst.getTeacher());
        assertEquals("!B203", subst.getRoom());
        assertEquals("für Mu REN", subst.getDesc());
        assertEquals("Vertretung", subst.getType());
    }
}
