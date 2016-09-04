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
 * Parser für Untis-Vertretungspläne mit dem Info-Stundenplan-Layout, aber ohne Navigationsleiste
 * Beispiel: http://www.vertretung.org/vertretung/w00000.htm
 * Wurde bisher noch nicht mit anderen Schulen getestet.
 *
 */
public class UntisInfoHeadlessParser extends UntisCommonParser {
	
	private String url;
	private JSONObject data;

	public UntisInfoHeadlessParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
		super(scheduleData, cookieProvider);
		try {
			data = scheduleData.getData();
			url = data.getString("url");
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public SubstitutionSchedule getSubstitutionSchedule()
			throws IOException, JSONException, CredentialInvalidException {
		new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);

		SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

		Document doc = Jsoup.parse(httpGet(url, data.getString("encoding")));
		Elements dayElems = doc.select("#vertretung > p > b, #vertretung > b");
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
			parseDay(day, next, v, null);
		}
		v.setClasses(getAllClasses());
		v.setTeachers(getAllTeachers());
		return v;
	}

	@Override
	public List<String> getAllTeachers() {
		return null;
	}

}
