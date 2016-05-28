/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.joda.time.LocalDateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser für Untis-Vertretungspläne mit dem Vertretungsplanungs-Layout
 * Beispiel: http://www.jkg-stuttgart.de/vertretungsplan/sa3.htm
 *
 */
public class UntisSubstitutionParser extends UntisCommonParser {

	private String baseUrl;
	private JSONObject data;

	public UntisSubstitutionParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
		super(scheduleData, cookieProvider);
		try {
			data = scheduleData.getData();
			baseUrl = data.getString("baseurl");
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public SubstitutionSchedule getSubstitutionSchedule() throws IOException,
			JSONException, CredentialInvalidException {
		new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);

		String encoding = data.getString("encoding");
		Document doc = Jsoup.parse(this.httpGet(baseUrl, encoding));
		Elements classes = doc.select("td a");

		SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

		String lastChange = doc.select("td[align=right]:not(:has(b))").text();
		LocalDateTime lastChangeDate = ParserUtils.parseDateTime(lastChange);

		Pattern dayPattern = Pattern.compile("\\d\\d?.\\d\\d?. / \\w+");

		for (Element klasse : classes) {
			Document classDoc = Jsoup.parse(httpGet(baseUrl.substring(0, baseUrl.lastIndexOf("/"))
					+ "/" + klasse.attr("href"), encoding));

			int dateColumn = -1;
			JSONArray columns = data.getJSONArray("columns");
			for (int i = 0; i < columns.length(); i++) {
				if (columns.getString(i).equals("date")) {
					dateColumn = i;
					break;
				}
			}

			Element table = classDoc.select("table[rules=all]").first();

			if (dateColumn == -1) {
				SubstitutionScheduleDay day = new SubstitutionScheduleDay();
				day.setLastChangeString(lastChange);
				day.setLastChange(lastChangeDate);
				String title = classDoc.select("font[size=5], font[size=4]").text();
				Matcher matcher = dayPattern.matcher(title);
				if (matcher.find()) {
					String date = matcher.group();
					day.setDateString(date);
					day.setDate(ParserUtils.parseDate(date));
				}
				parseVertretungsplanTable(table, data, day);
				v.addDay(day);
			} else {
				for (Element line : table
						.select("tr.list.odd:not(:has(td.inline_header)), "
								+ "tr.list.even:not(:has(td.inline_header)), "
								+ "tr:has(td[align=center]:has(font[color]))")) {
					SubstitutionScheduleDay day = null;
					String date = line.select("td").get(dateColumn).text().trim();
					for (SubstitutionScheduleDay search : v.getDays()) {
						if (search.getDateString().equals(date)) {
							day = search;
							break;
						}
					}
					if (day == null) {
						day = new SubstitutionScheduleDay();
						day.setDateString(date);
						day.setDate(ParserUtils.parseDate(date));
						day.setLastChangeString(lastChange);
						day.setLastChange(lastChangeDate);
						v.addDay(day);
					}
					parseVertretungsplanTable(line, data, day);
				}
			}
		}
		v.setClasses(getAllClasses());
		v.setTeachers(getAllTeachers());
		return v;
	}

	@Override
	public List<String> getAllClasses() throws JSONException, IOException {
		JSONArray classesJson = data.getJSONArray("classes");
		List<String> classes = new ArrayList<String>();
		for (int i = 0; i < classesJson.length(); i++) {
			classes.add(classesJson.getString(i));
		}
		return classes;
	}

	@Override
	public List<String> getAllTeachers() {
		return null;
	}

}
