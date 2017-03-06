/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package me.vertretungsplan.additionalinfo;

import me.vertretungsplan.additionalinfo.amgrottweil.AmgRottweilMessagesParser;
import me.vertretungsplan.additionalinfo.amgrottweil.AmgRottweilRSSParser;
import me.vertretungsplan.objects.AdditionalInfo;
import org.apache.http.client.fluent.Request;

import java.io.IOException;

/**
 * Parser that creates {@link AdditionalInfo}s.
 */
public abstract class BaseAdditionalInfoParser {
	protected BaseAdditionalInfoParser() {
	}

	/**
	 * Create an additional info parser. Uses the supplied {@code type} string to create an appropriate subclass.
	 *
	 * @param type the type of additional info
	 * @return A {@link BaseAdditionalInfoParser} subclass
	 */
	public static BaseAdditionalInfoParser getInstance(String type) {
		BaseAdditionalInfoParser parser = null;
		if (type.equals("winter-sh")) {
			parser = new WinterShParser();
		} else if (type.equals("amgrottweil-rss")) {
			parser = new AmgRottweilRSSParser();
		} else if (type.equals("amgrottweil-messages")) {
			parser = new AmgRottweilMessagesParser();
		}
		return parser;
	}

	public abstract AdditionalInfo getAdditionalInfo() throws IOException;

	@SuppressWarnings("SameParameterValue")
	protected String httpGet(String url, String encoding) throws IOException {
		return new String(Request.Get(url).execute().returnContent().asBytes(), encoding);
	}
}
