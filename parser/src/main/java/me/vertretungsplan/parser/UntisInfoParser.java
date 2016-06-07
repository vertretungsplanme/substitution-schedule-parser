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
import org.apache.http.client.HttpResponseException;
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
 * Parser für Untis-Vertretungspläne mit dem Info-Stundenplan-Layout
 * Beispiel: AKG Bensheim http://www.akg-bensheim.de/akgweb2011/content/Vertretung/default.htm
 *
 */
public class UntisInfoParser extends UntisCommonParser {
	
	private String baseUrl;
	private JSONObject data;
	private String navbarDoc;

	public UntisInfoParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
		super(scheduleData, cookieProvider);
		try {
			data = scheduleData.getData();
			baseUrl = data.getString("baseurl");
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	private String getNavbarDoc() throws JSONException, IOException {
		if(navbarDoc == null) {
			String navbarUrl = baseUrl + "/frames/navbar.htm";
			navbarDoc = httpGet(navbarUrl, data.getString("encoding"));
		}
		return navbarDoc;
	}

	@Override
	public SubstitutionSchedule getSubstitutionSchedule()
			throws IOException, JSONException, CredentialInvalidException {
		new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);
		
		Document navbarDoc = Jsoup.parse(getNavbarDoc().replace("&nbsp;", ""));
		Element select = navbarDoc.select("select[name=week]").first();

		SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);
		
		String info = navbarDoc.select(".description").text();
		String lastChange;
		try {
			lastChange = info.substring(info.indexOf("Stand:"));
		} catch (Exception e) {
            try {
				String infoHtml = httpGet(baseUrl + "/frames/title.htm", data.getString("encoding"));
				Document infoDoc = Jsoup.parse(infoHtml);
                String info2 = infoDoc.select(".description").text();
				lastChange = info2.substring(info2.indexOf("Stand:"));
			} catch (Exception e1) {
				lastChange = "";
			}
        }
		
		for (Element option:select.children()) {
			String week = option.attr("value");
            String letter = data.optString("letter", "w");
            if (data.optBoolean("single_classes", false)) {
				int classNumber = 1;
				for (String klasse:getAllClasses()) {
					String paddedNumber = String.format("%05d", classNumber);
					String url;
					if (data.optBoolean("w_after_number", false))
						url = baseUrl + "/" + week + "/" + letter + "/" + letter + paddedNumber + ".htm";
					else
						url = baseUrl + "/" + letter + "/" + week + "/" + letter + paddedNumber + ".htm";

					try {
						Document doc = Jsoup.parse(httpGet(url, data.getString("encoding")));
						parseDays(v, lastChange, doc);
					} catch (HttpResponseException e) {
						if (e.getStatusCode() == 500) {
							// occurs in Hannover_MMBS
							classNumber ++;
							continue;
						} else {
							throw e;
						}
					}

					classNumber ++;
				}
			} else {
				String url;
				if (data.optBoolean("w_after_number", false))
                    url = baseUrl + "/" + week + "/" + letter + "/" + letter + "00000.htm";
                else
                    url = baseUrl + "/" + letter + "/" + week + "/" + letter + "00000.htm";
				Document doc = Jsoup.parse(httpGet(url, data.getString("encoding")));
				parseDays(v, lastChange, doc);
			}
		}
		v.setClasses(getAllClasses());
		v.setTeachers(getAllTeachers());
		v.setWebsite(baseUrl + "/default.htm");
		return v;
	}

	private void parseDays(SubstitutionSchedule v, String lastChange, Document doc) throws JSONException {
		Elements days = doc.select("#vertretung > p > b, #vertretung > b");
		for (Element dayElem : days) {
			SubstitutionScheduleDay day = new SubstitutionScheduleDay();

			day.setLastChangeString(lastChange);
			day.setLastChange(ParserUtils.parseDateTime(lastChange));

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
	}

	@Override
	public List<String> getAllClasses() throws JSONException, IOException {
        if (super.getAllClasses() != null) {
            return super.getAllClasses();
        } else {
            String js = getNavbarDoc();
            Pattern pattern = Pattern.compile("var classes = (\\[[^\\]]*\\]);");
            Matcher matcher = pattern.matcher(js);
            if (matcher.find()) {
                JSONArray classesJson = new JSONArray(matcher.group(1));
				List<String> classes = new ArrayList<>();
				for (int i = 0; i < classesJson.length(); i++) {
					String className = classesJson.getString(i);
					if (data.optString("classSelectRegex", null) != null) {
						Pattern classNamePattern = Pattern.compile(data.getString("classSelectRegex"));
						Matcher classNameMatcher = classNamePattern.matcher(className);
						if (classNameMatcher.find()) {
							if (classNameMatcher.groupCount() > 0) {
								StringBuilder builder = new StringBuilder();
								for (int j = 1; j <= classNameMatcher.groupCount(); j++) {
									if (classNameMatcher.group(j) != null) {
										builder.append(classNameMatcher.group(j));
									}
								}
								className = builder.toString();
							} else {
								className = classNameMatcher.group();
							}
						}
					}
                    classes.add(className);
                }
                return classes;
            } else {
                throw new IOException();
            }
        }
    }

	@Override
	public List<String> getAllTeachers() {
		return null;
	}

}
