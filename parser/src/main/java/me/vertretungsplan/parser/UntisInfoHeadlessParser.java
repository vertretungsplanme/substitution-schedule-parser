/*  Vertretungsplan - Android-App für Vertretungspläne von Schulen
    Copyright (C) 2014  Johan v. Forstner

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see [http://www.gnu.org/licenses/]. */

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
			parseDay(day, next, v);
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
