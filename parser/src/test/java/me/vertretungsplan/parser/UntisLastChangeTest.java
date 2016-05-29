/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class UntisLastChangeTest {

    private Document doc;
    private String date;

    public UntisLastChangeTest(Document doc, String description, String date) {
        this.doc = doc;
        this.date = date;
    }

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        Document monHead = Jsoup.parse(wrapHtml(
                "<table class=\"mon_head\">\n" +
                        "    <tr>\n" +
                        "        <td valign=\"bottom\"><h1><strong>Untis</strong> 2015 </h1></td>\n" +
                        "        <td valign=\"bottom\"></td>\n" +
                        "        <td align=\"right\" valign=\"bottom\">\n" +
                        "            <p>Beispielschule <span style=\"width:10px\">&nbsp;</span> D-12345 Musterstadt<br />\n" +
                        "            Stundenplan 2015/16<span style=\"width:10px\">&nbsp;</span> <span style=\"width:10px\">&nbsp;</span> Stand: 30.10.2015 11:25</p>\n" +
                        "        </td>\n" +
                        "    </tr>\n" +
                        "</table>"));
        Document monHeadInComment = Jsoup.parse(wrapHtml(
                "<!--<table class=\"mon_head\">\n" +
                        "    <tr>\n" +
                        "        <td valign=\"bottom\"><h1><strong>Untis</strong> 2015 </h1></td>\n" +
                        "        <td valign=\"bottom\"></td>\n" +
                        "        <td align=\"right\" valign=\"bottom\">\n" +
                        "            <p>Beispielschule <span style=\"width:10px\">&nbsp;</span> D-12345 Musterstadt<br />\n" +
                        "            Stundenplan 2015/16<span style=\"width:10px\">&nbsp;</span> <span style=\"width:10px\">&nbsp;</span> Stand: 30.10.2015 11:25</p>\n" +
                        "        </td>\n" +
                        "    </tr>\n" +
                        "</table>-->"));
        return Arrays.asList(new Object[][]{
                {monHead, "mon_head table", "30.10.2015 11:25"},
                {monHeadInComment, "mon_head table in comment", "30.10.2015 11:25"}
        });
    }

    private static String wrapHtml(String html) {
        return "<html>" + "<body>" + html + "</body>" + "</html>";
    }

    @Test
    public void testFindLastChange() throws Exception {
        assertEquals(UntisCommonParser.findLastChange(doc, null), date);
    }
}