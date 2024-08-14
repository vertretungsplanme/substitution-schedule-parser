/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IndiwareMobileDemoTest extends BaseDemoTest {

    @Test
    public void demoTest() {
        Document doc = Jsoup.parse(readResource("/indiware-mobile/indiware-mobile.xml"), "", Parser.xmlParser());
        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setType(SubstitutionSchedule.Type.STUDENT);
        SubstitutionScheduleDay day = IndiwareMobileParser.parseDay(doc, new ColorProvider(), scheduleData);

        assertEquals(new LocalDate(2017, 6, 21), day.getDate());
        assertEquals(new LocalDateTime(2017, 6, 20, 10, 28), day.getLastChange());
        assertEquals(192, day.getSubstitutions().size());
    }
}
