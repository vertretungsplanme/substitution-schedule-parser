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
import org.joda.time.format.DateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.*;

/**
 * Parser for substitution schedules in HTML format created by the Turbo-Vertretung software.
 * <p>
 * Example:
 * <a href="http://www.goethe-schule.de/ANBgkqhkiG9w0BAQEFAAOCAQ8goethe-schuleAMIIBCgKCAQEAnkFG3NUV4779/internet1.html">Goethe-Schule Bochum</a>
 * <p>
 * This parser can be accessed using <code>"turbovertretung"</code> for {@link SubstitutionScheduleData#setApi(String)}.
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
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules.
 */
public class TurboVertretungParser extends BaseParser {
    private static final String PARAM_URLS = "urls";
    private static final String PARAM_ENCODING = "encoding";
    private JSONObject data;

    public TurboVertretungParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore); //

        JSONArray urls = data.getJSONArray(PARAM_URLS);
        String encoding = data.optString(PARAM_ENCODING, null);
        List<Document> docs = new ArrayList<>();

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        for (int i = 0; i < urls.length(); i++) {
            String url;
            if (urls.get(i) instanceof JSONObject) {
                // backwards compatibility
                url = urls.getJSONObject(i).getString("url");
            } else {
                url = urls.getString(i);
            }
            loadUrl(url, encoding, docs);
        }

        for (Document doc : docs) {
            String html = doc.body().html();
            String[] parts = html.split("<p class=\"Titel\">");
            for (int i = 1; i < parts.length; i++) {
                Document partDoc = Jsoup.parse("<p class=\"Titel\">" + parts[i]);

                if (partDoc.select(".Trennlinie").size() == 2) {
                    Element nextElement = partDoc.select(".Trennlinie").first().nextElementSibling();
                    if ("Trennlinie".equals(nextElement.className())) {
                        // empty part. This can happen when the maximum page size is extremely small...
                        continue;
                    }
                }


                parseTurboVertretungDay(v, partDoc);
            }
        }

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());

        return v;
    }

    private void parseTurboVertretungDay(SubstitutionSchedule v, Document doc) {
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();

        String date = doc.select(".Titel").text().replaceFirst("Vertretungsplan( für)? ", "");
        day.setDate(DateTimeFormat.forPattern("EEEE, d. MMMM yyyy").withLocale(Locale.GERMAN).parseLocalDate(date));

        String lastChange = doc.select(".Stand").text();
        day.setLastChange(ParserUtils.parseDateTime(lastChange));

        if (doc.text().contains("Kein Vertretungsplan")) {
            v.addDay(day);
            return;
        }

        if (!doc.select(".LehrerFrueher").isEmpty()) {
            day.addMessage(doc.select(".LehrerFrueherLabel").text() + "\n" + doc.select(".LehrerFrueher").text());
        }
        if (!doc.select(".LehrerVerplant").isEmpty()) {
            day.addMessage(doc.select(".LehrerVerplantLabel").text() + "\n" + doc.select(".LehrerVerplant").text());
        }
        if (!doc.select(".Abwesenheiten-Klassen").isEmpty()) {
            day.addMessage(doc.select(".Abwesenheiten-KlassenLabel").text() + "\n" +
                    doc.select(".Abwesenheiten-Klassen").text());
        }
        if (!doc.select(".Abwesenheiten").isEmpty()) {
            day.addMessage(doc.select(".AbwesenheitenLabel").text() + "\n" +
                    doc.select(".Abwesenheiten").text());
        }

        Element table = doc.select("table").first();
        for (Element row : table.select("tr:has(td)")) {
            if (!row.select(".Klasseleer").isEmpty()) continue;

            Substitution substitution = new Substitution();
            substitution.setLesson(row.select(query("Stunde")).text());
            substitution.setPreviousTeacher(row.select(query("Lehrer")).text());
            substitution.setTeacher(row.select(query("Vertretung")).text());
            substitution.setClasses(new HashSet<>(Arrays.asList(row.select(query("Klasse")).text().split(" "))));
            substitution.setSubject(row.select(query("Fach")).text());
            substitution.setDesc(row.select(query("Anmerkung")).text());
            substitution.setRoom(row.select(query("Raum")).text());

            String type = recognizeType(row.select(query("Anmerkung")).text());
            if (type == null) type = "Vertretung";
            substitution.setType(type);
            substitution.setColor(colorProvider.getColor(type));

            day.addSubstitution(substitution);
        }

        v.addDay(day);
    }

    @NotNull
    private String query(String css) {
        return "." + css + ", ." + css + "Neu";
    }

    private void loadUrl(String url, String encoding, List<Document> docs)
            throws IOException, CredentialInvalidException {
        String html = httpGet(url, encoding).replace("&nbsp;", "");
        Document doc = Jsoup.parse(html);
        docs.add(doc);
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException {
        return getClassesFromJson();
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }
}
