/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ColumnTypeDetectorTest {
    private ColumnTypeDetector detector;

    @Before
    public void setUp() throws IOException, JSONException {
        detector = new ColumnTypeDetector();
    }

    @Test
    public void testClass() {
        assertEquals("class", detector.getColumnType("Klasse", Collections.<String>emptyList()));
        assertEquals("class", detector.getColumnType("Klasse", Arrays.asList("Klasse", "Kurs", "Fach")));
        assertEquals("ignore", detector.getColumnType("Kurs", Arrays.asList("Klasse", "Kurs", "Fach")));
        assertEquals("class", detector.getColumnType("Kurs", Arrays.asList("Kurs", "Fach", "Raum")));
    }
}
