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
import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for substitution schedules in HTML format created by the <a href="http://untis.de/">Untis</a> software
 * using the "Vertretungsplanung" layout.
 * <p>
 * Example: <a href="http://www.jkg-stuttgart.de/jkgdata/vertretungsplan/sa3.htm">JKG Stuttgart</a>
 * <p>
 * This parser can be accessed using <code>"untis-substitution"</code> for
 * {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>baseurl</code> (String, required if <code>urls</code> not specified)</dt>
 * <dd>The URL of the home page of the substitution schedule where the selection of classes is found.</dd>
 *
 * <dt><code>urls</code> (Array of strings, required if <code>baseurl</code> not specified)</dt>
 * <dd>The URLs of the home pages of the substitution schedule where the selection of classes is found.</dd>
 *
 * <dt><code>encoding</code> (String, required)</dt>
 * <dd>The charset of the XML files. It's probably either UTF-8 or ISO-8859-1.</dd>
 *
 * <dt><code>columns</code> (Array of Strings, required)</dt>
 * <dd>As defined in {@link UntisCommonParser}, but additionally supports <code>"columns"</code> as a column type.</dd>
 *
 * <dt><code>classes</code> (Array of Strings, required)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 *
 * <dt><code>website</code> (String, recommended)</dt>
 * <dd>The URL of a website where the substitution schedule can be seen online</dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules
 * and those specified in {@link UntisCommonParser}.
 */
public class UntisSubstitutionParser extends UntisCommonParser {

    private static final String PARAM_BASEURL = "baseurl";
    private static final String PARAM_URLS = "urls";
    private static final String PARAM_ENCODING = "encoding";
    private static final String PARAM_WEBSITE = "website";
    private List<String> urls;
    private JSONObject data;

    public UntisSubstitutionParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        try {
            data = scheduleData.getData();
            urls = new ArrayList<>();
            if (data.has(PARAM_BASEURL)) {
                urls.add(data.getString(PARAM_BASEURL));
            } else {
                JSONArray urlsArray = data.getJSONArray(PARAM_URLS);
                for (int i = 0; i < urlsArray.length(); i++) {
                    urls.add(urlsArray.getString(i));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException,
            JSONException, CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);

        String encoding = data.getString(PARAM_ENCODING);
        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        int successfulSchedules = 0;
        HttpResponseException lastExceptionSchedule = null;
        for (String baseUrl:urls) {
            try {
                Document doc = Jsoup.parse(this.httpGet(baseUrl, encoding));
                Elements classes = doc.select("td a");

                String lastChange = doc.select("td[align=right]:not(:has(b))").text();

                int successfulClasses = 0;
                HttpResponseException lastExceptionClass = null;
                for (Element klasse : classes) {
                    try {
                        Document classDoc = Jsoup.parse(httpGet(baseUrl.substring(0, baseUrl.lastIndexOf("/"))
                                + "/" + klasse.attr("href"), encoding));

                        parseSubstitutionTable(v, lastChange, classDoc);
                        successfulClasses++;
                    } catch (HttpResponseException e) {
                        lastExceptionClass = e;
                    }
                }
                if (successfulClasses == 0 && lastExceptionClass != null) {
                    throw lastExceptionClass;
                }
                successfulSchedules ++;
            } catch (HttpResponseException e) {
                lastExceptionSchedule = e;
            }
        }
        if (successfulSchedules == 0 && lastExceptionSchedule != null) {
            throw lastExceptionSchedule;
        }
        if (data.has(PARAM_WEBSITE)) {
            v.setWebsite(data.getString(PARAM_WEBSITE));
        } else {
            v.setWebsite(urls.get(0));
        }
        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());
        return v;
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }

}
