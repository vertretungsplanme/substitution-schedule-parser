/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo.amgrottweil;

import me.vertretungsplan.additionalinfo.BaseAdditionalInfoParser;
import me.vertretungsplan.objects.AdditionalInfo;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

public abstract class AmgRottweilMessagesParser extends BaseAdditionalInfoParser {

    @Override public AdditionalInfo getAdditionalInfo() throws IOException {
        String html = httpGet(getUrl(), "UTF-8");
        return parse(html);
    }

    @NotNull protected abstract String getUrl();

    @NotNull protected abstract String getTitle();

    @NotNull AdditionalInfo parse(String html) {
        Document doc = Jsoup.parse(html);
        Elements messages = doc.select("tr td:eq(1)");

        StringBuilder text = new StringBuilder();
        boolean first = true;
        for (Element message : messages) {
            if (first) {
                first = false;
            } else {
                text.append("<br><br>");
            }
            text.append(message.text());
        }

        AdditionalInfo info = new AdditionalInfo();
        info.setHasInformation(false);
        info.setTitle(getTitle());
        info.setText(text.toString());
        return info;
    }
}
