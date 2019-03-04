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
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for substitution schedules in XML format created by the <a href="http://indiware.de/">Indiware</a>
 * software and hosted on <a href="https://www.stundenplan24.de/">Stundenplan24.de</a>. Schools only providing the
 * mobile version ("Indiware mobil") of the schedule instead of the desktop version ("Vertretungsplan") are currently
 * not supported. The parser also supports schedules in the same format hosted on different URLs.
 * <p>
 * This parser can be accessed using <code>"stundenplan24"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>schoolNumber</code> (String, required if <code>baseurl</code> not specified)</dt>
 * <dd>The 8-digit school number used to access the schedule. It can be found in the URL.</dd>
 *
 * <dl>
 * <dt><code>baseurl</code> (String, required if <code>schoolNumber</code> not specified)</dt>
 * <dd>Schedule hosted on a custom URL. The URL normally ends with "/vplan" (without slash at the end).</dd>
 *
 * <dt><code>classes</code> (Array of Strings, required)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 * </dl>
 *
 * When specifying <code>schoolNumber</code>, you have to use a
 * {@link me.vertretungsplan.objects.authentication.UserPasswordAuthenticationData}
 * because all schedules on Stundenplan24.de seem to be protected by a login. Schedules on custom URLs may use
 * different kinds of login, in that case the parameters from {@link LoginHandler} are supported.
 */
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

        String baseurl;
        boolean isTeacher = scheduleData.getType() == SubstitutionSchedule.Type.TEACHER;
        if (data.has("schoolNumber")) {
            baseurl = "https://www.stundenplan24.de/" + data.getString("schoolNumber") +
                    (isTeacher ? "/vplanle/" : "/vplan/");
            if (credential == null || !(credential instanceof UserPasswordCredential)) {
                throw new IOException("no login");
            }
            String login = ((UserPasswordCredential) credential).getUsername();
            String password = ((UserPasswordCredential) credential).getPassword();
            executor.auth(login, password);
        } else {
            baseurl = data.getString("baseurl") + "/";
            new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);
        }

        List<Document> docs = new ArrayList<>();

        for (int i = 0; i < MAX_DAYS; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            String dateStr = DateTimeFormat.forPattern("yyyyMMdd").print(date);
            String suffix = isTeacher ? "Le" : "Kl";
            String url = baseurl + "vdaten/Vplan" + suffix + dateStr + ".xml?_=" + System.currentTimeMillis();
            try {
                String xml = httpGet(url, ENCODING);
                Document doc = Jsoup.parse(xml, url, Parser.xmlParser());
                if (doc.select("kopf datei").text().equals("Vplan" + suffix + dateStr + ".xml")) {
                    docs.add(doc);
                }
            } catch (HttpResponseException e) {
                if (e.getStatusCode() != 404 && e.getStatusCode() != 300) throw e;
            }
        }

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        for (Document doc : docs) {
            v.addDay(parseIndiwareDay(doc, false));
        }

        v.setWebsite(baseurl);

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());

        return v;
    }
}
