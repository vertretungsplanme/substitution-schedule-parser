/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


package me.vertretungsplan.sample;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import me.vertretungsplan.ParserUtil;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.authentication.AuthenticationData;
import me.vertretungsplan.objects.authentication.NoAuthenticationData;
import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.objects.credential.PasswordCredential;
import me.vertretungsplan.objects.credential.SchoolNumberPasswordCredential;
import me.vertretungsplan.objects.credential.UserPasswordCredential;

public class Sample {
    public static void main(String[] args) throws Exception {
        schoolsFromJson();

        SubstitutionScheduleData data = new SubstitutionScheduleData();
        data.setType(SubstitutionSchedule.Type.STUDENT);
        data.setApi("untis-monitor");
        data.setAuthenticationData(new NoAuthenticationData());
        data.setData(new JSONObject("{\n" +
                "         \"classes\": [\n" +
                "           \"05a\",\"05b\",\"05c\",\"05d\",\"05e\",\"05f\",\"05g\",\n" +
                "           \"06a\",\"06b\",\"06c\",\"06d\",\"06e\",\"06f\",\"06g\",\n" +
                "           \"07a\",\"07b\",\"07c\",\"07d\",\"07e\",\"07f\",\"07g\",\n" +
                "           \"08a\",\"08b\",\"08c\",\"08d\",\"08e\",\"08f\",\"08g\",\n" +
                "           \"09a\",\"09b\",\"09c\",\"09d\",\"09e\",\"09f\",\"09g\",\n" +
                "           \"Ea\",\"Eb\",\"Ec\",\"Ed\",\"Ee\",\"Ef\",\n" +
                "           \"Q1a\",\"Q1b\",\"Q1c\",\"Q1d\",\"Q1e\",\"Q1f\",\"Q1g\",\"Q1h\",\"Q1i\",\"Q1j\",\n" +
                "           \"Q2a\",\"Q2b\",\"Q2c\",\"Q2d\",\"Q2e\",\"Q2f\",\"Q2g\",\"Q2h\",\"Q2i\",\"Q2j\" \n" +
                "        ],\n" +
                "         \"class_in_extra_line\": true,\n" +
                "         \"website\": \"http://vertretung.lornsenschule.de/schueler/subst_001.htm\",\n" +
                "         \"stand_links\": true,\n" +
                "         \"urls\": [\n" +
                "           {\n" +
                "             \"following\": false,\n" +
                "             \"url\": \"http://vertretung.lornsenschule.de/schueler/f1/subst_001.htm\" \n" +
                "          },\n" +
                "           {\n" +
                "             \"following\": false,\n" +
                "             \"url\": \"http://vertretung.lornsenschule.de/schueler/f2/subst_001.htm\" \n" +
                "          } \n" +
                "        ],\n" +
                "         \"encoding\": \"ISO-8859-1\",\n" +
                "         \"columns\": [\n" +
                "           \"lesson\",\"type\",\"subject\",\"previousSubject\",\"room\",\"desc\" \n" +
                "        ] \n" +
                "      }"));
        data.getAdditionalInfos().add("winter-sh");
        SubstitutionSchedule schedule = ParserUtil.parseSubstitutionSchedule(data);
        System.out.println(schedule);
    }

    private static void schoolsFromJson() throws Exception {
        String schoolsJsonPath = "/vertretungsplan.schools.json";
        String credentialsJsonPath = "/vertretungsplan.credentials.json";

        // get schools from json
        InputStream inputStreamSchools = Sample.class.getResourceAsStream(schoolsJsonPath);
        if (inputStreamSchools == null) {
            throw new IOException("Datei nicht gefunden: " + schoolsJsonPath);
        }
        String jsonSchoolsString = new String(inputStreamSchools.readAllBytes(), StandardCharsets.UTF_8);
        JSONArray schools =  new JSONArray(jsonSchoolsString);

        // get credentails from json
        InputStream inputStreamCredentials = Sample.class.getResourceAsStream(credentialsJsonPath);
        if (inputStreamCredentials == null) {
            throw new IOException("Datei nicht gefunden: " + credentialsJsonPath);
        }
        String jsonCredentialsString = new String(inputStreamCredentials.readAllBytes(), StandardCharsets.UTF_8);
        JSONArray credentials =  new JSONArray(jsonCredentialsString);

        Map<String, JSONObject> credentialsMap = new HashMap<>();
        for (int i = 0; i < credentials.length(); i++) {
            JSONObject credential = credentials.getJSONObject(i);
            if (credential.getBoolean("valid")) {
                String schoolId = credential.getString("schoolId");
                String scheduleId = credential.getString("scheduleId");
                String key = schoolId + "_" + scheduleId;
                if (!credentialsMap.containsKey(key)) {
                    credentialsMap.put(key,credential);
                }
            }
        }

        // loop over schools
        for (int i = 0; i < schools.length(); i++) {
            JSONObject school = schools.getJSONObject(i);
            String schoolId = school.getString("_id");
            String schoolName = school.getString("name");
            JSONArray schedules = school.getJSONArray("schedules");

            // loop over schedules
            for (int j = 0; j < schedules.length(); j++) {
                JSONObject schedule = schedules.getJSONObject(j);
                String visibility = schedule.getString("visibility");

                if ("EVERYONE".equals(visibility)) {
                    String api = schedule.getString("api");
                    String type = schedule.getString("type");
                    String schduleId = schedule.getString("id");
                    String key = schoolId + "_" + schduleId;
                    String authClass = schedule.getJSONObject("authenticationData").getString("@class");
                    String className = "me.vertretungsplan.objects.authentication" + authClass;

                    System.out.println(schoolId + " " + schoolName + " " + api + " " + type + " " + authClass);
                    SubstitutionScheduleData data = new SubstitutionScheduleData();

                    data.setType(SubstitutionSchedule.Type.valueOf(type));
                    data.setApi(api);

                    Class<?> clazz;
                    clazz = Class.forName(className);
                    data.setAuthenticationData((AuthenticationData) clazz.getDeclaredConstructor().newInstance());

                    JSONObject dataJson = schedule.getJSONObject("data");
                    data.setData(dataJson);

                    try {
                        SubstitutionSchedule scheduleResult;
                        if (".NoAuthenticationData".equals(authClass)) {
                            scheduleResult = ParserUtil.parseSubstitutionSchedule(data);
                        } else {
                            Credential cred;
                            if (!credentialsMap.containsKey(key)) {
                                System.out.println("no cred found");
                                throw new Exception("no cred found");
                            }
                            JSONObject credJson = credentialsMap.get(key);
                            if (null == authClass) {
                                System.out.println("authClass is null");
                                throw new Exception("authClass is null");
                            } else switch (authClass) {
                                case ".PasswordAuthenticationData" -> cred = new PasswordCredential(credJson.getString("password"));
                                case ".UserPasswordAuthenticationData" -> cred = new UserPasswordCredential(credJson.getString("password"), credJson.getString("username"));
                                case ".SchoolNumberPasswordAuthenticationData" -> {
                                    String schoolNumber = schedule.getJSONObject("authenticationData").getString("schoolNumber");
                                    cred = new SchoolNumberPasswordCredential(schoolNumber);
                                }
                                default -> {
                                    System.out.println("authClass is " + authClass);
                                    cred = new UserPasswordCredential(credJson.getString("password"), credJson.getString("username"));
                                }
                            }
                            scheduleResult = ParserUtil.parseSubstitutionSchedule(data, cred);
                        }
                        System.out.println(scheduleResult);   
                    } catch (Exception e) {
                        System.out.println("skip Schedule Error");
                    }
                }
            }
            System.out.println();
        }
    }
}
