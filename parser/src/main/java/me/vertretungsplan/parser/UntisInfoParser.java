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
import org.jetbrains.annotations.NotNull;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
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
 * Parser for substitution schedules in HTML format created by the <a href="http://untis.de/">Untis</a> software
 * using the "Info-Stundenplan" layout. Only substitution schedule tables (often labelled with "Ver-Kla" in the
 * dropdown menu) are supported, not timetables.
 * <p>
 * Example: <a href="http://www.akg-bensheim.de/akgweb2011/content/Vertretung/default.htm">AKG Bensheim</a>
 * <p>
 * This parser can be accessed using <code>"untis-info"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>baseurl</code> (String, required)</dt>
 * <dd>The base URL under which all the HTML files (e.g. <code>default.htm</code>) are located, wthout a slash at
 * the end.</dd>
 *
 * <dt><code>encoding</code> (String, required)</dt>
 * <dd>The charset of the HTML files. It's probably either UTF-8 or ISO-8859-1.</dd>
 *
 * <dt><code>classes</code> (Array of Strings, optional)</dt>
 * <dd>The list of all classes, as they can appear in the schedule. If this is omitted, classes are automatically
 * determined by parsing JavaScript code in the <code>frames/navbar.htm</code> page.</dd>
 *
 * <dt><code>classSelectRegex</code> (String, optional)</dt>
 * <dd>RegEx to modify the classes parsed from JavaScript code in {@link #getAllClasses()}. The RegEx is matched against
 * the class using {@link Matcher#find()}. If the RegEx contains groups, the concatenation of all group results
 * {@link Matcher#group(int)} is used as the resulting class. Otherwise, {@link Matcher#group()} is used.
 * </dd>
 *
 * <dt><code>removeNonMatchingClasses</code> (Boolean, optional)</dt>
 * <dd>If this is set to <code>true</code>, classes parsed from JavaScript in {@link #getAllClasses()} where
 * <code>classSelectRegex</code> is not found ({@link Matcher#find()} returns <code>false</code>) are discarded from
 * the list. Default: <code>false</code>
 * </dd>
 *
 * <dt><code>singleClasses</code> (Boolean, optional)</dt>
 * <dd>Set this to <code>true</code> if there is no common substitution schedule for all classes, but separate ones
 * for each class selectable in a dropdown instead. This of course drastically increases the number of HTTP
 * requests needed to load the schedule. Default: <code>"false"</code>
 * </dd>
 *
 * <dt><code>wAfterNumber</code> (Boolean, optional)</dt>
 * <dd>Set this to <code>true</code> if the URL of the actual schedules (displayed in a frame) end with
 * <code>36/w/w00000.htm</code> instead of <code>w/36/w00000.htm</code>. Default: <code>"false"</code>
 * </dd>
 *
 * <dt><code>letter</code> (String, optional, Default: <code>w</code>)</dt>
 * <dd>The letter occurring in the URL of the schedule pages. For student schedules, this is almost always a
 * <code>w</code>. Teacher schedules use a <code>v</code>.</dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules
 * and those specified in {@link UntisCommonParser}.
 */
public class UntisInfoParser extends UntisCommonParser {

    public static final String PARAM_BASEURL = "baseurl";
    private static final String PARAM_ENCODING = "encoding";
    public static final String PARAM_CLASS_SELECT_REGEX = "classSelectRegex";
    public static final String PARAM_REMOVE_NON_MATCHING_CLASSES = "removeNonMatchingClasses";
    private static final String PARAM_SINGLE_CLASSES = "singleClasses";
    public static final String PARAM_W_AFTER_NUMBER = "wAfterNumber";
    private static final String PARAM_LETTER = "letter";
    private static final String PARAM_SCHEDULE_TYPE = "scheduleType";
    private String baseUrl;
    private JSONObject data;
    private String navbarDoc;

    public UntisInfoParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        try {
            data = scheduleData.getData();
            baseUrl = data.getString(PARAM_BASEURL);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String getNavbarDoc() throws JSONException, IOException, CredentialInvalidException {
        if (navbarDoc == null) {
            String navbarUrl = baseUrl + "/frames/navbar.htm";
            navbarDoc = httpGet(navbarUrl, data.getString(PARAM_ENCODING));
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
            lastChange = info.substring(info.indexOf("Stand:") + "Stand:".length()).trim();
        } catch (Exception e) {
            try {
                String infoHtml = httpGet(baseUrl + "/frames/title.htm", data.getString(PARAM_ENCODING));
                Document infoDoc = Jsoup.parse(infoHtml);
                String info2 = infoDoc.select(".description").text();
                lastChange = info2.substring(info2.indexOf("Stand:") + "Stand:".length()).trim();
            } catch (Exception e1) {
                lastChange = "";
            }
        }

        int successfulWeeks = 0;
        HttpResponseException lastException = null;
		for (Element option : select.children()) {
			String week = option.attr("value");
            String weekName = option.text();
			if (data.optBoolean(PARAM_SINGLE_CLASSES,
                    data.optBoolean("single_classes", false))) { // backwards compatibility
                int classNumber = 1;
				for (String klasse : getAllClasses()) {
                    String url = getScheduleUrl(week, classNumber, data);
					try {
                        parsePage(v, lastChange, klasse, url, weekName);
                    } catch (HttpResponseException e) {
                        if (e.getStatusCode() == 500) {
                            // occurs in Hannover_MMBS
                            classNumber++;
                            continue;
                        } else {
                            throw e;
                        }
                    }

					classNumber ++;
				}
			    successfulWeeks++;
			} else {
				String url = getScheduleUrl(week, 0,data);
                try {
                    parsePage(v, lastChange,  null, url, weekName);
                    successfulWeeks++;
                } catch (HttpResponseException e) {
                    lastException = e;
                }
            }
        }
        if (successfulWeeks == 0 && lastException != null) {
            throw lastException;
        }
        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());
        v.setWebsite(baseUrl + "/default.htm");
        return v;
    }

    @NotNull
    static String getScheduleUrl(String week, int number, JSONObject data)
            throws JSONException {
        String paddedNumber = String.format("%05d", number);
        String baseUrl = data.getString(PARAM_BASEURL);
        String letter = getLetter(data);
        String url;
        if (data.optBoolean(PARAM_W_AFTER_NUMBER,
                data.optBoolean("w_after_number", false))) { // backwards compatibility
            url = baseUrl + "/" + week + "/" + letter + "/" + letter + paddedNumber + ".htm";
        } else {
            url = baseUrl + "/" + letter + "/" + week + "/" + letter + paddedNumber + ".htm";
        }
        return url;
    }

    private static String getLetter(JSONObject data) {
        String letter;
        switch (data.optString(PARAM_SCHEDULE_TYPE, "substitution")) {
            case "timetable":
                letter = "c";
                break;
            case "substitutionTeacher":
                letter = "v";
                break;
            case "substitution":
            default:
                letter = "w";
                break;
        }
        return data.optString(PARAM_LETTER, letter);
    }

    private void parsePage(SubstitutionSchedule v, String lastChange, String klasse, String url, String weekName)
            throws IOException, CredentialInvalidException, JSONException {
        Document doc = Jsoup.parse(httpGet(url, data.getString(PARAM_ENCODING)));
        switch (data.optString(PARAM_SCHEDULE_TYPE, "substitution")) {
            case "timetable":
                parseTimetable(v, lastChange, doc, klasse, weekName);
                break;
            case "substitution":
            case "substitutionTeacher":
            default:
                parseSubstitutionDays(v, lastChange, doc, klasse);
                break;
        }

    }

    private void parseTimetable(SubstitutionSchedule v, String lastChange, Document doc, String klasse, String
            weekName) {
        v.setLastChange(ParserUtils.parseDateTime(lastChange));
        LocalDate weekStart = DateTimeFormat.forPattern("d.M.yyyy").parseLocalDate(weekName);

        Element table = doc.select("table").first();

        List<SubstitutionScheduleDay> days = new ArrayList<>();
        for (int i = 0; i < table.select("tr").first().select("td:gt(0)").size(); i++) {
            LocalDate date = weekStart.plusDays(i);
            SubstitutionScheduleDay day = new SubstitutionScheduleDay();
            day.setDate(date);
        }

        List<String> lessons = new ArrayList<>();
        for (Element lessonHeader : table.select("tr:gt(0) td:eq(0)")) {
            lessons.add(lessonHeader.select("table").first().select("td").first().text());
        }

        for (int col = 0; col < days.size(); col++) {

        }
    }

    private void parseSubstitutionDays(SubstitutionSchedule v, String lastChange, Document doc, String klasse)
            throws JSONException, CredentialInvalidException {
        Elements days = doc.select("#vertretung > p > b, #vertretung > b");
        if (days.size() > 0) {
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
                parseDay(day, next, v, klasse);
            }
        } else if (doc.select("tr:has(td[align=center]):gt(0)").size() > 0) {
            parseSubstitutionTable(v, null, doc);
            v.setLastChangeString(lastChange);
            v.setLastChange(ParserUtils.parseDateTime(lastChange));
        }
    }

    @Override
    public List<String> getAllClasses() throws JSONException, IOException, CredentialInvalidException {
        if (super.getAllClasses() != null) {
            return super.getAllClasses();
        } else {
            return parseClasses(getNavbarDoc(), data);
        }
    }

    @NotNull static List<String> parseClasses(String navbarDoc, JSONObject data) throws JSONException, IOException {
        Pattern pattern = Pattern.compile("var classes = (\\[[^\\]]*\\]);");
        Matcher matcher = pattern.matcher(navbarDoc);
        if (matcher.find()) {
            JSONArray classesJson = new JSONArray(matcher.group(1));
            List<String> classes = new ArrayList<>();
            for (int i = 0; i < classesJson.length(); i++) {
                String className = classesJson.getString(i);
                if (data.optString(PARAM_CLASS_SELECT_REGEX, null) != null) {
                    Pattern classNamePattern = Pattern.compile(data.getString(PARAM_CLASS_SELECT_REGEX));
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
                    } else if (data.optBoolean(PARAM_REMOVE_NON_MATCHING_CLASSES, false)) {
                        continue;
                    }
                }
                classes.add(className);
            }
            return classes;
        } else {
            throw new IOException();
        }
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }

}
