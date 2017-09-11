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
import org.jetbrains.annotations.NotNull;
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
 *
 * <dt><code>embeddedContentSelector</code> (String, optional)</dt>
 * <dd>When the Untis schedule is embedded in another HTML file using server-side code, you can use this to
 * specify which HTML elements should be considered as the containers for the Indiware HTML schedule. The CSS selector
 * syntax is supported as specified by
 * <a href="https://jsoup.org/cookbook/extracting-data/selector-syntax">JSoup</a>.</dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules.
 */
public class IndiwareParser extends BaseParser {
    private static final String PARAM_URLS = "urls";
    private static final String PARAM_ENCODING = "encoding";
    private static final String PARAM_EMBEDDED_CONTENT_SELECTOR = "embeddedContentSelector";
    protected JSONObject data;

    private static final int MAX_DAYS = 7;

    static final Pattern datePattern = Pattern.compile("\\w+, \\d\\d?\\. \\w+ \\d{4}", Pattern.UNICODE_CHARACTER_CLASS);
    static final Pattern lastChangePattern = Pattern.compile("\\d\\d?\\.\\d\\d?\\.\\d{4}, \\d\\d?\\:\\d\\d");
    static final Pattern substitutionPattern = Pattern.compile("für ([^\\s]+) ((?:(?! ,|Frau|Herr).)+|(?:Herr|Frau) " +
            "[^\\s]+) ?,? ?(.*)");
    static final Pattern cancelPattern = Pattern.compile("((?!verlegt)[^\\s]+) (.+) fällt (:?leider )?aus");
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
        String encoding = data.optString(PARAM_ENCODING, null);
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

        successfulSchedules = 0;
        lastException = null;
        for (String response : docs) {
            try {
                parseIndiwarePage(v, response);
                successfulSchedules++;
            } catch (IOException e) {
                lastException = e;
            }
        }

        if (successfulSchedules == 0 && lastException != null) {
            throw lastException;
        }

        v.setWebsite(urls.getString(0));

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());

        return v;
    }

    void parseIndiwarePage(SubstitutionSchedule v, String response) throws JSONException, IOException {
        boolean html;
        Element doc;
        if (response.contains("<html") || response.contains("<table")) {
            html = true;
            doc = Jsoup.parse(response);
        } else {
            html = false;
            doc = Jsoup.parse(response, "", Parser.xmlParser());
        }
        if (html && data.has(PARAM_EMBEDDED_CONTENT_SELECTOR)) {
            String selector = data.getString(PARAM_EMBEDDED_CONTENT_SELECTOR);
            Elements elems = doc.select(selector);
            if (elems.size() == 0) throw new IOException("No elements found using " + selector);
            for (Element elem : elems) {
                v.addDay(parseIndiwareDay(elem, true));
            }
        } else if (html && doc.select(".vpfuer").size() > 1) {
            // multiple schedules after each other on one page
            String[] htmls = doc.html().split("<span class=\"vpfuer\">");
            for (int i = 1; i < htmls.length; i++) {
                Document splitDoc = Jsoup.parse(htmls[i]);
                v.addDay(parseIndiwareDay(splitDoc, true));
            }
        } else {
            v.addDay(parseIndiwareDay(doc, html));
        }
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

        public XMLDataSource(Element doc) {
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
        private Element doc;

        public HTMLDataSource(Element doc) {
            this.doc = doc;
        }

        @Override public Element titel() {
            return doc.select(".vpfuerdatum").first();
        }

        @Override public Element datum() {
            return doc.select(".vpdatum").first();
        }

        @Override public Elements kopfinfos() {
            return doc.select("table:has(th[class^=thkopf]) tr");
        }

        @Override public Element fuss() {
            return doc.select("table:not(:has(th[class^=thkopf])):not(:has(.tdaktionen))" +
                    ":not(span:contains(Aufsichten) + table)").first();
        }

        @Override public Elements fusszeilen() {
            return fuss().select("tr td");
        }

        @Override public Elements aktionen() {
            return doc.select("table:has(.tdaktionen) tr:gt(0)");
        }

        public Elements headers() {
            return doc.select("table:has(.tdaktionen) th");
        }
    }

    SubstitutionScheduleDay parseIndiwareDay(Element doc, boolean html) throws IOException {
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();

        DataSource ds;
        if (html) {
            ds = new HTMLDataSource(doc);
        } else {
            ds = new XMLDataSource(doc);
        }


        Matcher matcher = datePattern.matcher(ds.titel().text());
        if (!matcher.find()) throw new IOException("malformed date: " + ds.titel().text());
        String date = matcher.group();
        day.setDate(DateTimeFormat.forPattern("EEEE, dd. MMMM yyyy")
                .withLocale(Locale.GERMAN).parseLocalDate(date));

        matcher = lastChangePattern.matcher(ds.datum().text());
        if (!matcher.find()) throw new IOException("malformed date: " + ds.datum().text());
        String lastChange = matcher.group();
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
                Set<String> classNames = th.classNames();
                for (String className : classNames) {
                    if (className.contains("thplan") || className.contains("thlplan")) {
                        columnTypes.add(className.replace("thplan", "")
                                .replace("thlplan", "")
                                .replace("_scheuler",
                                        ""));  // sic! -> http://www.hildebrand-gymnasium.de/index.php/klasse-5.html
                        break;
                    }
                }
            }
        }

        for (Element aktion : ds.aktionen()) {
            Substitution substitution = new Substitution();
            String course = null;
            int i = 0;
            for (Element info : aktion.children()) {
                String value = info.text().replace("\u00a0", "");
                if (value.equals("---")) {
                    i++;
                    continue;
                }
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
                        String subject = subjectAndCourse(course, value);
                        if (columnTypes != null && columnTypes.contains("vfach")) {
                            substitution.setPreviousSubject(subject);
                        } else {
                            substitution.setSubject(subject);
                        }
                        break;
                    case "vfach":
                        substitution.setSubject(subjectAndCourse(course, value));
                    case "lehrer":
                        Matcher bracesMatcher = bracesPattern.matcher(value);
                        if (bracesMatcher.matches()) value = bracesMatcher.group(1);
                        substitution.setTeachers(new HashSet<>(Arrays.asList(value.split(", "))));
                        break;
                    case "raum":
                        if (columnTypes != null && columnTypes.contains("vraum")) {
                            substitution.setPreviousRoom(value);
                        } else {
                            substitution.setRoom(value);
                        }
                        break;
                    case "vraum":
                        substitution.setRoom(value);
                    case "info":
                        handleDescription(substitution, value);
                        break;
                }
                i++;
            }
            if (substitution.getType() == null) substitution.setType("Vertretung");
            substitution.setColor(colorProvider.getColor(substitution.getType()));
            if (course != null && substitution.getSubject() == null) {
                substitution.setSubject(course);
            }
            day.addSubstitution(substitution);
        }

        return day;
    }

    static void handleDescription(Substitution substitution, String value) {
        if (value == null) return;

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
            substitution.setType("Entfall");
            substitution.setPreviousSubject(cancelMatcher.group(1));
            substitution.setPreviousTeacher(cancelMatcher.group(2));
        } else if (delayMatcher.matches()) {
            substitution.setType("Verlegung");
            substitution.setPreviousSubject(delayMatcher.group(1));
            substitution.setPreviousTeacher(delayMatcher.group(2));
            substitution.setDesc(delayMatcher.group(3));
        } else if (selfMatcher.matches()) {
            substitution.setType("selbst.");
            if (!selfMatcher.group(1).isEmpty()) substitution.setDesc(selfMatcher.group(1));
        } else if (value.equals("fällt aus") || value.equals("Klausur") || value.equals("Aufg.")) {
            substitution.setType(value);
        } else {
            substitution.setDesc(value);
        }
    }

    @NotNull private String subjectAndCourse(String course, String subject) {
        StringBuilder subjectBuilder = new StringBuilder();
        subjectBuilder.append(subject);
        if (course != null) {
            subjectBuilder.append(" ").append(course);
        }
        return subjectBuilder.toString();
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
