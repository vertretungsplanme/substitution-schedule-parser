/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.objects.SubstitutionScheduleData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColorProviderTest {

    @Test
    public void testPreconfigured() {
        final SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject());
        final ColorProvider provider = new ColorProvider(scheduleData);

        assertEquals("#2196F3", provider.getColor("Vertretung"));
    }

    @Test
    public void testCustomName() throws JSONException {
        final SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        final JSONObject data = new JSONObject();
        final JSONObject colors = new JSONObject();
        final JSONArray values = new JSONArray();
        values.put("Vertretung");
        colors.put("red", values);
        data.put("colors", colors);
        scheduleData.setData(data);
        final ColorProvider provider = new ColorProvider(scheduleData);

        assertEquals("#F44336", provider.getColor("Vertretung"));
    }

    @Test
    public void testCustomColor() throws JSONException {
        final SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        final JSONObject data = new JSONObject();
        final JSONObject colors = new JSONObject();
        final JSONArray values = new JSONArray();
        values.put("Vertretung");
        colors.put("#123456", values);
        data.put("colors", colors);
        scheduleData.setData(data);
        final ColorProvider provider = new ColorProvider(scheduleData);

        assertEquals("#123456", provider.getColor("Vertretung"));
    }
}
