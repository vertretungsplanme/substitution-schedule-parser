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

public class TurboVertretungParser extends BaseParser {
    private JSONObject data;

    public TurboVertretungParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException, CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore); //

        JSONArray urls = data.getJSONArray("urls");
        String encoding = data.getString("encoding");
        List<Document> docs = new ArrayList<>();

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        for (int i = 0; i < urls.length(); i++) {
            JSONObject url = urls.getJSONObject(i);
            loadUrl(url.getString("url"), encoding, docs);
        }

        for (Document doc : docs) {
            String html = doc.body().html();
            String[] parts = html.split("<p class=\"Titel\">");
            for (int i = 1; i < parts.length; i++) {
                Document partDoc = Jsoup.parse("<p class=\"Titel\">" + parts[i]);
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

        String lastChange = doc.select(".Stand").text().replace("Stand: ", "");
        day.setLastChange(DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss").withLocale(Locale.GERMAN).parseLocalDateTime(lastChange));

        if (doc.select(".LehrerFrueher").size() > 0) {
            day.addMessage(doc.select(".LehrerFrueherLabel").text() + "\n" + doc.select(".LehrerFrueher").text());
        }
        if (doc.select(".LehrerVerplant").size() > 0) {
            day.addMessage(doc.select(".LehrerVerplantLabel").text() + "\n" + doc.select(".LehrerVerplant").text());
        }
        if (doc.select(".Abwesenheiten-Klassen").size() > 0) {
            day.addMessage(doc.select(".Abwesenheiten-KlassenLabel").text() + "\n" + doc.select(".Abwesenheiten-Klassen").text());
        }

        Element table = doc.select("table").first();
        for (Element row : table.select("tr:has(td)")) {
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

    private void loadUrl(String url, String encoding, List<Document> docs) throws IOException {
        String html = httpGet(url, encoding).replace("&nbsp;", "");
        Document doc = Jsoup.parse(html);
        docs.add(doc);
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException {
        JSONArray classesJson = data.getJSONArray("classes");
        List<String> classes = new ArrayList<>();
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
