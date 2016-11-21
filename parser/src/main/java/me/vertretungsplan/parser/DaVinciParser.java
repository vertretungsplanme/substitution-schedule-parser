/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.jetbrains.annotations.NotNull;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for substitution schedules in HTML format created by the <a href="http://davinci.stueber.de/">DaVinci</a>
 * software. Probably only supports the newest version (DaVinci 6).
 * <p>
 * This parser can be accessed using <code>"davinci"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>url</code> (String, required)</dt>
 * <dd>The URL of the home page of the DaVinci HTML export can be found. This can either be a schedule for a single
 * day or an overview page with a selection of classes or days (in both calendar and list views)</dd>
 *
 * <dt><code>classes</code> (Array of Strings, required if <code>classesSource</code> not specified)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 *
 * <dt><code>classesSource</code> (String, optional)</dt>
 * <dd>The URL of the homepage of a DaVinci timetable, showing the list of all available classes</dd>
 * </dl>
 *
 * <dt><code>embeddedContentSelector</code> (String, optional)</dt>
 * <dd>When the DaVinci schedule is embedded in another HTML file using server-side code, you can use this to
 * specify which HTML elements should be considered as the containers for the DaVinci schedule. The CSS selector
 * syntax is supported as specified by
 * <a href="https://jsoup.org/cookbook/extracting-data/selector-syntax">JSoup</a>.</dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules.
 */
public class DaVinciParser extends BaseParser {
    private static final String ENCODING = "UTF-8";
    private static final String PARAM_URL = "url";
    private static final String PARAM_CLASSES_SOURCE = "classesSource";
    public static final String PARAM_EMBEDDED_CONTENT_SELECTOR = "embeddedContentSelector";

    public DaVinciParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
    }

    static void parseDaVinciTable(Element table, SubstitutionScheduleDay day, ColorProvider colorProvider) {
        parseDaVinciTable(table, day, null, colorProvider);
    }

    static void parseDaVinciTable(Element table, SubstitutionScheduleDay day, String klasse,
                                  ColorProvider colorProvider) {
        List<String> headers = new ArrayList<>();
        for (Element header : table.select("thead tr th, tr td[bgcolor=#9999FF]")) {
            headers.add(header.text());
        }

        // These two variables can
        Set<String> classes = new HashSet<>();
        String lesson = null;

        Pattern previousCurrentPattern = Pattern.compile("\\+([^\\s]+) \\(([^)]+)\\)");
        Pattern previousPattern = Pattern.compile("\\(([^)]+)\\)");

        for (Element row : table.select("tr:not(thead tr, tr:has(td[bgcolor=#9999FF]))")) {
            Substitution subst = new Substitution();
            Elements columns = row.select("td");
            for (int i = 0; i < headers.size(); i++) {
                String value = columns.get(i).text().replace("\u00a0", "");
                String header = headers.get(i);

                if (value.isEmpty()) {
                    if (header.equals("Klasse")) subst.setClasses(classes);
                    if (header.equals("Pos") || header.equals("Stunde") || header.equals("Std.")) {
                        subst.setLesson(lesson);
                    }
                    if (header.equals("Art") || header.equals("Merkmal")) subst.setType("Vertretung");
                    continue;
                }

                Matcher previousCurrentMatcher = previousCurrentPattern.matcher(value);
                Matcher previousMatcher = previousPattern.matcher(value);

                switch (header) {
                    case "Klasse":
                        classes = new HashSet<>(Arrays.asList(value.split(", ")));
                        subst.setClasses(classes);
                        break;
                    case "Pos":
                    case "Stunde":
                    case "Std.":
                        lesson = value;
                        subst.setLesson(lesson);
                        break;
                    case "VLehrer Kürzel":
                    case "VLehrer":
                    case "Vertreter":
                        if (!value.startsWith("*")) {
                            if (value.equals("Raumänderung")) {
                                subst.setType(value);
                            } else {
                                subst.setTeacher(value);
                            }
                        }
                        break;
                    case "Lehrer":
                    case "Lehrer Kürzel":
                    case "Lehrer Name":
                        if (previousCurrentMatcher.find()) {
                            subst.setTeacher(previousCurrentMatcher.group(1));
                            subst.setPreviousTeacher(previousCurrentMatcher.group(2));
                        } else if (previousMatcher.find()) {
                            subst.setPreviousTeacher(previousMatcher.group(1));
                        } else {
                            subst.setPreviousTeacher(value);
                        }
                        break;
                    case "VFach":
                        subst.setSubject(value);
                        break;
                    case "Fach":
                    case "Original Fach":
                        if (previousCurrentMatcher.find()) {
                            subst.setSubject(previousCurrentMatcher.group(1));
                            subst.setPreviousSubject(previousCurrentMatcher.group(2));
                        } else {
                            subst.setPreviousSubject(value);
                        }
                        break;
                    case "VRaum":
                        subst.setRoom(value);
                        break;
                    case "Raum":
                    case "Original Raum":
                        if (previousCurrentMatcher.find()) {
                            subst.setRoom(previousCurrentMatcher.group(1));
                            subst.setPreviousRoom(previousCurrentMatcher.group(2));
                        } else {
                            subst.setPreviousRoom(value);
                        }
                        break;
                    case "Art":
                    case "Merkmal":
                        subst.setType(value);
                        break;
                    case "Info":
                    case "Mitteilung":
                        subst.setDesc(value);
                        if (!headers.contains("Art") && !headers.contains("Merkmal")) {

                        }
                        break;
                }
            }
            if (klasse != null) {
                Set<String> fixedClasses = new HashSet<>();
                fixedClasses.add(klasse);
                subst.setClasses(fixedClasses);
            }
            if (subst.getType() == null) {
                String recognizedType = null;
                if (subst.getDesc() != null) recognizedType = recognizeType(subst.getDesc());
                subst.setType(recognizedType != null ? recognizedType : "Vertretung");
            }
            subst.setColor(colorProvider.getColor(subst.getType()));
            day.addSubstitution(subst);
        }
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException, CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);

        SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);

        String url = scheduleData.getData().getString(PARAM_URL);
        Document doc = Jsoup.parse(httpGet(url, ENCODING));
        List<String> dayUrls = getDayUrls(url, doc);

        if (scheduleData.getData().has(PARAM_EMBEDDED_CONTENT_SELECTOR)) {
            for (Element el : doc.select(scheduleData.getData().getString(PARAM_EMBEDDED_CONTENT_SELECTOR))) {
                schedule.addDay(parseDay(el));
            }
        } else {
            for (String dayUrl : dayUrls) {
                Document dayDoc;
                if (dayUrl.equals(url)) {
                    dayDoc = doc;
                } else {
                    dayDoc = Jsoup.parse(httpGet(dayUrl, ENCODING));
                }
                schedule.addDay(parseDay(dayDoc));
            }
        }

        schedule.setWebsite(url);
        schedule.setClasses(getAllClasses());
        schedule.setTeachers(getAllTeachers());

        return schedule;
    }

    @NotNull
    static List<String> getDayUrls(String url, Document doc)
            throws IOException {
        List<String> dayUrls = new ArrayList<>();
        if (doc.select("ul.classes").size() > 0) {
            // List of classes
            Elements classes = doc.select("ul.classes li a");
            for (Element klasse : classes) {
                dayUrls.add(new URL(new URL(url), klasse.attr("href")).toString());
            }
        } else if (doc.select("ul.month").size() > 0) {
            // List of days in calendar view
            Elements days = doc.select("ul.month li input[onclick]");
            for (Element day : days) {
                String urlFromOnclick = urlFromOnclick(day.attr("onclick"));
                if (urlFromOnclick == null) continue;
                dayUrls.add(new URL(new URL(url), urlFromOnclick).toString());
            }
        } else if (doc.select("ul.day-index").size() > 0) {
            // List of days in list view
            Elements days = doc.select("ul.day-index li a");
            for (Element day : days) {
                dayUrls.add(new URL(new URL(url), day.attr("href")).toString());
            }
        } else {
            // Single day
            dayUrls.add(url);
        }
        return dayUrls;
    }

    private static String urlFromOnclick(String onclick) {
        Pattern pattern = Pattern.compile("window\\.location\\.href='([^']+)'");
        Matcher matcher = pattern.matcher(onclick);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    @NotNull
    SubstitutionScheduleDay parseDay(Element doc) throws IOException {
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();

        String title = doc.select("h1.list-table-caption").first().text();
        String klasse = null;
        // title can either be date or class
        if (title.matches("\\w+ \\d+\\.\\d+.\\d{4}")) {
            day.setDateString(title);
            day.setDate(ParserUtils.parseDate(title));
        } else {
            klasse = title;
            String nextText = doc.select("h1.list-table-caption").first().nextElementSibling().text();
            if (nextText.matches("\\w+ \\d+\\.\\d+.\\d{4}")) {
                day.setDateString(nextText);
                day.setDate(ParserUtils.parseDate(nextText));
            } else {
                throw new IOException("Could not find date");
            }
        }

        for (Element p : doc.select(".row:has(h1.list-table-caption) p")) {
            for (TextNode node : p.textNodes()) {
                if (!node.text().trim().isEmpty()) day.addMessage(node.text().trim());
            }
        }
        for (Element message : doc.select(".callout")) {
            for (TextNode node : message.textNodes()) {
                if (!node.text().trim().isEmpty()) day.addMessage(node.text().trim());
            }
        }

        String lastChange = doc.select(".row.copyright div").first().ownText();
        Pattern pattern = Pattern.compile("(\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}) \\|");
        Matcher matcher = pattern.matcher(lastChange);
        if (matcher.find()) {
            LocalDateTime lastChangeTime =
                    DateTimeFormat.forPattern("dd-MM-yyyy HH:mm").parseLocalDateTime(matcher.group(1));
            day.setLastChange(lastChangeTime);
        }

        if (doc.select(".list-table").size() > 0 || !doc.select(".callout").text().contains("Es liegen keine")) {
            Element table = doc.select(".list-table").first();
            parseDaVinciTable(table, day, klasse, colorProvider);
        }
        return day;
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        if (scheduleData.getData().has(PARAM_CLASSES_SOURCE)) {
            Document doc = Jsoup.parse(httpGet(scheduleData.getData().getString("classesSource"), ENCODING));
            List<String> classes = new ArrayList<>();
            for (Element li : doc.select("li.Class")) {
                classes.add(li.text());
            }
            return classes;
        } else {
            return getClassesFromJson();
        }
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }
}
