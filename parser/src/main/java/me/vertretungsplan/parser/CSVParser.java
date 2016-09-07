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
import org.apache.http.client.fluent.Request;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic parser for substitution schedules in CSV format.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 *     <dt><code>url</code> (String, required)</dt>
 *     <dd>The url of the CSV file to be fetched</dd>
 *
 *     <dt><code>separator</code> (String, required)</dt>
 *     <dd>The separator used in the CSV file (such as <code>","</code>, <code>";"</code> or <code>"\t"</code>)</dd>
 *
 *     <dt><code>columns</code> (Array of Strings, required)</dt>
 *     <dd>The order of columns used in the CSV file. Entries can be: <code>"lesson", "subject",
 *     "previousSubject", "type", "type-entfall", "room", "previousRoom", "teacher", "previousTeacher", desc",
 *     "desc-type", "class", "day", "stand"</code></dd>
 *
 *     <dt><code>classes</code> (Array of Strings, required if <code>classesUrl</code> not specified)</dt>
 *     <dd>The list of all classes, as they can appear in the schedule</dd>
 *
 *     <dt><code>website</code> (String, recommended)</dt>
 *     <dd>The URL of a website where the substitution schedule can be seen online</dd>
 *
 *     <dt><code>skipLines</code> (Integer, optional)</dt>
 *     <dd>The number of lines to skip at the beginning of the CSV file. Default: <code>0</code></dd>
 *
 *     <dt><code>classesUrl</code> (String, optional)</dt>
 *     <dd>The URL of an additional CSV file containing the classes, one per line</dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler}.
 */
public class CSVParser extends BaseParser {

    private static final String PARAM_SEPARATOR = "separator";
    private static final String PARAM_SKIP_LINES = "skipLines";
    private static final String PARAM_COLUMNS = "columns";
    private static final String PARAM_WEBSITE = "website";
    private static final String PARAM_CLASSES_URL = "classesUrl";
    private static final String PARAM_CLASSES = "classes";
    private static final String PARAM_URL = "url";
    private JSONObject data;

    public CSVParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException,
            CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);
        String url = data.getString(PARAM_URL);
        String response = executor.execute(Request.Get(url)).returnContent().asString();

        return parseCSV(response);
    }

    @NotNull
    SubstitutionSchedule parseCSV(String response) throws JSONException, IOException {
        SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);

        String[] lines = response.split("\n");

        String separator = data.getString(PARAM_SEPARATOR);
        for (int i = data.optInt(PARAM_SKIP_LINES, 0); i < lines.length; i++) {
            String[] columns = lines[i].split(separator);
            Substitution v = new Substitution();
            String dayName = null;
            String stand = "";
            int j = 0;
            for (String column:columns) {
                String type = data.getJSONArray(PARAM_COLUMNS)
                        .getString(j);
                switch (type) {
                    case "lesson":
                        v.setLesson(column);
                        break;
                    case "subject":
                        v.setSubject(column);
                        break;
                    case "previousSubject":
                        v.setPreviousSubject(column);
                        break;
                    case "type":
                        v.setType(column);
                        v.setColor(colorProvider.getColor(column));
                        break;
                    case "type-entfall":
                        if (column.equals("x")) {
                            v.setType("Entfall");
                            v.setColor(colorProvider.getColor("Entfall"));
                        } else {
                            v.setType("Vertretung");
                            v.setColor(colorProvider.getColor("Vertretung"));
                        }
                        break;
                    case "room":
                        v.setRoom(column);
                        break;
                    case "teacher":
                        v.setTeacher(column);
                        break;
                    case "previousTeacher":
                        v.setPreviousTeacher(column);
                        break;
                    case "desc":
                        v.setDesc(column);
                        break;
                    case "desc-type":
                        v.setDesc(column);
                        String recognizedType = recognizeType(column);
                        v.setType(recognizedType);
                        v.setColor(colorProvider.getColor(recognizedType));
                        break;
                    case "previousRoom":
                        v.setPreviousRoom(column);
                        break;
                    case "class":
                        v.getClasses().add(getClassName(column, data));
                        break;
                    case "day":
                        dayName = column;
                        break;
                    case "stand":
                        stand = column;
                        break;
                }
                j++;
            }
            if (v.getType() == null) {
                v.setType("Vertretung");
                v.setColor(colorProvider.getColor("Vertretung"));
            }

            if (dayName != null) {
                SubstitutionScheduleDay day = new SubstitutionScheduleDay();
                day.setDateString(dayName);
                day.setDate(ParserUtils.parseDate(dayName));
                day.setLastChangeString(stand);
                day.setLastChange(ParserUtils.parseDateTime(stand));
                day.addSubstitution(v);
                schedule.addDay(day);
            }
        }

        if (scheduleData.getData().has(PARAM_WEBSITE)) {
            schedule.setWebsite(scheduleData.getData().getString(PARAM_WEBSITE));
        }

        schedule.setClasses(getAllClasses());
        schedule.setTeachers(getAllTeachers());

        return schedule;
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException {
        if (data.has(PARAM_CLASSES_URL)) {
            String url = data.getString(PARAM_CLASSES_URL);
            String response = executor.execute(Request.Get(url)).returnContent().asString();
            List<String> classes = new ArrayList<>();
            for (String string:response.split("\n")) {
                classes.add(string.trim());
            }
            return classes;
        } else {
            JSONArray classesJson = data.getJSONArray(PARAM_CLASSES);
            List<String> classes = new ArrayList<>();
            for (int i = 0; i < classesJson.length(); i++) {
                classes.add(classesJson.getString(i));
            }
            return classes;
        }
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }
}
