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
    private UserData userData;
    private static final String USERAGENT = "vertretungsplan.me";
    private String sharedSecret;
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("yyyyMMdd");
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormat.forPattern("HHmm");

    WebUntisParser(SubstitutionScheduleData scheduleData,
                   CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    @Override public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        try {
            login();
            SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);
            schedule.setLastChange(getLastImport());

            TimeGrid timegrid = new TimeGrid(getTimeGrid());
            final LocalDate today = LocalDate.now();
            int daysToAdd = getDaysToAdd();

            LocalDate endDate = today.plusDays(6 + daysToAdd);

            LocalDate startNextYear = null;
            LocalDate endNextYear = null;

            int schoolyearId = -1;
            int nextYearId = -1;
            JSONArray schoolyears = getSchoolyears();
            for (int i = 0; i < schoolyears.length(); i++) {
                JSONObject schoolyear = schoolyears.getJSONObject(i);
                schoolyearId = schoolyear.getInt("id");
                final LocalDate yearStart = parseDate(schoolyear.getInt("startDate"));
                final LocalDate yearEnd = parseDate(schoolyear.getInt("endDate"));

                if ((yearStart.isBefore(today) || yearStart.equals(today)) && (yearEnd.isAfter(today) || yearEnd
                        .equals(today))) {
                    // this is the current schoolyear
                    if (endDate.isAfter(yearEnd)) {
                        endDate = yearEnd;

                        if (i < schoolyears.length() - 1) {
                            JSONObject nextYear = schoolyears.getJSONObject(i + 1);
                            startNextYear = parseDate(nextYear.getInt("startDate"));
                            endNextYear = startNextYear.plus(Days.days(5));
                            nextYearId = nextYear.getInt("id");
                        }
                    }
                    break;
                }
            }

            try {
                schedule = parseScheduleUsingSubstitutions(schedule, timegrid, today, endDate);
                if (startNextYear != null && endNextYear != null) {
                    parseScheduleUsingSubstitutions(schedule, timegrid, startNextYear, endNextYear);
                }
            } catch (UnauthorizedException e) {
                schedule = parseScheduleUsingTimetable(schedule, timegrid, today, endDate, schoolyearId);
                if (startNextYear != null && endNextYear != null) {
                    parseScheduleUsingTimetable(schedule, timegrid, startNextYear, endNextYear, nextYearId);
                }
            }

            schedule.setClasses(toNamesList(getClasses()));
            final String protocol = data.optString(PARAM_PROTOCOL, "https") + "://";
            schedule.setWebsite(protocol + data.getString(PARAM_HOST) + "/WebUntis");

            try {
                addMessagesOfDay(schedule);
            } catch (UnauthorizedException ignored) {

            }

            logout();
            return schedule;
        } catch (UnauthorizedException e) {
            throw new IOException(e);
        }
    }

    private void addMessagesOfDay(SubstitutionSchedule schedule)
            throws JSONException, CredentialInvalidException, IOException, UnauthorizedException {
        try {
            for (int i = 0; i < 7; i++) {
                LocalDate date = LocalDate.now().plusDays(i);
                JSONArray messages = getMessagesOfDay(date).getJSONObject("messageOfDayCollection")
                        .getJSONArray("messages");
                if (messages.length() > 0) {
                    SubstitutionScheduleDay day = getDayForDate(schedule, date);
                    for (int j = 0; j < messages.length(); j++) {
                        day.addMessage(messages.getJSONObject(j).getString("text"));
                    }
                }
            }
        } catch (MethodNotFoundException ignored) {
            // this is an old WebUntis instance that does not support getMessagesOfDay. Fail silently.
        }
    }

    /**
     * find out if there's a holiday currently and if so, also display substitutions after it
     *
     * @return
     * @throws JSONException
     * @throws CredentialInvalidException
     * @throws IOException
     */
    private int getDaysToAdd() throws JSONException, CredentialInvalidException, IOException {
        final LocalDate today = LocalDate.now();
        int daysToAdd = 0;
        try {
            //
            JSONArray holidays = getHolidays();
            for (int i = 0; i < holidays.length(); i++) {
                LocalDate startDate = parseDate(holidays.getJSONObject(i).getInt("startDate"));
                LocalDate endDate = parseDate(holidays.getJSONObject(i).getInt("endDate"));
                if (!startDate.isAfter(today.plusDays(6)) && !endDate.isBefore(today)) {
                    if (startDate.isBefore(today)) {
                        daysToAdd += Days.daysBetween(today, endDate).getDays() + 1;
                    } else {
                        daysToAdd += Days.daysBetween(startDate, endDate).getDays() + 2;
                    }
                }
            }
        } catch (UnauthorizedException ignored) {

        }
        return daysToAdd;
    }

    private LocalDate parseDate(int startDate1) {
        return DATE_FORMAT.parseLocalDate(String.valueOf(startDate1));
    }

    private SubstitutionSchedule parseScheduleUsingTimetable(SubstitutionSchedule schedule, TimeGrid timegrid,
                                                             LocalDate startDate,
                                                             LocalDate endDate, int schoolyear)
            throws JSONException, UnauthorizedException, CredentialInvalidException, IOException {
        try {
            switch (scheduleData.getType()) {
                case TEACHER:
                    if (userData.getPersonType() != UserData.TYPE_TEACHER) {
                        throw new CredentialInvalidException();
                    }

                    Map<Integer, String> teachers = idNameMap(getTeachers());
                    for (Map.Entry<Integer, String> entry : teachers.entrySet()) {
                        JSONArray json = getTimetable(startDate, endDate, new UserData(entry.getKey(), UserData
                                .TYPE_TEACHER));
                        parseTimetable(json, schedule, timegrid);
                    }
                    break;
                case STUDENT:
                    Map<Integer, String> classes = idNameMap(getClasses(schoolyear));
                    for (Map.Entry<Integer, String> entry : classes.entrySet()) {
                        JSONArray json = getTimetable(startDate, endDate, new UserData(entry.getKey(), UserData
                                .TYPE_KLASSE));
                        parseTimetable(json, schedule, timegrid);
                    }
                    break;
            }
        } catch (UnauthorizedException e) {
            try {
                // access is only allowed for the own schedule
                JSONArray json = getTimetable(startDate, endDate, userData);
                parseTimetable(json, schedule, timegrid);
            } catch (UnauthorizedException | IOException e1) {
                if (schedule.getType() != SubstitutionSchedule.Type.STUDENT || userData.klasseId == null) throw e1;
                JSONArray json = getTimetable(startDate, endDate, new UserData(userData.klasseId, UserData
                        .TYPE_KLASSE));
                parseTimetable(json, schedule, timegrid);
            }
        }

        return schedule;
    }

    private void parseTimetable(JSONArray json, SubstitutionSchedule schedule, TimeGrid timegrid)
            throws JSONException, CredentialInvalidException {
        for (int i = 0; i < json.length(); i++) {
            JSONObject lesson = json.getJSONObject(i);

            LocalDate date = parseDate(lesson.getInt("date"));
            SubstitutionScheduleDay day = getDayForDate(schedule, date);

            if (!isSubstitution(lesson)) {
                continue;
            }

            Substitution subst = new Substitution();
            if (lesson.has("code")) {
                subst.setType(codeToType(lesson.getString("code")));
            } else {
                subst.setType("Vertretung");
            }
            subst.setColor(colorProvider.getColor(subst.getType()));

            parseClasses(lesson, subst);
            parseSubjects(lesson, subst);
            parseRooms(lesson, subst);
            parseTeachers(schedule, lesson, subst);

            if (lesson.has("lstext") && lesson.has("substText")
                    && !lesson.getString("lstext").equals(lesson.getString("substText"))) {
                subst.setDesc(lesson.getString("lstext") + ", " + lesson.getString("substText"));
            } else if (lesson.has("lstext")) {
                subst.setDesc(lesson.getString("lstext"));
            } else if (lesson.has("substText")) {
                subst.setDesc(lesson.getString("substText"));
            }

            LocalTime start = TIME_FORMAT.parseLocalTime(getParseableTime(lesson.getInt("startTime")));
            LocalTime end = TIME_FORMAT.parseLocalTime(getParseableTime(lesson.getInt("endTime")));

            TimeGrid.Day timegridDay = timegrid.getDay(date.getDayOfWeek());
            subst.setLesson(timegridDay != null ? timegridDay.getLesson(start, end) : "");

            day.addSubstitution(subst);
        }
    }

    private boolean isSubstitution(JSONObject lesson) throws JSONException {
        if (lesson.has("code") || lesson.has("substText")) {
            return true;
        }
        // check for room, teacher or subject change
        for (String name : new String[]{"ro", "te", "su"}) {
            if (!lesson.has(name)) continue;

            JSONArray json = lesson.getJSONArray(name);
            for (int k = 0; k < json.length(); k++) {
                JSONObject singleJson = json.getJSONObject(k);
                if (singleJson.has("orgname") && singleJson.has("name")
                        && !singleJson.getString("name").equals(singleJson.getString("orgname"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @NotNull private SubstitutionSchedule parseScheduleUsingSubstitutions(SubstitutionSchedule schedule,
                                                                          TimeGrid timegrid, LocalDate startDate,
                                                                          LocalDate endDate)
            throws CredentialInvalidException, IOException, JSONException, UnauthorizedException {
        JSONArray substitutions = getSubstitutions(startDate, endDate);

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
            parseClasses(substJson, substitution);
            parseSubjects(substJson, substitution);
            parseRooms(substJson, substitution);
            parseTeachers(schedule, substJson, substitution);

            substitution.setDesc(substJson.optString("txt"));

            LocalDate date = parseDate(substJson.getInt("date"));
            LocalTime start = TIME_FORMAT.parseLocalTime(getParseableTime(substJson.getInt("startTime")));
            LocalTime end = TIME_FORMAT.parseLocalTime(getParseableTime(substJson.getInt("endTime")));

            TimeGrid.Day timegridDay = timegrid.getDay(date.getDayOfWeek());
            substitution.setLesson(timegridDay != null ? timegridDay.getLesson(start, end) : "");

            SubstitutionScheduleDay day = getDayForDate(schedule, date);
            day.addSubstitution(substitution);
        }

        return schedule;
    }

    private void parseTeachers(SubstitutionSchedule schedule, JSONObject substJson, Substitution substitution)
            throws JSONException, CredentialInvalidException {
        if (!substJson.has("te")) return;

        JSONArray teachersJson = substJson.getJSONArray("te");
        Set<String> teachers = new HashSet<>();
        Set<String> previousTeachers = new HashSet<>();
        for (int k = 0; k < teachersJson.length(); k++) {
            JSONObject teacherJson = teachersJson.getJSONObject(k);

            if (schedule.getType().equals(SubstitutionSchedule.Type.TEACHER) && !teacherJson.has("orgname") &&
                    !teacherJson.has("name")) {
                // cannot access teacher names
                throw new CredentialInvalidException();
            }

            if (teacherJson.has("name")) {
                teachers.add(teacherJson.getString("name"));
            }
            if (teacherJson.has("orgname")) {
                previousTeachers.add(teacherJson.getString("orgname"));
            }
        }
        substitution.setTeachers(teachers);
        substitution.setPreviousTeachers(previousTeachers);
    }

    private void parseRooms(JSONObject substJson, Substitution substitution) throws JSONException {
        JSONArray roomsJson = substJson.getJSONArray("ro");
        StringBuilder room = null;
        StringBuilder previousRoom = null;
        for (int k = 0; k < roomsJson.length(); k++) {
            JSONObject roomJson = roomsJson.getJSONObject(k);
            if (roomJson.has("name")) {
                if (room == null) {
                    room = new StringBuilder(roomJson.getString("name"));
                } else {
                    room.append(", ").append(roomJson.getString("name"));
                }
            }
            if (roomJson.has("orgname")) {
                if (previousRoom == null) {
                    previousRoom = new StringBuilder(roomJson.getString("orgname"));
                } else {
                    previousRoom.append(", ").append(roomJson.getString("orgname"));
                }
            }
        }
        substitution.setRoom(room != null ? room.toString() : null);
        substitution.setPreviousRoom(previousRoom != null ? previousRoom.toString() : null);
    }

    private void parseSubjects(JSONObject substJson, Substitution substitution) throws JSONException {
        JSONArray subjectsJson = substJson.getJSONArray("su");
        StringBuilder subject = null;
        for (int k = 0; k < subjectsJson.length(); k++) {
            JSONObject subjectJson = subjectsJson.getJSONObject(k);
            if (subjectJson.has("name")) {
                if (subject == null) {
                    subject = new StringBuilder(subjectJson.getString("name"));
                } else {
                    subject.append(", ").append(subjectJson.getString("name"));
                }
            }
        }
        substitution.setSubject(subject != null ? subject.toString() : null);
    }

    private void parseClasses(JSONObject substJson, Substitution substitution) throws JSONException {
        Set<String> cls = new HashSet<>();
        JSONArray classesJson = substJson.getJSONArray("kl");
        for (int k = 0; k < classesJson.length(); k++) {
            cls.add(classesJson.getJSONObject(k).getString("name"));
        }
        substitution.setClasses(cls);
    }

    @NotNull private static SubstitutionScheduleDay getDayForDate(SubstitutionSchedule schedule,
                                                                  LocalDate date) {
        SubstitutionScheduleDay day = null;
        for (SubstitutionScheduleDay currentDay : schedule.getDays()) {
            if (currentDay.getDate().equals(date)) {
                day = currentDay;
                break;
            }
        }
        if (day == null) {
            day = new SubstitutionScheduleDay();
            day.setDate(date);
            schedule.addDay(day);
        }
        return day;
    }

    @NotNull
    private Map<Integer, String> idNameMap(JSONArray subjects) throws JSONException {
        Map<Integer, String> subjectsMap = new HashMap<>();
        for (int i = 0; i < subjects.length(); i++) {
            JSONObject subject = subjects.getJSONObject(i);
            subjectsMap.put(subject.getInt("id"), subject.getString("name"));
        }
        return subjectsMap;
    }

    private String codeToType(String code) {
        switch (code) {
            // for getSubstitutions
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
            // for getTimetable
            case "irregular":
                return "Vertretung";
            case "cancelled":
                return "Entfall";
            default:
                System.err.println("unknown type: " + code);
                return code;
        }
    }

    @Override public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        try {
            login();
            List<String> classes = toNamesList(getClasses());
            logout();
            return classes;
        } catch (UnauthorizedException e) {
            throw new CredentialInvalidException();
        }
    }

    @Override public List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException {
        try {
            login();
            List<String> teachers = toNamesList(getTeachers());
            logout();
            return teachers;
        } catch (UnauthorizedException e) {
            throw new CredentialInvalidException();
        }
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
                    String name = unit.getString("name");
                    if (name.equals("0")) {
                        name = String.valueOf(i + 1);
                    }
                    startTimes.put(fmt.parseLocalTime(startTime), name);

                    String endTime = getParseableTime(unit.getInt("endTime"));
                    endTimes.put(fmt.parseLocalTime(endTime), name);
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

    @Override public boolean isPersonal() {
        return true;
    }

    // ---------------
    // | API methods |
    // ---------------

    private JSONObject getMessagesOfDay(LocalDate date)
            throws JSONException, CredentialInvalidException, IOException, UnauthorizedException {
        // messages only seem to work if they are set as "public" in WebUntis.
        loginInternal();
        JSONObject params = new JSONObject();
        params.put("date", date.toString());
        return (JSONObject) request("getMessagesOfDay", params, true);
    }

    private JSONArray getSubstitutions(LocalDate start, LocalDate end)
            throws CredentialInvalidException, IOException, JSONException, UnauthorizedException {
        JSONObject params = new JSONObject();
        params.put("startDate", DATE_FORMAT.print(start));
        params.put("endDate", DATE_FORMAT.print(end));
        params.put("departmentId", 0);
        return (JSONArray) request("getSubstitutions", params);
    }

    private JSONArray getTimetable(LocalDate start, LocalDate end, UserData userData)
            throws CredentialInvalidException, IOException, JSONException, UnauthorizedException {
        JSONObject params = new JSONObject();
        JSONObject options = new JSONObject();
        options.put("startDate", DATE_FORMAT.print(start));
        options.put("endDate", DATE_FORMAT.print(end));
        options.put("showBooking", true);
        options.put("showInfo", true);
        options.put("showSubstText", true);
        options.put("showLsText", true);
        options.put("showLsNumber", true);
        options.put("showStudentgroup", true);

        JSONArray fields = new JSONArray();
        fields.put("name");
        fields.put("longname");
        fields.put("id");
        options.put("klasseFields", fields);
        options.put("roomFields", fields);
        options.put("subjectFields", fields);
        options.put("teacherFields", fields);

        JSONObject element = new JSONObject();
        element.put("id", userData.getPersonId());
        element.put("type", userData.getPersonType());
        options.put("element", element);

        params.put("options", options);
        return (JSONArray) request("getTimetable", params);
    }

    private JSONArray getTimeGrid() throws JSONException, CredentialInvalidException, IOException,
            UnauthorizedException {
        return (JSONArray) request("getTimegridUnits");
    }

    private JSONArray getHolidays() throws JSONException, CredentialInvalidException, IOException,
            UnauthorizedException {
        return (JSONArray) request("getHolidays");
    }

    private JSONArray getSchoolyears() throws JSONException, CredentialInvalidException, IOException,
            UnauthorizedException {
        return (JSONArray) request("getSchoolyears");
    }

    private LocalDateTime getLastImport()
            throws JSONException, CredentialInvalidException, IOException, UnauthorizedException {
        return new LocalDateTime(request("getLatestImportTime"));
    }

    private void login() throws JSONException, IOException, CredentialInvalidException, UnauthorizedException {
        if (sessionId != null && userData != null) return;

        JSONObject params = new JSONObject();
        params.put("user", ((UserPasswordCredential) credential).getUsername());
        params.put("password", ((UserPasswordCredential) credential).getPassword());
        params.put("client", USERAGENT);
        JSONObject response = (JSONObject) request("authenticate", params);
        if (response.has("sessionId")) {
            sessionId = response.getString("sessionId");
            final int klasseId = response.optInt("klasseId", -1);
            userData = new UserData(response.getInt("personId"), response.getInt("personType"),
                    klasseId == -1 ? null : klasseId);
        } else {
            throw new CredentialInvalidException();
        }
    }

    private void loginInternal() throws JSONException, IOException, CredentialInvalidException, UnauthorizedException {
        if (sharedSecret != null) return;

        JSONObject params = new JSONObject();
        params.put("userName", ((UserPasswordCredential) credential).getUsername());
        params.put("password", ((UserPasswordCredential) credential).getPassword());
        params.put("client", USERAGENT);
        sharedSecret = (String) request("getAppSharedSecret", params, true);
    }

    private void logout() throws IOException, JSONException, CredentialInvalidException, UnauthorizedException {
        request("logout");
        sessionId = null;
        userData = null;
    }

    private JSONArray getClasses() throws IOException, JSONException, CredentialInvalidException,
            UnauthorizedException {
        return getClasses(null);
    }

    private JSONArray getClasses(Integer schoolyear) throws IOException, JSONException, CredentialInvalidException,
            UnauthorizedException {
        JSONObject params = new JSONObject();
        if (schoolyear != null) {
            params.put("schoolyearId", schoolyear);
        }
        return (JSONArray) request("getKlassen", params);
    }

    private JSONArray getTeachers() throws IOException, JSONException, CredentialInvalidException,
            UnauthorizedException {
        return (JSONArray) request("getTeachers");
    }

    @NotNull private List<String> toNamesList(JSONArray classesJson) throws JSONException {
        List<String> classes = new ArrayList<>();
        for (int i = 0; i < classesJson.length(); i++) {
            classes.add(classesJson.getJSONObject(i).getString("name"));
        }
        return classes;
    }

    private Object request(String method)
            throws IOException, JSONException, CredentialInvalidException, UnauthorizedException {
        return request(method, new JSONObject());
    }

    private Object request(String method, @NotNull JSONObject params)
            throws JSONException, IOException, CredentialInvalidException, UnauthorizedException {
        return request(method, params, false);
    }

    private Object request(String method, @NotNull JSONObject params, boolean internal)
            throws JSONException, IOException, CredentialInvalidException, UnauthorizedException {
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
                    throw new MethodNotFoundException();
                case -8504: // wrong password
                case -8998: // user temporarily blocked
                case -8502: // no username specified
                    throw new CredentialInvalidException();
                case -8520:
                    throw new IOException("not logged in");
                case -8509:
                    throw new UnauthorizedException();
                default:
                    throw new IOException(error.toString());
            }
        }
        return response.get("result");
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

    /**
     * Thrown when the user does not have the rights to execute an API call
     */
    private class UnauthorizedException extends Throwable {
    }

    private class UserData {
        public static final int TYPE_KLASSE = 1;
        public static final int TYPE_TEACHER = 2;
        public static final int TYPE_SUBJECT = 3;
        public static final int TYPE_ROOM = 4;
        public static final int TYPE_STUDENT = 5;

        public UserData(int personId, int personType) {
            this.personId = personId;
            this.personType = personType;
            this.klasseId = null;
        }

        public UserData(int personId, int personType, int klasseId) {
            this.personId = personId;
            this.personType = personType;
            this.klasseId = klasseId;
        }

        private int personId;
        private int personType;
        private Integer klasseId;

        public int getPersonId() {
            return personId;
        }

        public int getPersonType() {
            return personType;
        }

        public int getKlasseId() {
            return klasseId;
        }
    }

    private class MethodNotFoundException extends IOException {
    }
}
