/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo;

import me.vertretungsplan.objects.AdditionalInfo;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.IOException;

public abstract class BaseRSSFeedParser extends BaseAdditionalInfoParser {

    private static final int MAX_LENGTH = 100;
    private static final int MAX_ITEMS_COUNT = 5;

    protected abstract String getRSSUrl();

    protected String getTitle() {
        return "Neuigkeiten";
    }

    @Override public AdditionalInfo getAdditionalInfo() throws IOException {
        String xml = httpGet(getRSSUrl(), "UTF-8");
        return parse(xml);
    }

    @NotNull private AdditionalInfo parse(String xml) {
        AdditionalInfo info = new AdditionalInfo();
        info.setTitle(getTitle());
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());

        StringBuilder content = new StringBuilder();

        int count = 0;
        for (Element item : doc.select("item")) {
            if (count >= MAX_ITEMS_COUNT) {
                break;
            } else if (count != 0) {
                content.append("<br><br>");
            }

            content.append("<b><a href=\"");
            content.append(item.select("link").text());
            content.append("\">");
            content.append(item.select("title").text());
            content.append("</a></b><br>");

            final String text = Jsoup.parse(item.select("description").text()).text();
            final String truncatedText = text.substring(0, Math.min(text.length(), MAX_LENGTH));
            content.append(truncatedText);
            if (truncatedText.length() < text.length()) content.append("…");

            count++;
        }

        info.setText(content.toString());
        return info;
    }
}
