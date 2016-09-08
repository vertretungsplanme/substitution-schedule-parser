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
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IndiwareParser extends BaseParser {
    protected JSONObject data;

    private static final int MAX_DAYS = 7;

    static final Pattern substitutionPattern = Pattern.compile("für ([^\\s]+) ((?:(?! ,).)+) ?,? ?(.*)");
    static final Pattern cancelPattern = Pattern.compile("([^\\s]+) (.+) fällt (:?leider )?aus");
    static final Pattern delayPattern = Pattern.compile("([^\\s]+) (.+) (verlegt nach .*)");
    static final Pattern selfPattern = Pattern.compile("selbst\\. ?,? ?(.*)");
    static final Pattern coursePattern = Pattern.compile("(.*)/ (.*)");
    static final Pattern bracesPattern = Pattern.compile("^\\((.*)\\)$");

    public IndiwareParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);
        JSONArray urls = data.getJSONArray("urls");
        String encoding = data.getString("encoding");
        List<Document> docs = new ArrayList<>();

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        Pattern dateFormatPattern = Pattern.compile("\\{date\\(([^)]+)\\)\\}");
        for (int i = 0; i < urls.length(); i++) {
            String url = urls.getString(i);
            Matcher matcher = dateFormatPattern.matcher(url);
            if (matcher.find()) {
                String pattern = matcher.group(1);
                for (int j = 0; j < MAX_DAYS; j++) {
                    LocalDate date = LocalDate.now().plusDays(j);
                    String dateStr = DateTimeFormat.forPattern(pattern).print(date);
                    String urlWithDate = matcher.replaceFirst(dateStr);
                    try {
                        String xml = httpGet(urlWithDate, encoding);
                        docs.add(Jsoup.parse(xml, url, Parser.xmlParser()));
                    } catch (IOException e) {
                        // fail silently
                    }
                }
            } else {
                String xml = httpGet(url, encoding);
                docs.add(Jsoup.parse(xml, url, Parser.xmlParser()));
            }
        }

        for (Document doc : docs) {
            v.addDay(parseIndiwareDay(doc));
        }

        v.setWebsite(urls.getString(0));

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());

        return v;
    }

    SubstitutionScheduleDay parseIndiwareDay(Document doc) {
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();
        Element vp = doc.select("vp").first();
        Element kopf = vp.select("kopf").first();

        String date = kopf.select("titel").text().replaceAll("\\(\\w-Woche\\)", "").trim();
        day.setDate(DateTimeFormat.forPattern("EEEE, dd. MMMM yyyy")
                .withLocale(Locale.GERMAN).parseLocalDate(date));

        String lastChange = kopf.select("datum").text();
        day.setLastChange(DateTimeFormat.forPattern("dd.MM.yyyy, HH:mm")
                .withLocale(Locale.GERMAN).parseLocalDateTime(lastChange));

        if (kopf.select("kopfinfo").size() > 0) {
            for (Element kopfinfo : kopf.select("kopfinfo").first().children()) {
                String title = kopfinfoTitle(kopfinfo.tagName());

                StringBuilder message = new StringBuilder();
                if (title != null) message.append("<b>").append(title).append(":").append("</b>").append(" ");
                message.append(kopfinfo.text());

                day.addMessage(message.toString());
            }
        }

        if (vp.select("fuss").size() > 0) {
            Element fuss = vp.select("fuss").first();
            StringBuilder message = new StringBuilder();
            boolean first = true;
            for (Element fusszeile : fuss.select("fusszeile")) {
                if (first) {
                    first = false;
                } else {
                    message.append("\n");
                }
                message.append(fusszeile.select("fussinfo").text());
            }
            day.addMessage(message.toString());
        }

        Element haupt = vp.select("haupt").first();

        for (Element aktion : haupt.select("aktion")) {
            Substitution substitution = new Substitution();
            String type = "Vertretung";
            String course = null;
            for (Element info : aktion.children()) {
                String value = info.text();
                if (value.equals("---")) continue;
                switch (info.tagName()) {
                    case "klasse":
                        Set<String> classes = new HashSet<>();
                        for (String klasse : value.split(",")) {
                            Matcher courseMatcher = coursePattern.matcher(klasse);
                            if (courseMatcher.matches()) {
                                classes.add(courseMatcher.group(1));
                                course = courseMatcher.group(2);
                            } else {
                                classes.add(klasse);
                            }
                        }
                        substitution.setClasses(classes);
                        break;
                    case "stunde":
                        substitution.setLesson(value);
                        break;
                    case "fach":
                        StringBuilder subject = new StringBuilder();
                        subject.append(value);
                        if (course != null) {
                            subject.append(" ").append(course);
                        }
                        substitution.setSubject(subject.toString());
                        break;
                    case "lehrer":
                        Matcher bracesMatcher = bracesPattern.matcher(value);
                        if (bracesMatcher.matches()) value = bracesMatcher.group(1);
                        substitution.setTeacher(value);
                        break;
                    case "raum":
                        substitution.setRoom(value);
                        break;
                    case "info":
                        Matcher substitutionMatcher = substitutionPattern.matcher(value);
                        Matcher cancelMatcher = cancelPattern.matcher(value);
                        Matcher delayMatcher = delayPattern.matcher(value);
                        Matcher selfMatcher = selfPattern.matcher(value);
                        if (substitutionMatcher.matches()) {
                            substitution.setPreviousSubject(substitutionMatcher.group(1));
                            substitution.setPreviousTeacher(substitutionMatcher.group(2));
                            if (!substitutionMatcher.group(3).isEmpty()) {
                                substitution.setDesc(substitutionMatcher.group(3));
                            }
                        } else if (cancelMatcher.matches()) {
                            type = "Entfall";
                            substitution.setPreviousSubject(cancelMatcher.group(1));
                            substitution.setPreviousTeacher(cancelMatcher.group(2));
                        } else if (delayMatcher.matches()) {
                            type = "Verlegung";
                            substitution.setPreviousSubject(delayMatcher.group(1));
                            substitution.setPreviousTeacher(delayMatcher.group(2));
                            substitution.setDesc(delayMatcher.group(3));
                        } else if (selfMatcher.matches()) {
                            type = "selbst.";
                            if (!selfMatcher.group(1).isEmpty()) substitution.setDesc(selfMatcher.group(1));
                        } else {
                            substitution.setDesc(value);
                        }
                        break;
                }
            }
            substitution.setType(type);
            substitution.setColor(colorProvider.getColor(substitution.getType()));
            if (course != null && substitution.getSubject() == null) {
                substitution.setSubject(course);
            }
            day.addSubstitution(substitution);
        }

        return day;
    }

    private static String kopfinfoTitle(String type) {
        switch (type) {
            case "abwesendl":
                return "Abwesende Lehrer";
            case "abwesendk":
                return "Abwesende Klassen";
            case "abwesendr":
                return "Nicht verfügbare Räume";
            case "aenderungl":
                return "Lehrer mit Änderung";
            case "aenderungk":
                return "Klassen mit Änderung";
            default:
                return null;
        }
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException {
        return getClassesFromJson();
    }

    @Override
    public List<String> getAllTeachers() throws IOException, JSONException {
        return null;
    }
}
