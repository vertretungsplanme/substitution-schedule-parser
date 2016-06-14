/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package me.vertretungsplan.additionalinfo;

import me.vertretungsplan.objects.AdditionalInfo;
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

	private static final String URL = "http://phpservice.transferservice.dataport.de/newsticker/feed.php?projekt=1";
	private static final String ENCODING = "ISO-8859-1";
	private static final String TITLE = "Witterungsbedingter Unterrichtsausfall";
	
	@Override
	public AdditionalInfo getAdditionalInfo() throws IOException {
		AdditionalInfo info = new AdditionalInfo();
		info.setTitle(TITLE);

		String xml = httpGet(URL, ENCODING);
		Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
		String text = doc.select("item description").first().text();
		if (text.equals("Zurzeit gibt es keine Hinweise auf witterungsbedingten Unterrichtsausfall.")) {
			info.setHasInformation(false);
		}
		info.setTitle(TITLE + " (Stand: " + doc.select("pubDate").first().text() + ")");
		info.setText(text);

		return info;
	}

}
