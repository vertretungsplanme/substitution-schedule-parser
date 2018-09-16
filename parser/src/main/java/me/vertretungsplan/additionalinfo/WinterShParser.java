/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package me.vertretungsplan.additionalinfo;

import me.vertretungsplan.objects.AdditionalInfo;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.IOException;

/**
 * Parser information about cancellation of classes caused by snow or other extreme weather conditions provided as an
 * RSS feed by the Ministry of Education of Schleswig-Holstein, Germany.
 * Same as http://www.schleswig-holstein.de/DE/Landesregierung/III/Service/winterhotline/Winterhotline.html
 * Can be used for all public and vocational schools in Schleswig-Holstein.
 */
public class WinterShParser extends BaseAdditionalInfoParser {

    private static final String URL = "https://phpefi.schleswig-holstein.de/lage/newsticker/feed.php?projekt=1";
    private static final String ENCODING = "UTF-8";
	private static final String TITLE = "Witterungsbedingter Unterrichtsausfall";
	
	@Override
	public AdditionalInfo getAdditionalInfo() throws IOException {
		String xml = httpGet(URL, ENCODING);
		return handleXML(xml);
	}

	@NotNull static AdditionalInfo handleXML(String xml) {
		AdditionalInfo info = new AdditionalInfo();
		info.setTitle(TITLE);
		Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
		doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
		String text = doc.select("item description").first().html().replace("\r\n", "<br>").trim();
		if (text.startsWith("Zurzeit gibt es keine Hinweise auf witterungsbedingten Unterrichtsausfall.")
				|| text.startsWith("Aktuell gibt es keine Hinweise auf witterungsbedingten Unterrichtsausfall.")) {
			info.setHasInformation(false);
			info.setText("Aktuell gibt es keine Hinweise auf witterungsbedingten Unterrichtsausfall.");
		}
		if (text.endsWith("<br>")) {
			text = text.substring(0, text.length() - 4);
		}
		info.setTitle(TITLE + " (Stand: " + doc.select("pubDate").first().text() + ")");
		info.setText(text);

		return info;
	}

}
