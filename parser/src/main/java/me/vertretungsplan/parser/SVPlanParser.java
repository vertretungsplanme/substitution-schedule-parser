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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parser für Vertretungspläne der Software svPlan
 * z.B: http://www.ratsschule.de/Vplan/PH_heute.htm
 */
public class SVPlanParser extends BaseParser {

    private JSONObject data;

    public SVPlanParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException,
            CredentialInvalidException {
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
            if (doc.select(".svp").size() > 0) {
                for (Element svp:doc.select(".svp")) {
                    parseSvPlanDay(v, svp, doc);
                }
            } else {
                parseSvPlanDay(v, doc, doc);
            }
        }

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());

        return v;
    }

    private void parseSvPlanDay(SubstitutionSchedule v, Element svp, Document doc) throws IOException {
        if (svp.select(".svp-tabelle, table:has(.Klasse)").size() > 0) {
            SubstitutionScheduleDay day = new SubstitutionScheduleDay();
            String date = "Unbekanntes Datum";
            if (svp.select(".svp-plandatum-heute, .svp-plandatum-morgen, .Titel").size() > 0) {
                date = svp.select(".svp-plandatum-heute, .svp-plandatum-morgen, .Titel").text().trim();
            } else if (doc.title().startsWith("Vertretungsplan für "))
                date = doc.title().substring("Vertretungsplan für ".length());
            date = date.replaceAll("\\s+", " ");
            day.setDateString(date);
            day.setDate(ParserUtils.parseDate(date));
            if (svp.select(".svp-uploaddatum, .Stand").size() > 0) {
                String lastChange = svp.select(".svp-uploaddatum, .Stand").text().replace("Aktualisierung: ", "")
                        .replace("Stand: ", "");
                day.setLastChangeString(lastChange);
                day.setLastChange(ParserUtils.parseDateTime(lastChange));
            }

            Elements rows = svp.select(".svp-tabelle tr, table:has(.Klasse) tr");
            String lastLesson = "";
            for (Element row : rows) {
                if (row.hasClass("svp-header") || (row.parent().select(".gerade").size() > 0 &&
                        row.select(".ungerade").size() == 0))
                    continue;

                Substitution substitution = new Substitution();
                List<String> affectedClasses = new ArrayList<>();

                for (Element column : row.select("td")) {
                    if (!hasData(column.text())) {
                        continue;
                    }
                    String type = column.className();
                    if (type.startsWith("svp-stunde") || type.startsWith("Stunde")) {
                        substitution.setLesson(column.text());
                        lastLesson = column.text();
                    } else if (type.startsWith("svp-klasse") || type.startsWith("Klasse"))
                        substitution.getClasses().addAll(Arrays.asList(column.text().split(", ")));
                    else if (type.startsWith("svp-esfehlt") || type.startsWith("Lehrer"))
                        substitution.setPreviousTeacher(column.text());
                    else if (type.startsWith("svp-esvertritt") || type.startsWith("Vertretung"))
                        substitution.setTeacher(column.text());
                    else if (type.startsWith("svp-fach") || type.startsWith("Fach"))
                        substitution.setSubject(column.text());
                    else if (type.startsWith("svp-bemerkung") || type.startsWith("Anmerkung")) {
                        substitution.setDesc(column.text());
                        String recognizedType = recognizeType(column.text());
                        substitution.setType(recognizedType);
                        substitution.setColor(colorProvider.getColor(recognizedType));
                    } else if (type.startsWith("svp-raum") || type.startsWith("Raum"))
                        substitution.setRoom(column.text());

                    if (substitution.getLesson() == null)
                        substitution.setLesson(lastLesson);
                }

                if (substitution.getType() == null) {
                    substitution.setType("Vertretung");
                }

                day.addSubstitution(substitution);
            }

            if (svp.select("h2:contains(Mitteilungen)").size() > 0) {
                Element h2 = svp.select("h2:contains(Mitteilungen)").first();
                Element sibling = h2.nextElementSibling();
                while (sibling != null && sibling.tagName().equals("p")) {
                    for (String nachricht : TextNode.createFromEncoded(sibling.html(), null).getWholeText().split("<br />\\s*<br />")) {
                        if (hasData(nachricht))
                            day.addMessage(nachricht);
                    }
                    sibling = sibling.nextElementSibling();
                }
            }

            v.addDay(day);
        } else {
            throw new IOException("keine SVPlan-Tabelle gefunden");
        }
    }

    private void loadUrl(String url, String encoding, List<Document> docs) throws IOException {
        String html = httpGet(url, encoding).replace("&nbsp;", "");
        Document doc = Jsoup.parse(html);
        docs.add(doc);
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
