/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package me.vertretungsplan.additionalinfo;

import me.vertretungsplan.objects.AdditionalInfo;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import org.apache.http.client.fluent.Request;

import java.io.IOException;

/**
 * Parser that creates {@link AdditionalInfo}s.
 */
public abstract class BaseAdditionalInfoParser {
	public BaseAdditionalInfoParser() {
	}

	/**
	 * Create an additional info parser. Uses the supplied {@code type} string to create an appropriate subclass.
	 *
	 * @param type die Art der Zusatzinformation (ein Element von <code>Schule.getAdditionalInfos()</code>)
	 * @return Eine Unterklasse von {@link BaseAdditionalInfoParser}, die zum übergebenen Typ passt
	 */
	public static BaseAdditionalInfoParser getInstance(String type) {
		BaseAdditionalInfoParser parser = null;
		if (type.equals("winter-sh")) {
			parser = new WinterShParser();
		} //else if ... (andere Parser)
		return parser;
	}

	public abstract AdditionalInfo getAdditionalInfo() throws IOException;

	@SuppressWarnings("SameParameterValue")
	protected String httpGet(String url, String encoding) throws IOException {
		return new String(Request.Get(url).execute().returnContent().asBytes(), encoding);
	}
}
