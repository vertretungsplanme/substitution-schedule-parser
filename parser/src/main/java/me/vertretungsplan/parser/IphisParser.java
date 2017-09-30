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
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
 *
 * You have to use a {@link me.vertretungsplan.objects.authentication.UserPasswordAuthenticationData} because all
 * schedules on IPHIS are protected by a login.
 */
public class IphisParser extends BaseParser {

    private static final String PARAM_URL = "url";
    private static final String PARAM_JWT_KEY = "jwt_key";
    private static final String PARAM_KUERZEL = "kuerzel";

    /**
     * URL of given IPHIS instance
     */
    private String api;

    /**
     * Shortcode for school
     */
    private String kuerzel;
    /** */
    private String jwt_key;

    /**  */
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

    @Override public DateTime getLastChange() throws IOException, JSONException, CredentialInvalidException {
        if (lastUpdate == null) {
            login();
        }
        return lastUpdate.toDateTime();
    }

    private Boolean login() throws CredentialInvalidException, IOException {
        final UserPasswordCredential userPasswordCredential = (UserPasswordCredential) credential;
        final String username = userPasswordCredential.getUsername();
        final String password = userPasswordCredential.getPassword();

        JSONObject payload = new JSONObject();
        try {
            payload.put("school", kuerzel);
            payload.put("user", username);
            payload.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
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
            lastUpdate = new LocalDateTime(token.getLong("stand"));
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
    private void getMessages() throws IOException, JSONException, CredentialInvalidException {
        if (messages == null) {
            final String url = api + "/nachrichten";
            messages = getJSONArray(url);
        }
    }

    /**
     * Returns a JSONArray with all grades.
     */
    private void getGrades() throws IOException, JSONException, CredentialInvalidException {
        if (grades == null) {
            final String url = api + "/klassen";
            grades = getJSONArray(url);
        }
    }

    /**
     * Returns a JSONArray with all teachers.
     */
    private void getTeachers() throws IOException, JSONException, CredentialInvalidException {
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
            return new JSONArray();
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
                if (!substitutionScheduleDay.getSubstitutions().isEmpty()) {
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
                classes.add(gradesHashMap.get(classId));
            }
            substitution.setClasses(classes);
        }
        // Set type
        final String type = change.getString("aenderungsgrund").trim();
        substitution.setType(type);
        // Set color
        substitution.setColor(colorProvider.getColor(type));
        // Set covering teacher
        final String[] coveringTeacherIds = getSQLArray(change.getString("id_person_verantwortlich"));
        if (coveringTeacherIds.length > 0) {
            if (teachersHashMap == null) {
                throw new IOException("Change references a covering teacher but teachers are empty.");
            }
            final HashSet<String> teachers = new HashSet<>();
            for (String coveringTeacherId : coveringTeacherIds) {
                teachers.add(teachersHashMap.get(coveringTeacherId));
            }
            substitution.setTeachers(teachers);

        }
        // Set teacher
        final String[] teacherIds = getSQLArray(change.getString("id_person_verantwortlich_orig"));
        if (teacherIds.length > 0) {
            if (teachersHashMap == null) {
                throw new IOException("Change references a teacher but teachers are empty.");
            }
            final HashSet<String> coveringTeachers = new HashSet<>();
            for (String coveringTeacherId : coveringTeacherIds) {
                coveringTeachers.add(teachersHashMap.get(coveringTeacherId));
            }
            substitution.setPreviousTeachers(coveringTeachers);

        }
        //Set room
        if (!change.optString("raum").isEmpty()) {
            substitution.setRoom(change.optString("raum"));
        }
        if (!change.optString("raum_orig").isEmpty()) {
            substitution.setPreviousRoom(change.optString("raum_orig"));
        }
        //Set subject
        if (!change.optString("fach").isEmpty()) {
            substitution.setSubject(change.optString("fach"));
        }
        if (!change.optString("fach_orig").isEmpty()) {
            substitution.setPreviousSubject(change.optString("fach_orig"));
        }
        //Set description
        substitution.setDesc(change.getString("information").trim());

        final String startingHour = change.getString("zeit_von").replaceFirst("^0+(?!$)", "");
        final String endingHour = change.getString("zeit_bis").replaceFirst("^0+(?!$)", "");
        if (!startingHour.equals("") || !endingHour.equals("")) {
            String lesson = "";
            if (!startingHour.equals("") && endingHour.equals("")) {
                lesson = "Ab " + startingHour;
            }
            if (startingHour.equals("") && !endingHour.equals("")) {
                lesson = "Bis " + endingHour;
            }
            if (!startingHour.equals("") && !endingHour.equals("")) {
                lesson = startingHour + " - " + endingHour;
            }
            if (startingHour.equals(endingHour)) {
                lesson = startingHour;
            }
            substitution.setLesson(lesson);
        }
        return substitution;
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        final List<String> classesList = new ArrayList<>();
        if (grades == null) {
            return null;
        }
        for (int i = 0; i < grades.length(); i++) {
            final JSONObject grade = grades.getJSONObject(i);
            classesList.add(grade.getString("name"));
        }
        return classesList;
    }

    @Override
    public List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException {
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
}
