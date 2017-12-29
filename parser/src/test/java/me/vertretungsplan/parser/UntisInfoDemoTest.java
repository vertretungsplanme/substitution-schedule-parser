/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class UntisInfoDemoTest extends BaseDemoTest {
    private Document doc;
    private UntisInfoParser parser;
    private SubstitutionScheduleData scheduleData;

    @Before
    public void setUp() throws JSONException {
        doc = Jsoup.parse(readResource("/untis-info/schedule1.html"));
        scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject("{\"classesSeparated\": true }"));
        scheduleData.setType(SubstitutionSchedule.Type.STUDENT);
        parser = new UntisInfoParser(scheduleData, null);
    }

    @Test
    public void demoTest() throws IOException, JSONException, CredentialInvalidException {
        SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);
        parser.parseSubstitutionDays(schedule, "29.06.2017 12:10", doc, null, new ArrayList<String>());
        assertEquals(2, schedule.getDays().size());
        if (schedule.getDays().get(0).getSubstitutions().size() == 31) {
            assertEquals(24, schedule.getDays().get(1).getSubstitutions().size());
        } else {
            assertEquals(31, schedule.getDays().get(1).getSubstitutions().size());
            assertEquals(24, schedule.getDays().get(0).getSubstitutions().size());
        }
    }
}
