/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IndiwareDemoTest extends BaseDemoTest {
    private IndiwareParser parser;
    private String xml;
    private String html;

    @Before
    public void setUp() throws JSONException {
        xml = readResource("/indiware/indiware.xml");
        html = readResource("/indiware/indiware.html");
        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject());
        parser = new IndiwareParser(scheduleData, null);
    }

    @Test
    public void demoTestXML() {
        SubstitutionScheduleDay schedule = parser.parseIndiwareDay(Jsoup.parse(xml, "", Parser.xmlParser()), false);
        verify(schedule);
    }

    @Test
    public void demoTestHTML() {
        SubstitutionScheduleDay schedule = parser.parseIndiwareDay(Jsoup.parse(html), true);
        verify(schedule);
    }

    @Test
    public void testEquals() {
        SubstitutionScheduleDay scheduleXML = parser.parseIndiwareDay(Jsoup.parse(xml, "", Parser.xmlParser()),
                false);
        SubstitutionScheduleDay scheduleHTML = parser.parseIndiwareDay(Jsoup.parse(html), true);
        assertEquals(scheduleXML, scheduleHTML);
    }

    private void verify(SubstitutionScheduleDay schedule) {
        assertEquals(new LocalDate(2016, 8, 22), schedule.getDate());
        assertEquals(new LocalDateTime(2016, 8, 19, 12, 50), schedule.getLastChange());
        assertEquals(2, schedule.getMessages().size());
        assertEquals("<b>Klassen mit Änderung:</b> bla", schedule.getMessages().get(0));
        assertEquals("Erste Zeile.\nZweite Zeile", schedule.getMessages().get(1));
        assertEquals(1, schedule.getSubstitutions().size());
        Substitution subst = schedule.getSubstitutions().iterator().next();
        assertEquals(2, subst.getClasses().size());
        assertEquals("3", subst.getLesson());
        assertEquals("Bio", subst.getSubject());
        assertEquals("Sch", subst.getTeacher());
        assertEquals("1234", subst.getRoom());
        assertEquals("Mat", subst.getPreviousSubject());
        assertEquals("Mül", subst.getPreviousTeacher());
        assertEquals(null, subst.getDesc());
        assertEquals("Vertretung", subst.getType());
    }
}
