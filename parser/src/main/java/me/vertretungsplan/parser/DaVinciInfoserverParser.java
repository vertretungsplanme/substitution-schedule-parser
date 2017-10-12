/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
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
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.http.client.HttpResponseException;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Parser for substitution schedules created by the <a href="http://davinci.stueber.de/">DaVinci</a> software and
 * hosted on their <a href="https://davinci.stueber.de/davinci-infoserver.php">InfoServer</a>.
 * <p>
 * This parser can be accessed using <code>"davinci-infoserver"</code> for
 * {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>baseurl</code> (String, required)</dt>
 * <dd>The URL of the DaVinci infoserver instance (without /daVinciIS.dll at the end)</dd>
 * </dl>
 */
public class DaVinciInfoserverParser extends BaseParser {
    public static final String PARAM_BASEURL = "baseurl";
    private static final DateTimeFormatter timeFmt = DateTimeFormat.forPattern("HHmm");

    DaVinciInfoserverParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
    }

    @Override public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        final String baseurl = scheduleData.getData().getString(PARAM_BASEURL);
        String url = baseurl + "/daVinciIS.dll?content=json";

        if (credential != null) {
            UserPasswordCredential upCred = (UserPasswordCredential) credential;
            url += "&username=" + URLEncoder.encode(upCred.getUsername(), "UTF-8") + "&key=" + DigestUtils.md5Hex
                    (upCred.getPassword());
        }
        try {
            final JSONObject response = new JSONObject(httpGet(url, "UTF-8"));
            return parseDaVinciJson(response, scheduleData);
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == 900) {
                throw new CredentialInvalidException();
            } else {
                throw e;
            }
        }
    }

    private static SubstitutionSchedule parseDaVinciJson(JSONObject json, SubstitutionScheduleData scheduleData) throws
            JSONException {
        SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);
        LocalDateTime lastChange = null;

        final JSONObject result = json.getJSONObject("result");
        final JSONArray classes = result.getJSONArray("classes");
        for (int i = 0; i < classes.length(); i++) {
            schedule.getClasses().add(classes.getJSONObject(i).getString("code"));
        }

        final JSONArray teachers = result.getJSONArray("teachers");
        for (int i = 0; i < teachers.length(); i++) {
            schedule.getTeachers().add(teachers.getJSONObject(i).getString("code"));
        }

        Timeslots timeslots = new Timeslots(result.getJSONArray("timeframes"));

        final JSONArray lessons = result.getJSONObject("displaySchedule").getJSONArray("lessonTimes");
        for (int i = 0; i < lessons.length(); i++) {
            final JSONObject lesson = lessons.getJSONObject(i);
            if (!lesson.has("changes")) continue;
            final JSONObject changes = lesson.getJSONObject("changes");

            if (changes.getInt("changeType") == 6) {
                // Absenz
                continue;
            }

            Substitution substitution = new Substitution();
            final LocalDateTime modified = DateTimeFormat.forPattern("yyyyMMddHHmmSS").parseLocalDateTime(
                    changes.getString("modified"));
            if (lastChange == null || lastChange.isBefore(modified)) {
                lastChange = modified;
            }

            if (changes.has("absentTeacherCodes")) {
                substitution.setPreviousTeachers(new HashSet<>(toStringList(
                        changes.getJSONArray("absentTeacherCodes"))));
            } else if (lesson.has("teacherCodes")) {
                substitution.setPreviousTeachers(new HashSet<>(toStringList(lesson.getJSONArray("teacherCodes"))));
            }
            if (changes.has("newTeacherCodes")) {
                substitution.setTeachers(new HashSet<>(toStringList(changes.getJSONArray("newTeacherCodes"))));
            } else if (lesson.has("teacherCodes")) {
                substitution.setTeachers(new HashSet<>(toStringList(lesson.getJSONArray("teacherCodes"))));
            }
            substitution.setClasses(new HashSet<>(toStringList(lesson.getJSONArray("classCodes"))));

            substitution.setLesson(timeslots.getLesson(
                    timeFmt.parseLocalTime(lesson.getString("startTime")),
                    timeFmt.parseLocalTime(lesson.getString("endTime"))));

            if (changes.has("lessonTitle")) {
                substitution.setSubject(changes.getString("lessonTitle"));
            } else if (lesson.has("courseTitle")) {
                substitution.setSubject(lesson.getString("courseTitle"));
            }

            substitution.setType(changes.optString("caption", "Vertretung"));
            substitution.setDesc(changes.optString("message", null));

            if (substitution.getType() == null) {
                substitution.setType("Vertretung");
            }

            for (String dateStr : toStringList(lesson.getJSONArray("dates"))) {
                LocalDate date = DateTimeFormat.forPattern("yyyyMMdd").parseLocalDate(dateStr);
                SubstitutionScheduleDay day = getDayByDate(date, schedule);
                day.addSubstitution(substitution);
            }

        }

        schedule.setLastChange(lastChange);
        return schedule;
    }

    private static SubstitutionScheduleDay getDayByDate(LocalDate date, SubstitutionSchedule schedule) {
        for (SubstitutionScheduleDay day : schedule.getDays()) {
            if (day.getDate().equals(date)) {
                return day;
            }
        }
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();
        day.setDate(date);
        schedule.addDay(day);
        return day;
    }

    private static List<String> toStringList(JSONArray array) throws JSONException {
        final List<String> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(array.getString(i));
        }
        return list;
    }

    @Override public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        return null;
    }

    @Override public List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException {
        return null;
    }

    @Override public boolean isPersonal() {
        return true;
    }

    private static class Timeslots {
        private HashMap<LocalTime, Timeslot> startsMap;
        private HashMap<LocalTime, Timeslot> endsMap;

        public Timeslots(JSONArray json) throws JSONException {
            startsMap = new HashMap<>();
            endsMap = new HashMap<>();

            for (int i = 0; i < json.length(); i++) {
                final JSONArray timeslots = json.getJSONObject(i).getJSONArray("timeslots");
                for (int j = 0; j < timeslots.length(); j++) {
                    final JSONObject timeslot = timeslots.getJSONObject(j);
                    final LocalTime start = timeFmt.parseLocalTime(timeslot.getString("startTime"));
                    final LocalTime end = timeFmt.parseLocalTime(timeslot.getString("endTime"));
                    final Timeslot slot = new Timeslot(start, end, timeslot.getString("label"));
                    startsMap.put(start, slot);
                    endsMap.put(end, slot);
                }
            }
        }

        public String getLesson(LocalTime start, LocalTime end) {
            final Timeslot startSlot = startsMap.get(start);
            final Timeslot endSlot = endsMap.get(end);
            if (startSlot.equals(endSlot)) {
                return startSlot.caption;
            } else {
                return startSlot.caption + " - " + endSlot.caption;
            }
        }
    }

    private static class Timeslot {
        LocalTime start;
        LocalTime end;
        String caption;

        Timeslot(LocalTime start, LocalTime end, String caption) {
            this.start = start;
            this.end = end;
            this.caption = caption;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;

            if (o == null || getClass() != o.getClass()) return false;

            Timeslot timeslot = (Timeslot) o;

            return new EqualsBuilder()
                    .append(start, timeslot.start)
                    .append(end, timeslot.end)
                    .append(caption, timeslot.caption)
                    .isEquals();
        }

        @Override public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(start)
                    .append(end)
                    .append(caption)
                    .toHashCode();
        }
    }
}
