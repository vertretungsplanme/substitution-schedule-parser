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
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.commons.codec.binary.Base32;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.joda.time.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Parser for substitution schedules hosted on WebUntis.
 * <p>
 * This parser can be accessed using <code>"webuntis"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 * <p>
 * Please bear in mind that WebUntis's API does not allow frequent polling, according to <a href="http://www.grupet
 * .at/phpBB3/viewtopic.php?t=5643">this forum post</a>
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>host</code> (String, required)</dt>
 * <dd>The hostname of the WebUntis server. For schedules hosted by Gruber&Petters, this is `&lt;something&gt;.webuntis
 * .com`.</dd>
 *
 * <dt><code>schoolname</code> (String, required)</dt>
 * <dd>The school name entered into WebUntis for login.</dd>
 *
 * <dt><code>protocol</code> (String, optional, Default: https)</dt>
 * <dd>The protocol used to access WebUntis. The *.webuntis.com servers support HTTPS, self-hosted ones may not.</dd>
 *
 * </dl>
 *
 * Schedules on WebUntis are always protected using a
 * {@link me.vertretungsplan.objects.authentication.UserPasswordAuthenticationData}.
 */
public class WebUntisParser extends BaseParser {
    private static final String PARAM_HOST = "host";
    private static final String PARAM_SCHOOLNAME = "schoolname";
    public static final String PARAM_PROTOCOL = "protocol";
    private final JSONObject data;
    private String sessionId;
    private static final String USERAGENT = "vertretungsplan.me";
    private String sharedSecret;
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("yyyyMMdd");

    WebUntisParser(SubstitutionScheduleData scheduleData,
                   CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    @Override public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        login();
        SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);
        schedule.setLastChange(getLastImport());

        TimeGrid timegrid = new TimeGrid(getTimeGrid());

        JSONArray holidays = getHolidays();
        // find out if there's a holiday currently and if so, also display substitutions after it
        int daysToAdd = 0;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < holidays.length(); i++) {
            LocalDate startDate = DATE_FORMAT.parseLocalDate(String.valueOf(holidays.getJSONObject(i).getInt
                    ("startDate")));
            LocalDate endDate = DATE_FORMAT.parseLocalDate(String.valueOf(holidays.getJSONObject(i).getInt
                    ("endDate")));
            if (!startDate.isAfter(today.plusDays(6)) && !endDate.isBefore(today)) {
                if (startDate.isBefore(today)) {
                    daysToAdd += Days.daysBetween(today, endDate).getDays() + 1;
                } else {
                    daysToAdd += Days.daysBetween(startDate, endDate).getDays() + 2;
                }
            }
        }

        JSONArray substitutions = getSubstitutions(today, today.plusDays(6 + daysToAdd));

        Map<LocalDate, SubstitutionScheduleDay> days = new HashMap<>();

        for (int j = 0; j < substitutions.length(); j++) {
            JSONObject substJson = substitutions.getJSONObject(j);

            Substitution substitution = new Substitution();
            if ("ex".equals(substJson.optString("lstype"))) {
                substitution.setType("Klausur");
            } else {
                substitution.setType(codeToType(substJson.getString("type")));
            }
            substitution.setColor(colorProvider.getColor(substitution.getType()));

            Set<String> cls = new HashSet<>();
            JSONArray classesJson = substJson.getJSONArray("kl");
            for (int k = 0; k < classesJson.length(); k++) {
                cls.add(classesJson.getJSONObject(k).getString("name"));
            }
            substitution.setClasses(cls);

            JSONArray subjectsJson = substJson.getJSONArray("su");
            String subject = null;
            for (int k = 0; k < subjectsJson.length(); k++) {
                JSONObject subjectJson = subjectsJson.getJSONObject(k);
                if (subjectJson.has("name")) {
                    if (subject == null) {
                        subject = subjectJson.getString("name");
                    } else {
                        subject += ", " + subjectJson.getString("name");
                    }
                }
            }
            substitution.setSubject(subject);

            JSONArray roomsJson = substJson.getJSONArray("ro");
            String room = null;
            String previousRoom = null;
            for (int k = 0; k < roomsJson.length(); k++) {
                JSONObject roomJson = roomsJson.getJSONObject(k);
                if (roomJson.has("name")) {
                    if (room == null) {
                        room = roomJson.getString("name");
                    } else {
                        room += ", " + roomJson.getString("name");
                    }
                }
                if (roomJson.has("orgname")) {
                    if (previousRoom == null) {
                        previousRoom = roomJson.getString("orgname");
                    } else {
                        previousRoom += ", " + roomJson.getString("orgname");
                    }
                }
            }
            substitution.setRoom(room);
            substitution.setPreviousRoom(previousRoom);

            JSONArray teachersJson = substJson.getJSONArray("te");
            String teacher = null;
            String previousTeacher = null;
            for (int k = 0; k < teachersJson.length(); k++) {
                JSONObject teacherJson = teachersJson.getJSONObject(k);

                if (schedule.getType().equals(SubstitutionSchedule.Type.TEACHER) && !teacherJson.has("orgname") &&
                        !teacherJson.has("name")) {
                    // cannot access teacher names
                    throw new CredentialInvalidException();
                }

                if (teacherJson.has("name")) {
                    if (teacher == null) {
                        teacher = teacherJson.getString("name");
                    } else {
                        teacher += ", " + teacherJson.getString("name");
                    }
                }
                if (teacherJson.has("orgname")) {
                    if (previousTeacher == null) {
                        previousTeacher = teacherJson.getString("orgname");
                    } else {
                        previousTeacher += ", " + teacherJson.getString("orgname");
                    }
                }
            }
            substitution.setTeacher(teacher);
            substitution.setPreviousTeacher(previousTeacher);

            substitution.setDesc(substJson.optString("txt"));

            DateTimeFormatter timeFormat = DateTimeFormat.forPattern("HHmm");

            LocalDate date = DATE_FORMAT.parseLocalDate(String.valueOf(substJson.getInt("date")));
            LocalTime start = timeFormat.parseLocalTime(getParseableTime(substJson.getInt("startTime")));
            LocalTime end = timeFormat.parseLocalTime(getParseableTime(substJson.getInt("endTime")));

            TimeGrid.Day timegridDay = timegrid.getDay(date.getDayOfWeek());
            substitution.setLesson(timegridDay != null ? timegridDay.getLesson(start, end) : "");

            SubstitutionScheduleDay day = getDayForDate(schedule, days, date);

            day.addSubstitution(substitution);
        }

        schedule.setClasses(toNamesList(getClasses()));
        final String protocol = data.optString(PARAM_PROTOCOL, "https") + "://";
        schedule.setWebsite(protocol + data.getString(PARAM_HOST) + "/WebUntis");

        logout();

        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            JSONArray messages = getMessagesOfDay(date).getJSONObject("messageOfDayCollection")
                    .getJSONArray("messages");
            if (messages.length() > 0) {
                SubstitutionScheduleDay day = getDayForDate(schedule, days, date);
                for (int j = 0; j < messages.length(); j++) {
                    day.addMessage(messages.getJSONObject(j).getString("text"));
                }
            }
        }
        return schedule;
    }

    @NotNull private static SubstitutionScheduleDay getDayForDate(SubstitutionSchedule schedule,
                                                                  Map<LocalDate, SubstitutionScheduleDay> days,
                                                                  LocalDate date) {
        SubstitutionScheduleDay day = days.get(date);
        if (day == null) {
            day = new SubstitutionScheduleDay();
            day.setDate(date);
            schedule.addDay(day);
            days.put(date, day);
        }
        return day;
    }

    private JSONObject getMessagesOfDay(LocalDate date) throws JSONException, CredentialInvalidException, IOException {
        // messages only seem to work if they are set as "public" in WebUntis.
        loginInternal();
        JSONObject params = new JSONObject();
        params.put("date", date.toString());
        return (JSONObject) request("getMessagesOfDay", params, true);
    }

    private JSONArray getSubstitutions(LocalDate start, LocalDate end)
            throws CredentialInvalidException, IOException, JSONException {
        JSONObject params = new JSONObject();
        params.put("startDate", DATE_FORMAT.print(start));
        params.put("endDate", DATE_FORMAT.print(end));
        params.put("departmentId", 0);
        return (JSONArray) request("getSubstitutions", params);
    }

    private JSONArray getTimeGrid() throws JSONException, CredentialInvalidException, IOException {
        return (JSONArray) request("getTimegridUnits");
    }

    private JSONArray getHolidays() throws JSONException, CredentialInvalidException, IOException {
        return (JSONArray) request("getHolidays");
    }

    private LocalDateTime getLastImport() throws JSONException, CredentialInvalidException, IOException {
        return new LocalDateTime(request("getLatestImportTime"));
    }

    @NotNull
    private Map<String, String> idNameMap(JSONArray subjects) throws JSONException {
        Map<String, String> subjectsMap = new HashMap<>();
        for (int i = 0; i < subjects.length(); i++) {
            JSONObject subject = subjects.getJSONObject(i);
            subjectsMap.put(subject.getString("id"), subject.getString("name"));
        }
        return subjectsMap;
    }

    private String codeToType(String code) {
        switch (code) {
            case "cancel":
                return "Entfall";
            case "subst":
            case "stxt":
                return "Vertretung";
            case "rmchg":
                return "Raumänderung";
            case "add":
                return "Sondereinsatz";
            case "shift":
                return "Verlegung";
            case "free":
                return "Freisetzung";
            default:
                System.err.println("unknown type: " + code);
                return code;
        }
    }

    @Override public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        login();
        List<String> classes = toNamesList(getClasses());
        logout();
        return classes;
    }

    @Override public List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException {
        login();
        List<String> teachers = toNamesList(getTeachers());
        logout();
        return teachers;
    }

    private void login() throws JSONException, IOException, CredentialInvalidException {
        if (sessionId != null) return;

        JSONObject params = new JSONObject();
        params.put("user", ((UserPasswordCredential) credential).getUsername());
        params.put("password", ((UserPasswordCredential) credential).getPassword());
        params.put("client", USERAGENT);
        JSONObject response = (JSONObject) request("authenticate", params);
        if (response.has("sessionId")) {
            sessionId = response.getString("sessionId");
        } else {
            throw new CredentialInvalidException();
        }
    }

    private void logout() throws IOException, JSONException, CredentialInvalidException {
        request("logout");
        sessionId = null;
    }

    private JSONArray getClasses() throws IOException, JSONException, CredentialInvalidException {
        return (JSONArray) request("getKlassen");
    }

    private JSONArray getTeachers() throws IOException, JSONException, CredentialInvalidException {
        return (JSONArray) request("getTeachers");
    }

    @NotNull private List<String> toNamesList(JSONArray classesJson) throws JSONException {
        List<String> classes = new ArrayList<>();
        for (int i = 0; i < classesJson.length(); i++) {
            classes.add(classesJson.getJSONObject(i).getString("name"));
        }
        return classes;
    }

    private Object request(String method) throws IOException, JSONException, CredentialInvalidException {
        return request(method, new JSONObject());
    }

    private Object request(String method, @NotNull JSONObject params)
            throws JSONException, IOException, CredentialInvalidException {
        return request(method, params, false);
    }

    private Object request(String method, @NotNull JSONObject params, boolean internal)
            throws JSONException, IOException, CredentialInvalidException {
        String host = data.getString(PARAM_HOST);
        String school = data.getString(PARAM_SCHOOLNAME);

        final String protocol = data.optString(PARAM_PROTOCOL, "https") + "://";
        String url = protocol + host + "/WebUntis/jsonrpc" + (internal ? "_intern" : "") + ".do?school=" +
                URLEncoder.encode(school, "UTF-8");

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USERAGENT);

        if (internal && !method.equals("getAppSharedSecret")) {
            JSONObject auth = new JSONObject();
            long time = System.currentTimeMillis();
            auth.put("user", ((UserPasswordCredential) credential).getUsername());
            try {
                auth.put("otp", authCodeInternal(time));
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new IOException(e);
            }
            auth.put("clientTime", time);
            params.put("auth", auth);
        }

        JSONObject body = new JSONObject();
        body.put("id", ISODateTimeFormat.dateTime().print(DateTime.now()));
        body.put("method", method);
        if (internal) {
            JSONArray paramsArray = new JSONArray();
            paramsArray.put(params);
            body.put("params", paramsArray);
        } else {
            body.put("params", params);
        }
        body.put("jsonrpc", "2.0");

        JSONObject response = new JSONObject(
                httpPost(url, "UTF-8", body.toString(), ContentType.APPLICATION_JSON, headers));
        if (!response.getString("id").equals(body.getString("id"))) {
            throw new IOException("wrong id returned by API");
        } else if (!response.has("result")) {
            JSONObject error = response.getJSONObject("error");
            switch (error.getInt("code")) {
                case -32601:
                    throw new IOException("Method not found");
                case -8504: // wrong password
                case -8998: // user temporarily blocked
                case -8502: // no username specified
                    throw new CredentialInvalidException();
                case -8520:
                    throw new IOException("not logged in");
                default:
                    throw new IOException(error.toString());
            }
        }
        return response.get("result");
    }

    private class TimeGrid {
        private final Map<Integer, Day> days;

        public TimeGrid(JSONArray timeGrid) throws JSONException {
            days = new HashMap<>();
            for (int i = 0; i < timeGrid.length(); i++) {
                JSONObject day = timeGrid.getJSONObject(i);
                int weekday = day.getInt("day") - 1; // Untis uses Sunday = 1, Joda Time Monday = 1
                if (weekday < 1) weekday += 7;
                days.put(weekday, new Day(day));
            }
        }

        public Day getDay(int weekday) {
            return days.get(weekday);
        }

        private class Day {
            private final Map<LocalTime, String> startTimes;
            private final Map<LocalTime, String> endTimes;

            public Day(JSONObject day) throws JSONException {
                startTimes = new HashMap<>();
                endTimes = new HashMap<>();

                DateTimeFormatter fmt = DateTimeFormat.forPattern("HHmm");
                JSONArray units = day.getJSONArray("timeUnits");
                for (int i = 0; i < units.length(); i++) {
                    JSONObject unit = units.getJSONObject(i);
                    String startTime = getParseableTime(unit.getInt("startTime"));
                    startTimes.put(fmt.parseLocalTime(startTime), unit.getString("name"));

                    String endTime = getParseableTime(unit.getInt("endTime"));
                    endTimes.put(fmt.parseLocalTime(endTime), unit.getString("name"));
                }
            }

            public String getLesson(LocalTime startTime, LocalTime endTime) {
                String startLesson = startTimes.get(startTime);
                String endLesson = endTimes.get(endTime);
                if (startLesson == null || endLesson == null) {
                    return "";
                } else if (startLesson.equals(endLesson)) {
                    return String.valueOf(startLesson);
                } else {
                    return startLesson + " - " + endLesson;
                }
            }
        }
    }

    @NotNull private String getParseableTime(int value) {
        return String.format("%04d", value);
    }

    private void loginInternal() throws JSONException, IOException, CredentialInvalidException {
        if (sharedSecret != null) return;

        JSONObject params = new JSONObject();
        params.put("userName", ((UserPasswordCredential) credential).getUsername());
        params.put("password", ((UserPasswordCredential) credential).getPassword());
        params.put("client", USERAGENT);
        sharedSecret = (String) request("getAppSharedSecret", params, true);
    }

    private int authCodeInternal(long time) throws NoSuchAlgorithmException, InvalidKeyException {
        long t = time / 30000;
        byte[] key = new Base32().decode(sharedSecret.toUpperCase().getBytes());
        byte[] data = new byte[8];
        long value = t;
        int i = 8;
        while (true) {
            int i2 = i - 1;
            if (i <= 0) {
                break;
            }
            data[i2] = (byte) ((int) value);
            value >>>= 8;
            i = i2;
        }
        SecretKeySpec signKey = new SecretKeySpec(key, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(signKey);
        byte[] hash = mac.doFinal(data);
        int offset = hash[19] & 15;
        long truncatedHash = 0;
        for (int i2 = 0; i2 < 4; i2 += 1) {
            truncatedHash = (truncatedHash << 8) | ((long) (hash[offset + i2] & 255));
        }
        return (int) ((truncatedHash & 2147483647L) % 1000000);
    }
}
