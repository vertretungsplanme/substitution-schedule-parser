/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.ParserUtil;
import me.vertretungsplan.exception.CredentialInvalidException;
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
 * <h4>Configuration parameters</h4>
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

        for (int i = 0; i < MAX_DAYS; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            String dateStr = DateTimeFormat.forPattern("yyyyMMdd").print(date);
            String url = baseurl + "mobdaten/PlanKl" + dateStr + ".xml?_=" + System.currentTimeMillis();
            try {
                String xml = httpGet(url, "UTF-8");
                Document doc = Jsoup.parse(xml, url, Parser.xmlParser());
                if (doc.select("kopf datei").text().equals("PlanKl" + dateStr + ".xml")) {
                    docs.add(doc);
                }
            } catch (HttpResponseException e) {
                if (e.getStatusCode() != 404 && e.getStatusCode() != 300) throw e;
            }
        }

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);
        for (Document doc:docs) {
            v.addDay(parseDay(doc, colorProvider));
        }

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());
        v.setWebsite(baseurl + "plankl.html");

        return v;
    }

    static SubstitutionScheduleDay parseDay(Document doc, ColorProvider colorProvider) {
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
                subst.setTeachers(split(text(lesson.select("Te"))));
                subst.setRoom(text(lesson.select("Ra")));
                IndiwareParser.handleDescription(subst, text(lesson.select("If")));
                if (subst.getType() == null) subst.setType("Vertretung");
                subst.setColor(colorProvider.getColor(subst.getType()));
                subst.setClasses(classes);
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
        String baseurl = data.getString(PARAM_BASEURL) + "/";

        for (int i = -4; i < MAX_DAYS; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            String dateStr = DateTimeFormat.forPattern("yyyyMMdd").print(date);
            String url = baseurl + "mobdaten/PlanKl" + dateStr + ".xml?_=" + System.currentTimeMillis();
            try {
                String xml = httpGet(url, "UTF-8");
                Document doc = Jsoup.parse(xml, url, Parser.xmlParser());

                List<String> classes = new ArrayList<>();
                for (Element klasse:doc.select("Klassen > Kl")) {
                    classes.add(klasse.select("Kurz").first().text());
                }
                return classes;
            } catch (HttpResponseException e) {
                if (e.getStatusCode() != 404 && e.getStatusCode() != 300) throw e;
            }
        }
        return new ArrayList<>();
    }

    @Override public List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException {
        return null;
    }
}
