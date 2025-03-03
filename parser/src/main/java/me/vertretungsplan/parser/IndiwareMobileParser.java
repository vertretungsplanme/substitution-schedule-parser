/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.AdditionalInfo;
import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.apache.http.client.HttpResponseException;
import org.jetbrains.annotations.NotNull;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Parser for the mobile version of substitution schedules created with the <a href="http://indiware.de/">Indiware</a>
 * software.
 * <p>
 * This parser can be accessed using <code>"indiware-mobile"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * # Configuration parameters
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>baseurl</code> (Array of Strings, required)</dt>
 * <dd>Base URL of the Indiware mobile schedule.</dd>
 *
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules.
 */
public class IndiwareMobileParser extends BaseParser {
    private static final String PARAM_BASEURL = "baseurl";
    private static final int MAX_DAYS = 7;
    private JSONObject data;

    public IndiwareMobileParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    @Override public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);

        String baseurl = data.getString(PARAM_BASEURL) + "/";

        List<Document> docs = new ArrayList<>();

        HttpResponseException lastException = null;
        for (int i = 0; i < MAX_DAYS; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            String dateStr = DateTimeFormat.forPattern("yyyyMMdd").print(date);
            String filePrefix = scheduleData.getType() == SubstitutionSchedule.Type.TEACHER ? "PlanLe" : "PlanKl";
            String url = baseurl + "mobdaten/" + filePrefix + dateStr + "" + ".xml?_=" + System.currentTimeMillis();
            try {
                String xml = httpGet(url, "UTF-8");
                Document doc = Jsoup.parse(xml, url, Parser.xmlParser());
                if (doc.select("kopf datei").text().equals(filePrefix + dateStr + ".xml")) {
                    docs.add(doc);
                }
            } catch (HttpResponseException e) {
                lastException = e;
            }
        }
        if (docs.size() == 0 && lastException != null) {
            throw lastException;
        }

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        final AdditionalInfo info = new AdditionalInfo();
        info.setTitle("Achtung");
        info.setText("Für diese Schule können wir den Vertretungsplan aufgrund Beschränkungen seitens des " +
                "Herstellers von Indiware stundenplan24.de nur noch jede Stunde aktualisieren. Push-Benachrichtigungen werden " +
                "dementsprechend auch nur verspätet ankommen. Bitte achte auf das oben angegebene " +
                "Aktualisierungsdatum!");
        info.setFromSchedule(true);
        v.getAdditionalInfos().add(info);

        for (Document doc:docs) {
            v.addDay(parseDay(doc, colorProvider, scheduleData));
        }

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());
        v.setWebsite(baseurl + "plankl.html");

        return v;
    }

    static SubstitutionScheduleDay parseDay(Document doc, ColorProvider colorProvider, SubstitutionScheduleData scheduleData) {
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();

        day.setDate(ParserUtils.parseDate(doc.select("Kopf > DatumPlan").text()));
        day.setLastChange(ParserUtils.parseDateTime(doc.select("Kopf > Zeitstempel").text()));

        for (Element klasse:doc.select("Klassen > Kl")) {
            String className = klasse.select("Kurz").first().text();

            HashSet<String> classes = new HashSet<>();
            classes.add(className);
            for (Element lesson:klasse.select("Pl > Std")) {
                if (lesson.select("If:not(:empty), Le[LeAe], Ra[RaAe], Fa[FaAe]").size() == 0) {
                    continue;
                }

                Substitution subst = new Substitution();
                subst.setLesson(text(lesson.select("St")));
                if (scheduleData.getType() == SubstitutionSchedule.Type.STUDENT) {
                    subst.setTeachers(split(text(lesson.select("Le"))));
                    subst.setClasses(classes);
                } else {
                    subst.setClasses(split(text(lesson.select("Le"))));
                    subst.setTeachers(classes);
                }
                subst.setSubject(text(lesson.select("Fa")));
                subst.setRoom(text(lesson.select("Ra")));
                IndiwareParser.handleDescription(subst, text(lesson.select("If")), scheduleData.getType() == SubstitutionSchedule.Type.TEACHER);
                if (subst.getType() == null) subst.setType("Vertretung");
                subst.setColor(colorProvider.getColor(subst.getType()));
                day.addSubstitution(subst);
            }
        }

        for (Element info:doc.select("ZusatzInfo > ZiZeile")) {
            day.getMessages().add(info.text());
        }
        return day;
    }

    @NotNull
    private static HashSet<String> split(String text) {
        if (text != null) {
            return new HashSet<>(Arrays.asList(text.split(",")));
        } else {
            return new HashSet<>();
        }
    }

    private static String text(Elements elem) {
        String text = elem.text().replace("&nbsp;", "").trim();
        if (!text.isEmpty()) {
            return text;
        } else {
            return null;
        }
    }

    @Override public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        if (scheduleData.getType() == SubstitutionSchedule.Type.STUDENT) {
            return parseClasses("PlanKl");
        } else {
            return new ArrayList<>();
        }
    }

    @NotNull private List<String> parseClasses(String filePrefix)
            throws JSONException, IOException, CredentialInvalidException {
        String baseurl = data.getString(PARAM_BASEURL) + "/";
        HttpResponseException lastException = null;
        for (int i = -4; i < MAX_DAYS; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            String dateStr = DateTimeFormat.forPattern("yyyyMMdd").print(date);
            String url = baseurl + "mobdaten/" + filePrefix + dateStr + ".xml?_=" + System.currentTimeMillis();
            try {
                String xml = httpGet(url, "UTF-8");
                Document doc = Jsoup.parse(xml, url, Parser.xmlParser());

                List<String> classes = new ArrayList<>();
                for (Element klasse:doc.select("Klassen > Kl")) {
                    classes.add(klasse.select("Kurz").first().text());
                }
                return classes;
            } catch (HttpResponseException e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            throw lastException;
        } else {
            return new ArrayList<>();
        }
    }

    @Override public List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException {
        if (scheduleData.getType() == SubstitutionSchedule.Type.TEACHER) {
            return parseClasses("PlanLe");
        } else {
            return null;
        }
    }
}
