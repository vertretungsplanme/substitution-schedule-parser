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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SVPlanTest {
    private SVPlanParser parser;

    @Before
    public void setUp() throws JSONException {
        SubstitutionScheduleData data = new SubstitutionScheduleData();
        final JSONObject json = new JSONObject();
        final JSONArray classes = new JSONArray();
        classes.put("7a");
        classes.put("8a");
        classes.put("8b");
        classes.put("9a");
        classes.put("10a");
        json.put("classes", classes);
        data.setData(json);
        data.setType(SubstitutionSchedule.Type.STUDENT);
        parser = new SVPlanParser(data, null);
    }

    @Test
    public void testGetClassesSingle() throws JSONException {
        List<String> classes = parser.getClasses("7a");
        assertEquals(1, classes.size());
        assertEquals("7a", classes.get(0));
    }

    @Test
    public void testGetClassesYear() throws JSONException {
        List<String> classes = parser.getClasses("[8]");
        assertEquals(2, classes.size());
        assertEquals("8a", classes.get(0));
        assertEquals("8b", classes.get(1));
    }

    @Test
    public void testGetClassesYearRange() throws JSONException {
        List<String> classes = parser.getClasses("[7-9]");
        assertEquals(4, classes.size());
        assertEquals("7a", classes.get(0));
        assertEquals("8a", classes.get(1));
        assertEquals("8b", classes.get(2));
        assertEquals("9a", classes.get(3));
    }
}
