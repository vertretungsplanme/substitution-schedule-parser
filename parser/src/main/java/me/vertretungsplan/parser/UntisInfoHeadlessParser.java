/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.List;

/**
 * Parser for substitution schedules in HTML format created by the <a href="http://untis.de/">Untis</a> software
 * using the "Info-Stundenplan" layout, but without the navigation bar. Only substitution schedule tables are supported,
 * not timetables.
 * <p>
 * Example: <a href="http://www.egwerther.de/vertretungsplan/w00000.htm">EG Werther</a>
 * <p>
 * This parser can be accessed using <code>"untis-info-headless"</code> for
 * {@link SubstitutionScheduleData#setApi(String)}.
 *
 * # Configuration parameters
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>url</code> (String, required)</dt>
 * <dd>The URL of the HTML file containing the schedule. There is only one page spanning a whole week.</dd>
 *
 * <dt><code>encoding</code> (String, required)</dt>
 * <dd>The charset of the HTML file. It's probably either UTF-8 or ISO-8859-1.</dd>
 *
 * <dt><code>classes</code> (Array of Strings, required)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules
 * and those specified in {@link UntisCommonParser}.
 */
public class UntisInfoHeadlessParser extends UntisCommonParser {

	private static final String PARAM_URL = "url";
    private static final String PARAM_ENCODING = "encoding";
	private String url;
	private JSONObject data;

	public UntisInfoHeadlessParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
		super(scheduleData, cookieProvider);
		try {
			data = scheduleData.getData();
            url = data.getString(PARAM_URL);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public SubstitutionSchedule getSubstitutionSchedule()
			throws IOException, JSONException, CredentialInvalidException {
		new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);

		SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

		Document doc = Jsoup.parse(httpGet(url, data.optString(PARAM_ENCODING, null)));
        doc.setBaseUri(url);
        Elements dayElems = doc.select("#vertretung > p > b, #vertretung > b");

        Elements frames = doc.select("frame[src*=w00]");
        if (dayElems.size() == 0 && frames.size() > 0) {
            // doc is embedded in frame
            doc = Jsoup.parse(httpGet(frames.get(0).absUrl("src"), data.optString(PARAM_ENCODING, null)));
            dayElems = doc.select("#vertretung > p > b, #vertretung > b");
        } else if (dayElems.size() == 0) {
            // seen at GHS Berlin, different kinds of center > font > center ... stacked (sometimes within #vertretung)
            dayElems = doc.select("center > font > p > b");
        }

        final List<String> allClasses = getAllClasses();
        if (dayElems.size() > 0) {
            // untis-info days
            for (Element dayElem : dayElems) {
                SubstitutionScheduleDay day = new SubstitutionScheduleDay();
                day.setLastChangeString("");

                String date = dayElem.text();
                day.setDateString(date);
                day.setDate(ParserUtils.parseDate(date));

                Element next;
                if (dayElem.parent().tagName().equals("p")) {
                    next = dayElem.parent().nextElementSibling().nextElementSibling();
                } else {
                    next = dayElem.parent().select("p").first().nextElementSibling();
                }
                parseDay(day, next, v, null, allClasses);
            }
        } else if (doc.select("tr:has(td[align=center]):gt(0)").size() > 0) {
            // untis-subst table
            parseSubstitutionTable(v, null, doc);
        }

        v.setClasses(allClasses);
        v.setTeachers(getAllTeachers());
		return v;
	}

	@Override
	public List<String> getAllTeachers() {
		return null;
	}

}
