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
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class UntisMonitorParserDemoTest extends BaseDemoTest {

    @Test
    public void demoTest() throws IOException, JSONException, CredentialInvalidException {
        Document doc = Jsoup.parse(readResource("/untis-monitor/subst_001.htm"));
        final SubstitutionScheduleData data = new SubstitutionScheduleData();
        data.setData(new JSONObject("{" +
                "\"columns\": [\n" +
                "           \"lesson\",\n" +
                "           \"type\",\n" +
                "           \"class\",\n" +
                "           \"course\",\n" +
                "           \"previousTeacher\",\n" +
                "           \"teacher\",\n" +
                "           \"desc\",\n" +
                "           \"room\" \n" +
                "        ]}"));
        final UntisMonitorParser parser = new UntisMonitorParser(data, null);
        SubstitutionScheduleDay day = parser.parseMonitorDay(doc, data.getData());
        assertEquals(new LocalDate(2018, 1, 1), day.getDate());
        assertEquals(new LocalDateTime(2018, 1, 1, 8, 42), day.getLastChange());
        assertEquals(14, day.getSubstitutions().size());
        assertEquals(4, day.getMessages().size());
        assertEquals("<b>Pakaski:</b> Das ist ein Tagestext nur für Lehrer Pakaski", day.getMessages().get(3));
        System.out.println(day.toString(SubstitutionSchedule.Type.TEACHER));
    }
}
