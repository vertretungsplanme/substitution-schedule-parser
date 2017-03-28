/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.objects.Substitution;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UntisCommonParserTest {
    @Test
    public void testHandleRoomSingle() throws Exception {
        Substitution subst = new Substitution();
        Element cell = Jsoup.parse("<table><td>224</td></table>")
                .select("td").first();

        UntisCommonParser.handleRoom(subst, cell);
        assertEquals(null, subst.getPreviousRoom());
        assertEquals("224", subst.getRoom());
    }

    @Test
    public void testHandleRoomSinglePrevious() throws Exception {
        Substitution subst = new Substitution();
        Element cell = Jsoup.parse("<table><td><s>264</s></td></table>")
                .select("td").first();

        UntisCommonParser.handleRoom(subst, cell);
        assertEquals("264", subst.getPreviousRoom());
        assertEquals(null, subst.getRoom());
    }

    @Test
    public void testHandleRoomBoth() throws Exception {
        Substitution subst = new Substitution();
        Element cell = Jsoup.parse("<table><td><s>248</s>?236</td></table>")
                .select("td").first();

        UntisCommonParser.handleRoom(subst, cell);
        assertEquals("248", subst.getPreviousRoom());
        assertEquals("236", subst.getRoom());
    }

    @Test
    public void testHandleRoomBothSpan() throws Exception {
        Substitution subst = new Substitution();
        Element cell = Jsoup.parse("<table><td><span style=\"color: #010101\"><s>248</s>?236</span></td></table>")
                .select("td").first();

        UntisCommonParser.handleRoom(subst, cell);
        assertEquals("248", subst.getPreviousRoom());
        assertEquals("236", subst.getRoom());
    }
}
