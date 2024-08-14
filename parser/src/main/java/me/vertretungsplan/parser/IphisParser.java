/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 * Copyright (c) 2017 Tobias Knipping
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureException;
import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.*;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpResponseException;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.paour.comparator.NaturalOrderComparator;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for substitution schedules from IPHIS.
 * <p>
 * More information can be found on the <a href="https://www.tk-schulsoftware.de">official website</a>.
 * <p>
 * This parser can be accessed using <code>"iphis"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>url</code> (String, required)</dt>
 * <dd>The URL of the IPHIS Instance.</dd>
 *
 * <dt><code>kuerzel</code> (String, required)</dt>
 * <dd>The school shortcode required for IPHIS.</dd>
 *
 * <dt><code>jwt_key</code> (String, required)</dt>
 * <dd>The key used for signing the JWT</dd>
 * </dl>
 * <p>
 * You have to use a {@link me.vertretungsplan.objects.authentication.UserPasswordAuthenticationData} because all
 * schedules on IPHIS are protected by a login.
 */
public class IphisParser extends BaseParser {

    private static final String PARAM_URL = "url";
    private static final String PARAM_JWT_KEY = "jwt_key";
    private static final String PARAM_KUERZEL = "kuerzel";
    private static final String PARAM_IS_PERSONAL = "isPersonal";

    /**
     * URL of given IPHIS instance
     */
    private String api;

    /**
     * Shortcode for school
     */
    private String kuerzel;

    /**
     *
     */
    private String jwt_key;

    /**
     *
     */
    private Boolean isPersonal;

    /**
     *
     */
    private String website;

    /**
     * array of grades/classes retrieved from the api
     */
    private JSONArray grades;
    /**
     * array of teachers retrieved from the api
     */
    private JSONArray teachers;
    /**
     * array of messages retrieved from the api
     */
    private JSONArray messages;
    /**
     * hold the Authentication Token (JWT)
     */
    private String authToken;
    /**
     * hold the timestamp of the last schedule-update
     */
    private LocalDateTime lastUpdate;

    public IphisParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        JSONObject data = scheduleData.getData();
        try {
            api = "https://" + data.getString(PARAM_URL) + "/remote/vertretungsplan/ssp";
            kuerzel = data.getString(PARAM_KUERZEL);
            jwt_key = data.getString(PARAM_JWT_KEY);
            if (data.has(PARAM_IS_PERSONAL)) {
                isPersonal = data.getBoolean(PARAM_IS_PERSONAL);
            } else {
                isPersonal = false;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        final SubstitutionSchedule substitutionSchedule = SubstitutionSchedule.fromData(scheduleData);

        if (login()) {
            getGrades();
            getTeachers();
            getMessages();

            final JSONArray changes = getChanges();

            substitutionSchedule.setClasses(getAllClasses());
            substitutionSchedule.setTeachers(getAllTeachers());
            substitutionSchedule.setWebsite(website);

            parseIphis(substitutionSchedule, changes, grades, teachers, messages);
        }
        return substitutionSchedule;
    }

    @Override
    public LocalDateTime getLastChange() throws IOException, CredentialInvalidException {
        if (lastUpdate == null) {
            login();
        }
        return lastUpdate;
    }

    private Boolean login() throws CredentialInvalidException, IOException {
        final UserPasswordCredential userPasswordCredential = (UserPasswordCredential) credential;
        final String username = userPasswordCredential.getUsername();
        final String password = userPasswordCredential.getPassword();

        JSONObject payload = new JSONObject();
        try {
            payload.put("school", kuerzel);
            payload.put("user", username);
            payload.put("type", scheduleData.getType());
            payload.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }

        httpPost(api + "/login", "UTF-8", payload.toString(), ContentType.APPLICATION_JSON);
        final String httpResponse = httpPost(api + "/login", "UTF-8", payload.toString(), ContentType.APPLICATION_JSON);
        final JSONObject token;
        try {
            token = new JSONObject(httpResponse);

            final String key = Base64.encodeBase64String(jwt_key.getBytes());
            final Claims jwtToken = Jwts.parser().setSigningKey(key)
                    .parseClaimsJws(token.getString("token")).getBody();
            assert jwtToken.getSubject().equals("vertretungsplan.me");

            authToken = token.getString("token");
            website = jwtToken.getIssuer();
            lastUpdate = new LocalDateTime(token.getLong("stand") * 1000);
        } catch (SignatureException | JSONException e) {
            throw new CredentialInvalidException();
        }

        return true;
    }

    /**
     * Returns a JSONArray with all changes from now to in one week.
     */
    private JSONArray getChanges() throws IOException, CredentialInvalidException {
        // Date (or alias of date) when the changes start
        final String startBy = LocalDate.now().toString();
        // Date (or alias of date) when the changes end
        final String endBy = LocalDate.now().plusWeeks(1).toString();

        final String url = api + "/vertretung/von/" + startBy + "/bis/" + endBy;
        return getJSONArray(url);
    }

    /**
     * Returns a JSONArray with all messages.
     */
    private void getMessages() throws IOException, CredentialInvalidException {
        if (messages == null) {
            final String url = api + "/nachrichten";
            messages = getJSONArray(url);
        }
    }

    /**
     * Returns a JSONArray with all grades.
     */
    private void getGrades() throws IOException, CredentialInvalidException {
        if (grades == null) {
            final String url = api + "/klassen";
            grades = getJSONArray(url);
        }
    }

    /**
     * Returns a JSONArray with all teachers.
     */
    private void getTeachers() throws IOException, CredentialInvalidException {
        if (teachers == null) {
            final String url = api + "/lehrer";
            teachers = getJSONArray(url);
        }
    }

    private JSONArray getJSONArray(String url) throws IOException, CredentialInvalidException {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + authToken);
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");

            final String httpResponse = httpGet(url, "UTF-8", headers);
            return new JSONArray(httpResponse);
        } catch (HttpResponseException httpResponseException) {
            if (httpResponseException.getStatusCode() == 404) {
                return null;
            }
            throw httpResponseException;
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    void parseIphis(SubstitutionSchedule substitutionSchedule, JSONArray changes, JSONArray grades,
                    JSONArray teachers, JSONArray messages) throws IOException, JSONException {
        if (changes == null) {
            return;
        }
        // Link course IDs to their names
        HashMap<String, String> coursesHashMap = null;
        if (grades != null) {
            coursesHashMap = new HashMap<>();
            for (int i = 0; i < grades.length(); i++) {
                JSONObject grade = grades.getJSONObject(i);
                coursesHashMap.put(grade.getString("id"), grade.getString("name"));
            }
        }
        // Link teacher IDs to their names
        HashMap<String, String> teachersHashMap = null;
        if (teachers != null) {
            teachersHashMap = new HashMap<>();
            for (int i = 0; i < teachers.length(); i++) {
                JSONObject teacher = teachers.getJSONObject(i);
                teachersHashMap.put(teacher.getString("id"), teacher.getString("name"));
            }
        }

        // Add Messages
        List<AdditionalInfo> infos = new ArrayList<>(messages.length());

        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            AdditionalInfo info = new AdditionalInfo();
            info.setHasInformation(message.getBoolean("notification"));
            info.setTitle(message.getString("titel").trim());
            info.setText(message.getString("nachricht").trim());
            info.setFromSchedule(true);
            infos.add(info);
        }

        substitutionSchedule.getAdditionalInfos().addAll(infos);
        substitutionSchedule.setLastChange(lastUpdate);

        // Add changes to SubstitutionSchedule
        LocalDate currentDate = LocalDate.now();
        SubstitutionScheduleDay substitutionScheduleDay = new SubstitutionScheduleDay();
        substitutionScheduleDay.setDate(currentDate);
        for (int i = 0; i < changes.length(); i++) {
            final JSONObject change = changes.getJSONObject(i);
            final LocalDate substitutionDate = new LocalDate(change.getString("datum"));

            // If starting date of change does not equal date of SubstitutionScheduleDay
            if (!substitutionDate.isEqual(currentDate)) {
                if (!substitutionScheduleDay.getSubstitutions().isEmpty()
                        || !substitutionScheduleDay.getMessages().isEmpty()) {
                    substitutionSchedule.addDay(substitutionScheduleDay);
                }
                substitutionScheduleDay = new SubstitutionScheduleDay();
                substitutionScheduleDay.setDate(substitutionDate);
                currentDate = substitutionDate;
            }

            if (change.getInt("id") > 0) {
                final Substitution substitution = getSubstitution(change, coursesHashMap, teachersHashMap);

                substitutionScheduleDay.addSubstitution(substitution);
            } else if (!change.optString("nachricht").isEmpty()) {
                substitutionScheduleDay.addMessage(change.optString("nachricht"));
            }
        }
        substitutionSchedule.addDay(substitutionScheduleDay);
    }

    private String[] getSQLArray(String data) {
        String[] retArray = {};
        Pattern pattern = Pattern.compile("\\{(.*?)}");
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            retArray = matcher.group(1).split(",");
        }
        return retArray;
    }

    private Substitution getSubstitution(JSONObject change, HashMap<String, String> gradesHashMap,
                                         HashMap<String, String> teachersHashMap)
            throws IOException, JSONException {
        final Substitution substitution = new Substitution();
        // Set class(es)
        final String[] classIds = getSQLArray(change.getString("id_klasse"));
        if (classIds.length > 0) {
            if (gradesHashMap == null) {
                throw new IOException("Change references a grade but grades are empty.");
            }
            final HashSet<String> classes = new HashSet<>();
            for (String classId : classIds) {
                if (!classId.equalsIgnoreCase("null")) {
                    if (gradesHashMap.containsKey(classId)) {
                        classes.add(gradesHashMap.get(classId));
                    } else {
                        throw new IllegalArgumentException("unknown class ID " + classId + " referenced");
                    }
                }
            }
            substitution.setClasses(classes);
        }
        // Set type
        final String type = change.getString("aenderungsgrund").trim();
        if (!type.isEmpty() && !type.equalsIgnoreCase("null")) {
            substitution.setType(type);
        } else {
            substitution.setType("Vertretung");
        }

        // Set color
        substitution.setColor(colorProvider.getColor(type));
        // Set covering teacher
        final String[] coveringTeacherIds = getSQLArray(change.getString("id_person_verantwortlich"));
        final HashSet<String> coveringTeachers = new HashSet<>();
        if (coveringTeacherIds.length > 0) {
            if (teachersHashMap == null) {
                throw new IOException("Change references a covering teacher but teachers are empty.");
            }
            for (String coveringTeacherId : coveringTeacherIds) {
                if (!coveringTeacherId.equalsIgnoreCase("null") && teachersHashMap.get(coveringTeacherId) != null) {
                    coveringTeachers.add(teachersHashMap.get(coveringTeacherId));
                }
            }
            substitution.setTeachers(coveringTeachers);
        }
        // Set teacher
        final String[] teacherIds = getSQLArray(change.getString("id_person_verantwortlich_orig"));
        final HashSet<String> teachers = new HashSet<>();
        if (teacherIds.length > 0) {
            if (teachersHashMap == null) {
                throw new IOException("Change references a teacher but teachers are empty.");
            }
            for (String teacherId : teacherIds) {
                if (!teacherId.equalsIgnoreCase("null") && teachersHashMap.get(teacherId) != null) {
                    teachers.add(teachersHashMap.get(teacherId));
                }
            }
            substitution.setPreviousTeachers(teachers);
        }

        //Set room
        if (!change.optString("raum").isEmpty() && !change.optString("raum").equalsIgnoreCase("null")) {
            substitution.setRoom(change.optString("raum"));
        } else if (!change.optString("raum_orig").isEmpty() &&
                !change.optString("raum_orig").equalsIgnoreCase("null")) {
            substitution.setRoom(change.optString("raum_orig"));
        }
        if (!change.optString("raum_orig").isEmpty() && !change.optString("raum_orig").equalsIgnoreCase("null")) {
            substitution.setPreviousRoom(change.optString("raum_orig"));
        } else if (!change.optString("raum").isEmpty() && !change.optString("raum").equalsIgnoreCase("null")) {
            substitution.setPreviousRoom(change.optString("raum"));
        }
        //Set subject
        if (!change.optString("fach").isEmpty() && !change.optString("fach").equalsIgnoreCase("null")) {
            substitution.setSubject(change.optString("fach"));
        }
        if (!change.optString("fach_orig").isEmpty() && !change.optString("fach_orig").equalsIgnoreCase("null")) {
            substitution.setPreviousSubject(change.optString("fach_orig"));
        }

        //Set description
        if (!change.getString("information").isEmpty() &&
                !change.getString("information").equalsIgnoreCase("null")) {
            substitution.setDesc(change.getString("information").trim());
        }

        final String startingHour = change.getString("zeit_von").replaceFirst("^0+(?!$)", "");
        final String endingHour = change.getString("zeit_bis").replaceFirst("^0+(?!$)", "");
        if (!startingHour.isEmpty() || !endingHour.isEmpty()) {
            String lesson = getLessonSubstitutionString(startingHour, endingHour);
            substitution.setLesson(lesson);
        }
        return substitution;
    }

    @NotNull
    private static String getLessonSubstitutionString(String startingHour, String endingHour) {
        String lesson = "";
        if (!startingHour.isEmpty() && endingHour.isEmpty()) {
            lesson = "Ab " + startingHour;
        }
        if (startingHour.isEmpty() && !endingHour.isEmpty()) {
            lesson = "Bis " + endingHour;
        }
        if (!startingHour.isEmpty() && !endingHour.isEmpty()) {
            lesson = startingHour + " - " + endingHour;
        }
        if (startingHour.equals(endingHour)) {
            lesson = startingHour;
        }
        return lesson;
    }

    @Override
    public List<String> getAllClasses() throws JSONException {
        final List<String> classesList = new ArrayList<>();
        if (grades == null) {
            return null;
        }
        for (int i = 0; i < grades.length(); i++) {
            final JSONObject grade = grades.getJSONObject(i);
            classesList.add(grade.getString("name"));
        }
        //noinspection unchecked
        classesList.sort(new NaturalOrderComparator());
        return classesList;
    }

    @Override
    public List<String> getAllTeachers() throws JSONException {
        final List<String> teachersList = new ArrayList<>();
        if (teachers == null) {
            return null;
        }
        for (int i = 0; i < teachers.length(); i++) {
            final JSONObject teacher = teachers.getJSONObject(i);
            teachersList.add(teacher.getString("name"));
        }
        return teachersList;
    }

    @Override
    public boolean isPersonal() {
        return isPersonal;
    }
}
