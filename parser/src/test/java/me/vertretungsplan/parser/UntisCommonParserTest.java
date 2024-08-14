/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.Substitution;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UntisCommonParserTest {
    @Test
    public void testHandleRoomSingle() {
        Substitution subst = new Substitution();
        Element cell = Jsoup.parse("<table><td>224</td></table>")
                .select("td").first();

        UntisCommonParser.handleRoom(subst, cell, false);
        assertNull(subst.getPreviousRoom());
        assertEquals("224", subst.getRoom());
    }

    @Test
    public void testHandleRoomSinglePrevious() {
        Substitution subst = new Substitution();
        Element cell = Jsoup.parse("<table><td><s>264</s></td></table>")
                .select("td").first();

        UntisCommonParser.handleRoom(subst, cell, false);
        assertEquals("264", subst.getPreviousRoom());
        assertNull(subst.getRoom());
    }

    @Test
    public void testHandleRoomBoth() {
        Substitution subst = new Substitution();
        Element cell = Jsoup.parse("<table><td><s>248</s>?236</td></table>")
                .select("td").first();

        UntisCommonParser.handleRoom(subst, cell, false);
        assertEquals("248", subst.getPreviousRoom());
        assertEquals("236", subst.getRoom());
    }

    @Test
    public void testHandleRoomBothSpan() {
        Substitution subst = new Substitution();
        Element cell = Jsoup.parse("<table><td><span style=\"color: #010101\"><s>248</s>?236</span></td></table>")
                .select("td").first();

        UntisCommonParser.handleRoom(subst, cell, false);
        assertEquals("248", subst.getPreviousRoom());
        assertEquals("236", subst.getRoom());
    }

    @Test
    public void testHandleClasses() throws JSONException {
        JSONObject data = new JSONObject("{\"classesSeparated\": false}");

        Substitution subst = new Substitution();
        UntisCommonParser.handleClasses(data, subst, "11a", Arrays.asList("11a", "11b", "12a"));
        assertEquals(new HashSet<>(Collections.singletonList("11a")), subst.getClasses());

        subst = new Substitution();
        UntisCommonParser.handleClasses(data, subst, "1112", Arrays.asList("11", "12"));
        assertEquals(new HashSet<>(Arrays.asList("11", "12")), subst.getClasses());

        subst = new Substitution();
        UntisCommonParser.handleClasses(data, subst, "11abc12b", Arrays.asList("11a", "11b", "11c", "12a", "12b",
                "12c"));
        assertEquals(new HashSet<>(Arrays.asList("11a", "11b", "11c", "12b")), subst.getClasses());

        subst = new Substitution();
        UntisCommonParser.handleClasses(data, subst, "1abc2b", Arrays.asList("1a", "1b", "1c", "2a", "2b", "2c"));
        assertEquals(new HashSet<>(Arrays.asList("1a", "1b", "1c", "2b")), subst.getClasses());

        subst = new Substitution();
        UntisCommonParser.handleClasses(data, subst, "5-6", Arrays.asList("5a", "5b", "5c", "6a", "6b", "6c", "7a"));
        assertEquals(new HashSet<>(Arrays.asList("5a", "5b", "5c", "6a", "6b", "6c")), subst.getClasses());
    }
}
