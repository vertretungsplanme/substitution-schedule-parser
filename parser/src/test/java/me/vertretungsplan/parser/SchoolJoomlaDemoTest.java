/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2018 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import org.joda.time.LocalDateTime;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class SchoolJoomlaDemoTest extends BaseDemoTest {
    private String jsonAllData;
    private SchoolJoomlaParser parser;

    @Before
    public void setUp() throws JSONException {
        jsonAllData = readResource("/schooljoomla/allData.json");

        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject("{\"baseurl\":\"http://test\"}"));
        scheduleData.setType(SubstitutionSchedule.Type.STUDENT);

        parser = new SchoolJoomlaParser(scheduleData, null);
    }

    @Test
    public void demoTest() throws IOException, JSONException, CredentialInvalidException {
        SubstitutionSchedule schedule = parser.parse(new JSONObject(jsonAllData));
        assertEquals(new LocalDateTime("2018-08-30T14:00:53.000"), schedule.getLastChange());
        assertEquals(2, schedule.getDays().size());
    }
}
