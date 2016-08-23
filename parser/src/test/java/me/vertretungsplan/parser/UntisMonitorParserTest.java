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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UntisMonitorParserTest {
    @Test
    public void testFindSubDocsSingle() {
        String html = "<html>blabla</html>";
        Document doc = Jsoup.parse(html);
        List<Document> docs = new ArrayList<>();
        UntisMonitorParser.findSubDocs(docs, html, doc);

        assertEquals(docs.size(), 1);
        assertEquals(docs.get(0), doc);
    }

    @Test
    public void testFindSubDocsMultiple() {
        String html = "<html>1</html><html>2</html>";
        Document doc = Jsoup.parse(html);
        List<Document> docs = new ArrayList<>();
        UntisMonitorParser.findSubDocs(docs, html, doc);

        assertEquals(docs.size(), 2);
        assertEquals(docs.get(0).text(), "1");
        assertEquals(docs.get(1).text(), "2");
    }
}
