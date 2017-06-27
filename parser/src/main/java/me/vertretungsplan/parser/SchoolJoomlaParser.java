/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.*;
import me.vertretungsplan.objects.credential.PasswordCredential;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.jetbrains.annotations.NotNull;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Parser for substitution schedules in HTML format served using SchoolJoomla.
 * <p>
 * This parser can be accessed using <code>"schoolJoomla"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>baseurl</code> (String, required)</dt>
 * <dd>The URL of the server where SchoolJoomla is installed (without a trailing slash). Usually, this is just the
 * domain. The parser automatically appends things like "/components/com_school_mobile".</dd>
 * </dl>
 */
public class SchoolJoomlaParser extends BaseParser {
    private static final String PARAM_BASEURL = "baseurl";

    SchoolJoomlaParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
    }

    @Override public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        JSONObject config = getConfiguration();
        JSONObject data = getData();

        SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);
        schedule.setLastChange(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseLocalDateTime(data.getString
                ("lastupdate")));

        if (scheduleData.getType() == SubstitutionSchedule.Type.STUDENT) {
            JSONObject substs = data.getJSONObject("vertretungsplan").getJSONObject("schuelervertretungen");
            Iterator datesIter = substs.keys();
            while (datesIter.hasNext()) {
                String dateStr = (String) datesIter.next();
                if (dateStr.equals("elementscount")) continue;

                LocalDate date = DateTimeFormat.forPattern("yyyy-MM-dd").parseLocalDate(dateStr);

                SubstitutionScheduleDay day = new SubstitutionScheduleDay();
                day.setDate(date);

                final JSONObject dayJson = substs.getJSONObject(dateStr);
                Iterator classesIter = dayJson.keys();
                while (classesIter.hasNext()) {
                    String klasse = (String) classesIter.next();
                    if (klasse.equals("elementscount")) continue;

                    Set<String> classes = new HashSet<>();
                    classes.add(klasse);

                    for (int i = 0; i < dayJson.getJSONArray(klasse).length(); i++) {
                        JSONObject subst = dayJson.getJSONArray(klasse).getJSONObject(i);
                        Substitution s = new Substitution();
                        s.setClasses(classes);
                        s.setLesson(subst.getString("stunden"));
                        s.setPreviousSubject(emptyToNull(subst.getString("fach")));
                        s.setSubject(emptyToNull(subst.getString("vfach")));
                        s.setPreviousRoom(emptyToNull(subst.getString("raum")));
                        s.setRoom(emptyToNull(subst.getString("vraum")));
                        s.setPreviousTeacher(emptyToNull(subst.getString("lehrerkuerzel")));
                        s.setTeacher(emptyToNull(subst.getString("verlehrerkuerzel")));
                        s.setDesc(emptyToNull(subst.getString("kommentar")));

                        String art = subst.getString("art");
                        switch (art) {
                            case "V":
                                s.setType("Vertretung");
                                break;
                            case "C":
                                s.setType("Entfall");
                                break;
                            case "R":
                                s.setType("Verlegung");
                                break;
                            default:
                                throw new IOException("unknown: " + art);
                        }
                        s.setColor(colorProvider.getColor(s.getType()));

                        day.addSubstitution(s);
                    }
                }
                schedule.addDay(day);
            }

            final JSONArray ticker = data.getJSONObject("vertretungsplan").getJSONArray("schuelernewsticker");
            if (ticker.length() > 0) {
                final StringBuilder builder = new StringBuilder();
                for (int i = 0; i < ticker.length(); i++) {
                    if (i > 0) builder.append("\n\n");
                    builder.append(ticker.getString(i));
                }

                AdditionalInfo info = new AdditionalInfo();
                info.setTitle("Newsticker");
                info.setText(builder.toString());
                info.setHasInformation(false);
                schedule.addAdditionalInfo(info);
            }
        } else if (scheduleData.getType() == SubstitutionSchedule.Type.TEACHER) {
            throw new IOException("not yet supported");
        }

        schedule.setClasses(getClasses(data));
        schedule.setTeachers(getAllTeachers());

        return schedule;
    }

    private String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }

    private JSONObject getData() throws IOException, CredentialInvalidException, JSONException {
        return executeTask("getAllData");
    }

    private JSONObject getConfiguration() throws IOException, CredentialInvalidException, JSONException {
        return executeTask("getConfiguration");
    }

    @NotNull private JSONObject executeTask(String task) throws JSONException, IOException, CredentialInvalidException {
        String baseurl = scheduleData.getData().getString(PARAM_BASEURL);

        String username = "";
        String password = "";
        if (credential != null) {
            if (credential instanceof UserPasswordCredential) {
                if (scheduleData.getType() != SubstitutionSchedule.Type.TEACHER) {
                    throw new IOException("student schedules only have passwords or no password");
                }
                username = ((UserPasswordCredential) credential).getUsername();
                password = ((UserPasswordCredential) credential).getPassword();
            } else if (credential instanceof PasswordCredential) {
                if (scheduleData.getType() != SubstitutionSchedule.Type.STUDENT) {
                    throw new IOException("teacher schedules need a username");
                }
                password = ((PasswordCredential) credential).getPassword();
            }
        }

        final String json = httpGet(baseurl + "/components/com_school_mobile/wserv/service" +
                ".php?select=&user=" + username + "&pw=" + password + "&task=" + task, "UTF-8");
        final JSONObject data = new JSONObject(json);

        final int error = data.getInt("error");
        if (error != 0 || data.getJSONArray("errors").length() > 0) {
            switch (error) {
                case 12: // wrong teacher password
                case 17: // wrong student password
                    throw new CredentialInvalidException();
                default:
                    throw new IOException(data.getString("error_desc"));
            }
        }

        return data;
    }

    @Override public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        JSONObject config = getConfiguration();
        JSONObject data = getData();
        return getClasses(data);
    }

    @NotNull private List<String> getClasses(JSONObject data) throws JSONException {
        List<String> classes = new ArrayList<>();
        JSONArray klassenjgst = data.getJSONArray("klassenjgst");
        for (int i = 0; i < klassenjgst.length(); i++) {
            classes.add(klassenjgst.getJSONObject(i).getString("name"));
        }
        return classes;
    }

    @Override public List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException {
        return new ArrayList<>();
    }
}
