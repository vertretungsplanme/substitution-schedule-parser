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

public class AmgRottweilMessagesParser extends BaseAdditionalInfoParser {

    static final String TITLE = "Nachrichten für Schüler";

    @Override public AdditionalInfo getAdditionalInfo() throws IOException {
        String html = httpGet("https://www.amgrw.de/AMGaktuell/NachrichtenSchueler.php", "UTF-8");
        return parse(html);
    }

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
        info.setTitle(TITLE);
        info.setText(text.toString());
        return info;
    }
}
