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
 * Generischer Parser für Vertretungspläne im CSV-Format
 * Beispiel: http://czg.noxxi.de/vp.pl?/csv
 */
public class CSVParser extends BaseParser {

    private JSONObject data;

    public CSVParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException,
            CredentialInvalidException {
        new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);
        String url = data.getString("url");
        String response = executor.execute(Request.Get(url)).returnContent().asString();

        return parseCSV(response);
    }

    @NotNull
    SubstitutionSchedule parseCSV(String response) throws JSONException, IOException {
        SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);

        String[] lines = response.split("\n");

        String separator = data.getString("separator");
        for (int i = data.optInt("skipLines", 0); i<lines.length; i++) {
            String[] columns = lines[i].split(separator);
            Substitution v = new Substitution();
            String dayName = null;
            String stand = "";
            int j = 0;
            for (String column:columns) {
                String type = data.getJSONArray("columns")
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

        if (scheduleData.getData().has("website")) {
            schedule.setWebsite(scheduleData.getData().getString("website"));
        }

        schedule.setClasses(getAllClasses());
        schedule.setTeachers(getAllTeachers());

        return schedule;
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException {
        if (data.has("classesUrl")) {
            String url = data.getString("classesUrl");
            String response = executor.execute(Request.Get(url)).returnContent().asString();
            List<String> classes = new ArrayList<>();
            for (String string:response.split("\n")) {
                classes.add(string.trim());
            }
            return classes;
        } else {
            JSONArray classesJson = data.getJSONArray("classes");
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
