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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser für Untis-Vertretungspläne mit dem Monitor-Stundenplan-Layout
 * Beispiel: Lornsenschule Schleswig http://vertretung.lornsenschule.de/schueler/subst_001.htm
 * Funktioniert mit vielen anderen Schulen mit unterschiedlichen Layouts.
 */
public class UntisMonitorParser extends UntisCommonParser {

    private static final int MAX_RECURSION_DEPTH = 30;
    private String loginResponse;

    public UntisMonitorParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
    }

    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException, CredentialInvalidException {
        loginResponse = new LoginHandler(scheduleData, credential, cookieProvider)
                .handleLoginWithResponse(executor, cookieStore);

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        JSONArray urls = scheduleData.getData().getJSONArray("urls");
        String encoding = scheduleData.getData().getString("encoding");
        List<Document> docs = new ArrayList<Document>();

        for (int i = 0; i < urls.length(); i++) {
            JSONObject url = urls.getJSONObject(i);
            loadUrl(url.getString("url"), encoding, url.getBoolean("following"), docs);
        }

        for (Document doc : docs) {
            if (doc.title().contains("Untis")) {
                SubstitutionScheduleDay day = parseMonitorVertretungsplanTag(doc, scheduleData.getData());
                v.addDay(day);
            } else if (scheduleData.getData().has("embeddedContentSelector")) {
                for (Element part : doc.select(scheduleData.getData().getString("embeddedContentSelector"))) {
                    SubstitutionScheduleDay day = parseMonitorVertretungsplanTag(part, scheduleData.getData());
                    v.addDay(day);
                }
            } else {
                //Error
            }

            if (scheduleData.getData().has("lastChangeSelector")
                    && doc.select(scheduleData.getData().getString("lastChangeSelector")).size() > 0) {
                String text = doc.select(scheduleData.getData().getString("lastChangeSelector")).first().text();
                String lastChange;
                Pattern pattern = Pattern.compile("\\d\\d\\.\\d\\d\\.\\d\\d\\d\\d,? \\d\\d:\\d\\d");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    lastChange = matcher.group();
                } else {
                    lastChange = text;
                }
                v.setLastChangeString(lastChange);
                v.setLastChange(ParserUtils.parseDateTime(lastChange));
            }
        }

        if (scheduleData.getData().has("website")) {
            v.setWebsite(scheduleData.getData().getString("website"));
        } else if (urls.length() == 1) {
            v.setWebsite(urls.getString(0));
        }

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());

        return v;
    }

    private void loadUrl(String url, String encoding, boolean following, List<Document> docs, String startUrl,
                         int recursionDepth) throws IOException, CredentialInvalidException {
        String html;
        if (url.equals("loginResponse")) {
            html = loginResponse;
        } else {
            html = httpGet(url, encoding).replace("&nbsp;", "");
        }
        Document doc = Jsoup.parse(html);
        doc.setBaseUri(url);

        if (doc.select(".mon_title").size() == 0) {
            // We have a problem - there seems to be no substitution schedule. Maybe it is hiding
            // inside a frame?
            if (doc.select("frameset frame[name").size() > 0) {
                for (Element frame : doc.select("frameset frame")) {
                    if (frame.attr("src").matches(".*subst_\\d\\d\\d.html?") && recursionDepth < MAX_RECURSION_DEPTH) {
                        String frameUrl = frame.absUrl("src");
                        loadUrl(frame.absUrl("src"), encoding, following, docs, frameUrl, recursionDepth + 1);
                    }
                }
            } else if (doc.text().contains("registriert")) {
                throw new CredentialInvalidException();
            } else {
                throw new IOException("Could not find .mon-title, seems like there is no Untis " +
                        "schedule here");
            }
        } else {
            docs.add(doc);
            if (following && doc.select("meta[http-equiv=refresh]").size() > 0) {
                Element meta = doc.select("meta[http-equiv=refresh]").first();
                String attr = meta.attr("content").toLowerCase();
                String redirectUrl = url.substring(0, url.lastIndexOf("/") + 1) + attr.substring(attr.indexOf("url=") + 4);
                if (!redirectUrl.equals(startUrl) && recursionDepth < MAX_RECURSION_DEPTH) {
                    loadUrl(redirectUrl, encoding, true, docs, startUrl, recursionDepth + 1);
                }
            }
        }
    }

    private void loadUrl(String url, String encoding, boolean following, List<Document> docs) throws IOException, CredentialInvalidException {
        loadUrl(url, encoding, following, docs, url, 0);
    }

    public List<String> getAllTeachers() {
        return null;
    }
}
