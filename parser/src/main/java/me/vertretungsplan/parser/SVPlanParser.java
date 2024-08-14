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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for substitution schedules in HTML format created by the <a href="http://www.haneke.de/SvPlan.html">svPlan</a>
 * software.
 * <p>
 * Example: <a href="http://www.ratsschule.de/Vplan/PH_heute.htm">Ratsschule Melle</a>
 * <p>
 * This parser can be accessed using <code>"svplan"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>urls</code> (Array of Strings, required)</dt>
 * <dd>The URLs of the HTML files of the schedule. There is one file for each day.</dd>
 *
 * <dt><code>encoding</code> (String, required)</dt>
 * <dd>The charset of the HTML files. It's probably either UTF-8 or ISO-8859-1.</dd>
 *
 * <dt><code>classes</code> (Array of Strings, required)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 *
 * <dt><code>classSeparator</code> (String, optional, Default: <code>", "</code>)</dt>
 * <dd>The string with which multiple classes are separated.</dd>
 *
 * <dt><code>excludeTeachers</code> (Boolean, optional, Default: <code>false</code>)</dt>
 * <dd>Don't show teachers on the schedule.</dd>
 *
 * <dt><code>repeatClass</code> (Boolean, optional, Default: <code>true</code>)</dt>
 * <dd>Whether an empty class column means that there is no class associated with this substitution (false) or that
 * the class from the previous row should be used (true).</dd>
 *
 * <dt><code>forceAllPages</code> (Boolean, optional, default: false)</dt>
 * <dd>If one page was loaded successfully, but additional pages failed due to HTTP error codes, don't ignore
 * these errors
 * </dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules.
 */
public class SVPlanParser extends BaseParser {

    private static final String PARAM_URLS = "urls";
    private static final String PARAM_ENCODING = "encoding";
    private static final String PARAM_CLASS_SEPARATOR = "classSeparator";
    private static final String PARAM_EXCLUDE_TEACHERS = "excludeTeachers";
    private static final String PARAM_REPEAT_CLASS = "repeatClass";
    private static final String PARAM_FORCE_ALL_PAGES = "forceAllPages";
    private JSONObject data;

    public SVPlanParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException,
            CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore); //

        JSONArray urls = data.getJSONArray(PARAM_URLS);
        String encoding = data.optString(PARAM_ENCODING, null);
        List<Document> docs = new ArrayList<>();

        int successfulSchedules = 0;
        IOException lastException = null;
        for (int i = 0; i < urls.length(); i++) {
            String url;
            if (urls.get(i) instanceof JSONObject) {
                // backwards compatibility
                final JSONObject obj = urls.getJSONObject(i);
                url = obj.getString("url");
                if (obj.has("postData")) {
                    JSONObject postParams = obj.getJSONObject("postData");
                    List<NameValuePair> nvps = new ArrayList<>();
                    for (String name : JSONObject.getNames(postParams)) {
                        String value = postParams.getString(name);
                        nvps.add(new BasicNameValuePair(name, value));
                    }
                    try {
                        docs.add(Jsoup.parse(httpPost(url, encoding, nvps).replace("&nbsp;", "")));
                        successfulSchedules++;
                    } catch (IOException e) {
                        if (data.optBoolean(PARAM_FORCE_ALL_PAGES)) {
                            throw e;
                        } else {
                            lastException = e;
                        }
                    }
                } else {
                    try {
                        docs.add(Jsoup.parse(httpGet(url, encoding).replace("&nbsp;", "")));
                        successfulSchedules++;
                    } catch (IOException e) {
                        if (data.optBoolean(PARAM_FORCE_ALL_PAGES)) {
                            throw e;
                        } else {
                            lastException = e;
                        }
                    }
                }
            } else {
                url = urls.getString(i);
                try {
                    docs.add(Jsoup.parse(httpGet(url, encoding).replace("&nbsp;", "")));
                    successfulSchedules++;
                } catch (IOException e) {
                    if (data.optBoolean(PARAM_FORCE_ALL_PAGES)) {
                        throw e;
                    } else {
                        lastException = e;
                    }
                }
            }
        }
        if (successfulSchedules == 0 && lastException != null) {
            throw lastException;
        }

        SubstitutionSchedule v = parseSVPlanSchedule(docs);
        return v;
    }

    @NotNull
    SubstitutionSchedule parseSVPlanSchedule(List<Document> docs) throws IOException, JSONException {
        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        for (Document doc : docs) {
            if (!doc.select(".svp, .scheduler").isEmpty()) {
                for (Element svp : doc.select(".svp, .scheduler")) {
                    parseSvPlanDay(v, svp, doc);
                }
            } else if (!doc.select(".Trennlinie").isEmpty()) {
                Element div = new Element(Tag.valueOf("div"), "");
                for (Node node : doc.body().childNodesCopy()) {
                    if (node instanceof Element && ((Element) node).hasClass("Trennlinie")
                            && !div.select("table").isEmpty()) {
                        parseSvPlanDay(v, div, doc);
                        div = new Element(Tag.valueOf("div"), "");
                    } else {
                        div.appendChild(node);
                    }
                }
                parseSvPlanDay(v, div, doc);
            } else {
                parseSvPlanDay(v, doc, doc);
            }
        }

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());
        return v;
    }

    private void parseSvPlanDay(SubstitutionSchedule v, Element svp, Document doc) throws IOException, JSONException {
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();
        if ((!svp.select(".svp-plandatum-heute, .svp-plandatum-morgen, .Titel").isEmpty() || doc.title()
                .startsWith("Vertretungsplan für "))) {
            setDate(svp, doc, day);
            final Elements tables = svp.select(".svp-tabelle, table:has(.Klasse)");
            if (!tables.isEmpty()) {
                Iterator<Element> iter = tables.iterator();
                while (iter.hasNext()) {
                    Element table = iter.next();
                    Element sibling = table.previousElementSibling();
                    if (!table.hasClass("svp-tabelle") &&
                            table.select("> tbody > tr > td.Klasse").isEmpty() &&
                            table.select("> tbody > tr > td.KlasseNeu").isEmpty()
                            || sibling != null && sibling.hasClass("AufsichtTitel")) {
                        iter.remove();
                    }
                }

                Elements rows = tables.select("tr");
                String lastLesson = "";
                String lastClass = "";
                for (Element row : rows) {
                    if ((!doc.select(".svp-header").isEmpty() && row.hasClass("svp-header"))
                            || !row.select("th").isEmpty() || row.text().trim().isEmpty()) {
                        continue;
                    }

                    Substitution substitution = new Substitution();

                    for (Element column : row.select("td")) {
                        String type = column.className();
                        if (!hasData(column.text())) {
                            if ((type.startsWith("svp-stunde") || type.startsWith("Stunde")) && hasData(lastLesson)) {
                                substitution.setLesson(lastLesson);
                            } else if ((type.startsWith("svp-klasse") || type.startsWith("Klasse"))
                                    && hasData(lastClass) && data.optBoolean(PARAM_REPEAT_CLASS, true)) {
                                substitution.getClasses().addAll(getClasses(lastClass));
                            }
                            continue;
                        }
                        if (type.startsWith("svp-stunde") || type.startsWith("Stunde")) {
                            substitution.setLesson(column.text());
                            lastLesson = column.text();
                        } else if (type.startsWith("svp-klasse") || type.startsWith("Klasse")) {
                            substitution.getClasses().addAll(getClasses(column.text()));
                            lastClass = column.text();
                        } else if (type.startsWith("svp-esfehlt") || type.startsWith("Lehrer")) {
                            if (!data.optBoolean(PARAM_EXCLUDE_TEACHERS)) {
                                substitution.setPreviousTeacher(column.text());
                            }
                        } else if (type.startsWith("svp-esvertritt") || type.startsWith("Vertretung")) {
                            if (!data.optBoolean(PARAM_EXCLUDE_TEACHERS)) {
                                substitution.setTeacher(column.text().replaceAll(" \\+$", ""));
                            }
                        } else if (type.startsWith("svp-fach") || type.startsWith("Fach")) {
                            substitution.setSubject(column.text());
                        } else if (type.startsWith("svp-bemerkung") || type.startsWith("Anmerkung")) {
                            substitution.setDesc(column.text());
                            String recognizedType = recognizeType(column.text());
                            substitution.setType(recognizedType);
                            substitution.setColor(colorProvider.getColor(recognizedType));
                        } else if (type.startsWith("svp-raum") || type.startsWith("Raum")) {
                            substitution.setRoom(column.text());
                        }
                    }

                    if (substitution.getType() == null) {
                        substitution.setType("Vertretung");
                        substitution.setColor(colorProvider.getColor("Vertretung"));
                    }

                    day.addSubstitution(substitution);
                }
            }
            if (!svp.select(".LehrerVerplant").isEmpty()) {
                day.addMessage("<b>Verplante Lehrer:</b> " + svp.select(".LehrerVerplant").text());
            }
            if (!svp.select(".Abwesenheiten").isEmpty()) {
                day.addMessage("<b>Abwesenheiten:</b> " + svp.select(".Abwesenheiten").text());
            }

            if (!svp.select("h2:contains(Mitteilungen)").isEmpty()) {
                Element h2 = svp.select("h2:contains(Mitteilungen)").first();
                Element sibling = h2.nextElementSibling();
                while (sibling != null && sibling.tagName().equals("p")) {
                    for (String nachricht : TextNode.createFromEncoded(sibling.html(), null).getWholeText()
                            .split("<br />\\s*<br />")) {
                        if (hasData(nachricht)) day.addMessage(nachricht);
                    }
                    sibling = sibling.nextElementSibling();
                }
            } else if (!svp.select(".Mitteilungen").isEmpty()) {
                for (Element p : svp.select(".Mitteilungen")) {
                    for (String nachricht : TextNode.createFromEncoded(p.html(), null).getWholeText()
                            .split("<br />\\s*<br />")) {
                        if (hasData(nachricht)) day.addMessage(nachricht);
                    }
                }
            }

            Elements aufsichtTable = svp.select(".svp-pausenaufsicht, .AufsichtTitel + table");
            if (!aufsichtTable.isEmpty()) {
                Elements rows = aufsichtTable.select("tr");
                String lastTime = "";
                for (Element row : rows) {
                    if (row.hasClass("svp-aufs-header") || row.select("td").isEmpty()) continue;
                    Substitution substitution = new Substitution();
                    substitution.setType("Pausenaufsicht");
                    substitution.setColor(colorProvider.getColor("Pausenaufsicht"));

                    for (Element column : row.select("td")) {
                        String type = column.className();
                        if (!hasData(column.text())) {
                            if ((type.startsWith("svp-aufs-zeit")) && hasData(lastTime)) {
                                substitution.setLesson(lastTime);
                            }
                            continue;
                        }
                        if (type.startsWith("svp-aufs-zeit") || type.startsWith("Stunde")) {
                            substitution.setLesson(column.text());
                            lastTime = column.text();
                        } else if (type.startsWith("svp-aufs-esfehlt") || type.startsWith("Lehrer")) {
                            if (!data.optBoolean(PARAM_EXCLUDE_TEACHERS)) {
                                substitution.setPreviousTeacher(column.text());
                            }
                        } else if (type.startsWith("svp-esvertritt") || type.startsWith("Vertretung")) {
                            if (!data.optBoolean(PARAM_EXCLUDE_TEACHERS)) {
                                substitution.setTeacher(column.text().replaceAll(" \\+$", ""));
                            }
                        } else if (type.startsWith("svp-aufs-ort") || type.startsWith("Raum")) {
                            substitution.setRoom(column.text());
                        } else if (type.startsWith("Anmerkung")) {
                            substitution.setDesc(column.text());
                        }
                    }

                    day.addSubstitution(substitution);
                }
            }
            v.addDay(day);
        } else {
            throw new IOException("keine SVPlan-Tabelle gefunden");
        }
    }

    @NotNull List<String> getClasses(String text) throws JSONException {
        // Detect things like "7"
        Pattern singlePattern = Pattern.compile("\\[(\\d+)\\]");
        Matcher singleMatcher = singlePattern.matcher(text);

        // Detect things like "5-12"
        Pattern rangePattern = Pattern.compile("\\[(\\d+) ?- ?(\\d+)\\]");
        Matcher rangeMatcher = rangePattern.matcher(text);

        Pattern pattern2 = Pattern.compile("^(\\d+).*");

        if (rangeMatcher.matches()) {
            List<String> classes = new ArrayList<>();
            int min = Integer.parseInt(rangeMatcher.group(1));
            int max = Integer.parseInt(rangeMatcher.group(2));
            for (String klasse : getAllClasses()) {
                Matcher matcher2 = pattern2.matcher(klasse);
                if (matcher2.matches()) {
                    int num = Integer.parseInt(matcher2.group(1));
                    if (min <= num && num <= max) classes.add(klasse);
                }
            }
            return classes;
        } else if (singleMatcher.matches()) {
            List<String> classes = new ArrayList<>();
            int grade = Integer.parseInt(singleMatcher.group(1));
            for (String klasse : getAllClasses()) {
                Matcher matcher2 = pattern2.matcher(klasse);
                if (matcher2.matches() && grade == Integer.parseInt(matcher2.group(1))) {
                    classes.add(klasse);
                }
            }
            return classes;
        } else {
            return Arrays.asList(text.split(data.optString(PARAM_CLASS_SEPARATOR, ", ")));
        }
    }

    private void setDate(Element svp, Document doc, SubstitutionScheduleDay day) {
        String date = "Unbekanntes Datum";
        if (!svp.select(".svp-plandatum-heute, .svp-plandatum-morgen, .Titel").isEmpty()) {
            date = svp.select(".svp-plandatum-heute, .svp-plandatum-morgen, .Titel").first().text();
        } else if (doc.title().startsWith("Vertretungsplan für ")) {
            date = doc.title().substring("Vertretungsplan für ".length());
        }
        date = date.replaceAll("\\s+", " ").trim();

        Pattern pattern = Pattern.compile("[^\\s]+, (?:den )?\\d+. [^\\s]+ \\d+");
        Matcher matcher = pattern.matcher(date);
        if (matcher.find()) {
            date = matcher.group();
        }

        day.setDateString(date);
        day.setDate(ParserUtils.parseDate(date));
        if (!svp.select(".svp-uploaddatum, .Stand").isEmpty()) {
            String lastChange = svp.select(".svp-uploaddatum, .Stand").text().replace("Aktualisierung: ", "")
                    .replace("Stand: ", "");
            day.setLastChangeString(lastChange);
            day.setLastChange(ParserUtils.parseDateTime(lastChange));
        }
    }

    private void loadUrl(String url, String encoding, List<Document> docs)
            throws IOException, CredentialInvalidException {

    }

    public List<String> getAllClasses() throws JSONException {
        return getClassesFromJson();
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }

    private boolean hasData(String text) {
        return !text.trim().isEmpty() && !text.trim().equals("---");
    }
}
