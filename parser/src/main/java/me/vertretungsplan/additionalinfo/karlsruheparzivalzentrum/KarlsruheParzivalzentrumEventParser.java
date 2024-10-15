/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo.karlsruheparzivalzentrum;

import me.vertretungsplan.additionalinfo.BaseAdditionalInfoParser;
import me.vertretungsplan.objects.AdditionalInfo;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;

public class KarlsruheParzivalzentrumEventParser extends BaseAdditionalInfoParser {

    private static final String URL = "https://www.parzival-zentrum.de/termine/";
    private static final String ENCODING = "UTF-8";
	private static final String TITLE = "Termine";
	
	@Override
	public AdditionalInfo getAdditionalInfo() throws IOException {
		String html = httpGet(URL, ENCODING);
		return handleHTML(html);
	}

	@NotNull static AdditionalInfo handleHTML(String html) {
		AdditionalInfo info = new AdditionalInfo();
		info.setTitle(TITLE);
		String text = "";
		Document doc = Jsoup.parse(html, "", Parser.htmlParser());
		Element eventshtml = doc.select(".eo-events-shortcode").first();
		for (Element event : eventshtml.select(".eo-event-future")) {
			text = text + event + "<br>";
		}
		info.setText(text);

		return info;
	}

}
