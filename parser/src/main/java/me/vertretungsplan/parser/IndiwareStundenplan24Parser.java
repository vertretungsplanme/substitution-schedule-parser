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
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.http.client.HttpResponseException;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IndiwareStundenplan24Parser extends IndiwareParser {

    private static final int MAX_DAYS = 7;
    private static final String ENCODING = "UTF-8";

    public IndiwareStundenplan24Parser(SubstitutionScheduleData scheduleData,
                                       CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {

        if (credential == null || !(credential instanceof UserPasswordCredential)) {
            throw new IOException("no login");
        }
        String login = ((UserPasswordCredential) credential).getUsername();
        String password = ((UserPasswordCredential) credential).getPassword();
        executor.auth(login, password);

        String schoolNumber = data.getString("schoolNumber");
        List<Document> docs = new ArrayList<>();

        for (int i = 0; i < MAX_DAYS; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            String dateStr = DateTimeFormat.forPattern("yyyyMMdd").print(date);
            String url = "http://www.stundenplan24.de/" + schoolNumber + "/vplan/vdaten/VplanKl" + dateStr +
                    ".xml?_=" + System.currentTimeMillis();
            try {
                String xml = httpGet(url, ENCODING);
                Document doc = Jsoup.parse(xml, url, Parser.xmlParser());
                if (doc.select("kopf datei").text().equals("VplanKl" + dateStr + ".xml")) {
                    docs.add(doc);
                }
            } catch (HttpResponseException e) {
                if (e.getStatusCode() != 404 && e.getStatusCode() != 300) throw e;
            }
        }

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        for (Document doc : docs) {
            v.addDay(parseIndiwareDay(doc));
        }

        v.setWebsite("http://www.stundenplan24.de/" + schoolNumber + "/vplan/");

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());

        return v;
    }
}
