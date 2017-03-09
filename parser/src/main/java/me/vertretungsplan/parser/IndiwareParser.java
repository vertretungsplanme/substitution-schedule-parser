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
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for substitution schedules in XML or HTML format created by the <a href="http://indiware.de/">Indiware</a>
 * software.
 * <p>
 * This parser can be accessed using <code>"indiware"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>urls</code> (Array of Strings, required)</dt>
 * <dd>The URLs of the XML files of the schedule. There is one file for each day. If the filenames themselves
 * contain the date, you can use something like <code>{date(yyyy-MM-dd)}</code> in the URL. This placeholder will then
 * be replaced with the dates of the next 7 days.</dd>
 *
 * <dt><code>encoding</code> (String, required)</dt>
 * <dd>The charset of the XML files. It's probably either UTF-8 or ISO-8859-1.</dd>
 *
 * <dt><code>classes</code> (Array of Strings, required)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules.
 */
public class IndiwareParser extends BaseParser {
    private static final String PARAM_URLS = "urls";
    private static final String PARAM_ENCODING = "encoding";
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
        JSONArray urls = data.getJSONArray(PARAM_URLS);
        String encoding = data.getString(PARAM_ENCODING);
        List<String> docs = new ArrayList<>();

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        int successfulSchedules = 0;
        IOException lastException = null;
        for (int i = 0; i < urls.length(); i++) {
            if (urls.optJSONObject(i) != null) {
                try {
                    JSONObject obj = urls.getJSONObject(i);
                    String url = obj.getString("url");
                    if (obj.has("postData")) {
                        JSONObject postParams = obj.getJSONObject("postData");
                        List<NameValuePair> nvps = new ArrayList<>();
                        for (String name : JSONObject.getNames(postParams)) {
                            String value = postParams.getString(name);
                            nvps.add(new BasicNameValuePair(name, value));
                        }
                        docs.add(httpPost(url, encoding, nvps));
                        successfulSchedules++;
                    }
                } catch (IOException e) {
                    lastException = e;
                }
            } else {
                for (String url : ParserUtils.handleUrlWithDateFormat(urls.getString(i))) {
                    try {
                        docs.add(httpGet(url, encoding));
                        successfulSchedules++;
                    } catch (IOException e) {
                        lastException = e;
                    }
                }
            }
        }
        if (successfulSchedules == 0 && lastException != null) {
            throw lastException;
        }

        for (String response : docs) {
            boolean html;
            Document doc;
            if (response.contains("<html")) {
                html = true;
                doc = Jsoup.parse(response);
            } else {
                html = false;
                doc = Jsoup.parse(response, null, Parser.xmlParser());
            }
            v.addDay(parseIndiwareDay(doc, html));
        }

        v.setWebsite(urls.getString(0));

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());

        return v;
    }

    private interface DataSource {
        Element titel();

        Element datum();

        Elements kopfinfos();

        Element fuss();

        Elements fusszeilen();

        Elements aktionen();
    }

    private class XMLDataSource implements DataSource {
        private Element vp;
        private Element kopf;

        public XMLDataSource(Document doc) {
            vp = doc.select("vp").first();
            kopf = vp.select("kopf").first();
        }

        @Override public Element titel() {
            return kopf.select("titel").first();
        }

        @Override public Element datum() {
            return kopf.select("datum").first();
        }

        @Override public Elements kopfinfos() {
            return kopf.select("kopfinfo > *");
        }

        @Override public Element fuss() {
            return vp.select("fuss").first();
        }

        @Override public Elements fusszeilen() {
            return fuss().select("fusszeile fussinfo");
        }

        @Override public Elements aktionen() {
            return vp.select("haupt > aktion");
        }
    }

    private class HTMLDataSource implements DataSource {
        private Document doc;

        public HTMLDataSource(Document doc) {
            this.doc = doc;
        }

        @Override public Element titel() {
            return doc.select(".vpfuerdatum").first();
        }

        @Override public Element datum() {
            return doc.select(".vpdatum").first();
        }

        @Override public Elements kopfinfos() {
            return doc.select(".tablekopf").first().select("tr");
        }

        @Override public Element fuss() {
            return doc.select("p:has(.ueberschrift:contains(Informationen)) + table").first();
        }

        @Override public Elements fusszeilen() {
            return fuss().select("tr td");
        }

        @Override public Elements aktionen() {
            return doc.select("p:has(.ueberschrift:contains(Unterrichtsstunden)) + p + table tr:gt(0)");
        }

        public Elements headers() {
            return doc.select("p:has(.ueberschrift:contains(Unterrichtsstunden)) + p + table th");
        }
    }

    SubstitutionScheduleDay parseIndiwareDay(Document doc, boolean html) {
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();

        DataSource ds;
        if (html) {
            ds = new HTMLDataSource(doc);
        } else {
            ds = new XMLDataSource(doc);
        }

        String date = ds.titel().text().replaceAll("\\(\\w-Woche\\)", "").trim();
        day.setDate(DateTimeFormat.forPattern("EEEE, dd. MMMM yyyy")
                .withLocale(Locale.GERMAN).parseLocalDate(date));

        String lastChange = ds.datum().text();
        day.setLastChange(DateTimeFormat.forPattern("dd.MM.yyyy, HH:mm")
                .withLocale(Locale.GERMAN).parseLocalDateTime(lastChange));

        if (ds.kopfinfos().size() > 0) {
            for (Element kopfinfo : ds.kopfinfos()) {
                String title = html ? kopfinfo.select("th").text() : kopfinfoTitle(kopfinfo.tagName()) + ":";

                StringBuilder message = new StringBuilder();
                if (title != null && !title.isEmpty()) {
                    message.append("<b>").append(title).append("</b>").append(" ");
                }
                message.append(html ? kopfinfo.select("td").text() : kopfinfo.text());

                day.addMessage(message.toString());
            }
        }

        if (ds.fuss() != null) {
            Element fuss = ds.fuss();
            StringBuilder message = new StringBuilder();
            boolean first = true;
            for (Element fusszeile : ds.fusszeilen()) {
                if (first) {
                    first = false;
                } else {
                    message.append("\n");
                }
                message.append(fusszeile.text());
            }
            day.addMessage(message.toString());
        }

        List<String> columnTypes = null;
        if (html) {
            columnTypes = new ArrayList<>();
            for (Element th : ((HTMLDataSource) ds).headers()) {
                columnTypes.add(th.className().replace("thplan", ""));
            }
        }

        for (Element aktion : ds.aktionen()) {
            Substitution substitution = new Substitution();
            String type = "Vertretung";
            String course = null;
            int i = 0;
            for (Element info : aktion.children()) {
                String value = info.text();
                if (value.equals("---")) continue;
                final String columnType = html ? columnTypes.get(i) : info.tagName();
                switch (columnType) {
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
                i++;
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
