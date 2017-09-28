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
import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpResponseException;
import org.apache.http.entity.ContentType;
import org.joda.time.LocalDate;
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

    /**
     * URL of the school website
     */
    private String website;

    /**  */
    private JSONArray grades;
    /**  */
    private JSONArray teachers;
    /**  */
    private String authToken;

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
            grades = getGrades();
            teachers = getTeachers();
            final JSONArray changes = getChanges();

            substitutionSchedule.setClasses(getAllClasses());
            substitutionSchedule.setTeachers(getAllTeachers());
            substitutionSchedule.setWebsite(website);

            parseIphis(substitutionSchedule, changes, grades, teachers);
        }
        return substitutionSchedule;
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
        final String token = httpPost(api + "/login", "UTF-8", payload.toString(), ContentType.APPLICATION_JSON);

        try {
            final String key = Base64.encodeBase64String(jwt_key.getBytes());
            final Claims jwtToken = Jwts.parser().setSigningKey(key)
                    .parseClaimsJws(token).getBody();
            assert jwtToken.getSubject().equals("vertretungsplan.me");

            authToken = token;
            website = jwtToken.getIssuer();
        } catch (SignatureException e) {
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
     * Returns a JSONArray with all grades.
     */
    private JSONArray getGrades() throws IOException, JSONException, CredentialInvalidException {
        final String url = api + "/klassen";
        if (grades == null) {
            grades = getJSONArray(url);
        }
        return grades;
    }

    /**
     * Returns a JSONArray with all teachers.
     */
    private JSONArray getTeachers() throws IOException, JSONException, CredentialInvalidException {
        final String url = api + "/lehrer";
        if (teachers == null) {
            teachers = getJSONArray(url);
        }
        return teachers;
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
                    JSONArray teachers) throws IOException, JSONException {
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
        // Add changes to SubstitutionSchedule
        LocalDate currentDate = LocalDate.now();
        SubstitutionScheduleDay substitutionScheduleDay = new SubstitutionScheduleDay();
        substitutionScheduleDay.setDate(currentDate);
        for (int i = 0; i < changes.length(); i++) {
            final JSONObject change = changes.getJSONObject(i);
            final Substitution substitution = getSubstitution(change, coursesHashMap, teachersHashMap);
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
            substitutionScheduleDay.addSubstitution(substitution);
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
        final String coveringTeacherId = change.getString("id_person_verantwortlich");
        if (!coveringTeacherId.equals("0")) {
            if (teachersHashMap == null) {
                throw new IOException("Change references a covering teacher but teachers are empty.");
            }
            substitution.setTeacher(teachersHashMap.get(coveringTeacherId));
        }
        // Set teacher
        final String teacherId = change.getString("id_person_verantwortlich_orig");
        if (!teacherId.equals("{0}")) {
            if (teachersHashMap == null) {
                throw new IOException("Change references a teacher but teachers are empty.");
            }
            if (type.equals("Vertretung") || !coveringTeacherId.equals("0")) {
                substitution.setPreviousTeacher(teachersHashMap.get(teacherId));
            } else {
                substitution.setTeacher(teachersHashMap.get(teacherId));
            }

        }
        //Set room
        substitution.setRoom(change.optString("raum"));
        substitution.setPreviousRoom(change.optString("raum_orig"));
        //Set subject
        substitution.setSubject(change.optString("fach"));
        substitution.setPreviousSubject(change.optString("fach_orig"));
        //Set description
        substitution.setDesc(change.getString("information").trim());

        final String startingHour = change.getString("zeit_von").replaceFirst("^0+(?!$)", "").substring(0, 5);
        final String endingHour = change.getString("zeit_bis").replaceFirst("^0+(?!$)", "").substring(0, 5);
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
            if (!startingHour.equals(endingHour)) {
                lesson = startingHour;
            }
            substitution.setLesson(lesson);
        }
        return substitution;
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        final List<String> classes = new ArrayList<>();
        final JSONArray jsonClasses = getGrades();
        if (jsonClasses == null) {
            return null;
        }
        for (int i = 0; i < jsonClasses.length(); i++) {
            final JSONObject grade = jsonClasses.getJSONObject(i);
            classes.add(grade.getString("name"));
        }
        //Collections.sort(classes);
        return classes;
    }

    @Override
    public List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException {
        final List<String> teachers = new ArrayList<>();
        final JSONArray jsonTeachers = getTeachers();
        if (jsonTeachers == null) {
            return null;
        }
        for (int i = 0; i < jsonTeachers.length(); i++) {
            final JSONObject teacher = jsonTeachers.getJSONObject(i);
            teachers.add(teacher.getString("name"));
        }
        //Collections.sort(teachers);
        return teachers;
    }
}
