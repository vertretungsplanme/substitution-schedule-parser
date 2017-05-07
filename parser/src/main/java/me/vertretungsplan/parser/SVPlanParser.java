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
import java.util.List;

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
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules.
 */
public class SVPlanParser extends BaseParser {

    private static final String PARAM_URLS = "urls";
    private static final String PARAM_ENCODING = "encoding";
    private static final String PARAM_CLASS_SEPARATOR = "classSeparator";
    private static final String PARAM_EXCLUDE_TEACHERS = "excludeTeachers";
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
                    docs.add(Jsoup.parse(httpPost(url, encoding, nvps).replace("&nbsp;", "")));
                } else {
                    docs.add(Jsoup.parse(httpGet(url, encoding).replace("&nbsp;", "")));
                }
            } else {
                url = urls.getString(i);
                docs.add(Jsoup.parse(httpGet(url, encoding).replace("&nbsp;", "")));
            }
        }

        SubstitutionSchedule v = parseSVPlanSchedule(docs);
        return v;
    }

    @NotNull
    SubstitutionSchedule parseSVPlanSchedule(List<Document> docs) throws IOException, JSONException {
        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        for (Document doc : docs) {
            if (doc.select(".svp").size() > 0) {
                for (Element svp : doc.select(".svp")) {
                    parseSvPlanDay(v, svp, doc);
                }
            } else if (doc.select(".Trennlinie").size() > 0) {
                Element div = new Element(Tag.valueOf("div"), "");
                for (Node node : doc.body().childNodesCopy()) {
                    if (node instanceof Element && ((Element) node).hasClass("Trennlinie")
                            && div.select("table").size() > 0) {
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

    private void parseSvPlanDay(SubstitutionSchedule v, Element svp, Document doc) throws IOException {
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();
        if ((svp.select(".svp-plandatum-heute, .svp-plandatum-morgen, .Titel").size() > 0 || doc.title()
                .startsWith("Vertretungsplan für "))) {
            setDate(svp, doc, day);
            if (svp.select(".svp-tabelle, table:has(.Klasse)").size() > 0) {

                Elements rows = svp.select(".svp-tabelle tr, table:has(.Klasse) tr");
                String lastLesson = "";
                String lastClass = "";
                for (Element row : rows) {
                    if ((doc.select(".svp-header").size() > 0 && row.hasClass("svp-header"))
                            || row.select("th").size() > 0 || row.text().trim().equals("")) {
                        continue;
                    }

                    Substitution substitution = new Substitution();

                    for (Element column : row.select("td")) {
                        String type = column.className();
                        if (!hasData(column.text())) {
                            if ((type.startsWith("svp-stunde") || type.startsWith("Stunde")) && hasData(lastLesson)) {
                                substitution.setLesson(lastLesson);
                            } else if ((type.startsWith("svp-klasse") || type.startsWith("Klasse"))
                                    && hasData(lastClass)) {
                                substitution.getClasses().addAll(Arrays.asList(lastClass.split(data.optString
                                        (PARAM_CLASS_SEPARATOR, ", "))));
                            }
                            continue;
                        }
                        if (type.startsWith("svp-stunde") || type.startsWith("Stunde")) {
                            substitution.setLesson(column.text());
                            lastLesson = column.text();
                        } else if (type.startsWith("svp-klasse") || type.startsWith("Klasse")) {
                            substitution.getClasses().addAll(Arrays.asList(column.text().split(data.optString
                                    (PARAM_CLASS_SEPARATOR, ", "))));
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
            if (svp.select(".LehrerVerplant").size() > 0) {
                day.addMessage("<b>Verplante Lehrer:</b> " + svp.select(".LehrerVerplant").text());
            }
            if (svp.select(".Abwesenheiten").size() > 0) {
                day.addMessage("<b>Abwesenheiten:</b> " + svp.select(".Abwesenheiten").text());
            }

            if (svp.select("h2:contains(Mitteilungen)").size() > 0) {
                Element h2 = svp.select("h2:contains(Mitteilungen)").first();
                Element sibling = h2.nextElementSibling();
                while (sibling != null && sibling.tagName().equals("p")) {
                    for (String nachricht : TextNode.createFromEncoded(sibling.html(), null).getWholeText()
                            .split("<br />\\s*<br />")) {
                        if (hasData(nachricht)) day.addMessage(nachricht);
                    }
                    sibling = sibling.nextElementSibling();
                }
            } else if (svp.select(".Mitteilungen").size() > 0) {
                for (Element p : svp.select(".Mitteilungen")) {
                    for (String nachricht : TextNode.createFromEncoded(p.html(), null).getWholeText()
                            .split("<br />\\s*<br />")) {
                        if (hasData(nachricht)) day.addMessage(nachricht);
                    }
                }
            }
            v.addDay(day);
        } else {
            throw new IOException("keine SVPlan-Tabelle gefunden");
        }
    }

    private void setDate(Element svp, Document doc, SubstitutionScheduleDay day) {
        String date = "Unbekanntes Datum";
        if (svp.select(".svp-plandatum-heute, .svp-plandatum-morgen, .Titel").size() > 0) {
            date = svp.select(".svp-plandatum-heute, .svp-plandatum-morgen, .Titel").text().replaceAll
                    ("Vertretungsplan (für )?", "").trim();
        } else if (doc.title().startsWith("Vertretungsplan für ")) {
            date = doc.title().substring("Vertretungsplan für ".length());
        }
        date = date.replaceAll("\\s+", " ");
        day.setDateString(date);
        day.setDate(ParserUtils.parseDate(date));
        if (svp.select(".svp-uploaddatum, .Stand").size() > 0) {
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
        return !text.trim().equals("") && !text.trim().equals("---");
    }
}
