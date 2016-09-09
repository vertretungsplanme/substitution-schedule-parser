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
import org.joda.time.LocalDateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <dt><code>baseurl</code> (String, required)</dt>
 * <dd>The URL of the home page of the substitution schedule with the selection of classes is found.</dd>
 *
 * <dt><code>encoding</code> (String, required)</dt>
 * <dd>The charset of the XML files. It's probably either UTF-8 or ISO-8859-1.</dd>
 *
 * <dt><code>classes</code> (Array of Strings, required)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules
 * and those specified in {@link UntisCommonParser}.
 */
public class UntisSubstitutionParser extends UntisCommonParser {

    private static final String PARAM_BASEURL = "baseurl";
    private static final String PARAM_ENCODING = "encoding";
    private String baseUrl;
    private JSONObject data;

    public UntisSubstitutionParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        try {
            data = scheduleData.getData();
            baseUrl = data.getString(PARAM_BASEURL);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException,
            JSONException, CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);

        String encoding = data.getString(PARAM_ENCODING);
        Document doc = Jsoup.parse(this.httpGet(baseUrl, encoding));
        Elements classes = doc.select("td a");

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        String lastChange = doc.select("td[align=right]:not(:has(b))").text();
        LocalDateTime lastChangeDate = ParserUtils.parseDateTime(lastChange);

        Pattern dayPattern = Pattern.compile("\\d\\d?.\\d\\d?. / \\w+");

        for (Element klasse : classes) {
            Document classDoc = Jsoup.parse(httpGet(baseUrl.substring(0, baseUrl.lastIndexOf("/"))
                    + "/" + klasse.attr("href"), encoding));

            int dateColumn = -1;
            JSONArray columns = data.getJSONArray("columns");
            for (int i = 0; i < columns.length(); i++) {
                if (columns.getString(i).equals("date")) {
                    dateColumn = i;
                    break;
                }
            }

            Element table = classDoc.select("table[rules=all]").first();

            if (dateColumn == -1) {
                SubstitutionScheduleDay day = new SubstitutionScheduleDay();
                day.setLastChangeString(lastChange);
                day.setLastChange(lastChangeDate);
                String title = classDoc.select("font[size=5], font[size=4]").text();
                Matcher matcher = dayPattern.matcher(title);
                if (matcher.find()) {
                    String date = matcher.group();
                    day.setDateString(date);
                    day.setDate(ParserUtils.parseDate(date));
                }
                parseSubstitutionScheduleTable(table, data, day);
                v.addDay(day);
            } else {
                for (Element line : table
                        .select("tr.list.odd:not(:has(td.inline_header)), "
                                + "tr.list.even:not(:has(td.inline_header)), "
                                + "tr:has(td[align=center]:has(font[color]))")) {
                    SubstitutionScheduleDay day = null;
                    String date = line.select("td").get(dateColumn).text().trim();
                    for (SubstitutionScheduleDay search : v.getDays()) {
                        if (search.getDateString().equals(date)) {
                            day = search;
                            break;
                        }
                    }
                    if (day == null) {
                        day = new SubstitutionScheduleDay();
                        day.setDateString(date);
                        day.setDate(ParserUtils.parseDate(date));
                        day.setLastChangeString(lastChange);
                        day.setLastChange(lastChangeDate);
                        v.addDay(day);
                    }
                    parseSubstitutionScheduleTable(line, data, day);
                }
            }
        }
        v.setWebsite(baseUrl);
        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());
        return v;
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }

}
