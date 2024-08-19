/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2023 Tobias Knipping
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import io.jsonwebtoken.SignatureException;
import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.*;
import me.vertretungsplan.objects.credential.PasswordCredential;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.http.client.HttpResponseException;
import org.apache.http.entity.ContentType;
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
 * Parser for substitution schedules from VPO.
 * <p>
 * More information can be found on the <a href="https://vpo.de">official website</a>.
 * <p>
 * This parser can be accessed using <code>"vpo"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>url</code> (String, required)</dt>
 * <dd>The URL of the VPO Instance.</dd>
 *
 * <dt><code>jwt_key</code> (String, required)</dt>
 * <dd>The key used for signing the JWT</dd>
 * </dl>
 *
 * You have to use a {@link me.vertretungsplan.objects.authentication.PasswordAuthenticationData} because all
 * schedules on VPO are protected by a login.
 */
public class VPOParser extends BaseParser {

    private static final String PARAM_URL = "url";
    private static final String PARAM_IS_PERSONAL = "isPersonal";

    /**
     * URL of given VPO instance
     */
    private String api;

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

    public VPOParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        JSONObject data = scheduleData.getData();
        try {
            api = "https://" + data.getString(PARAM_URL) + "/app";
            website = "https://" + data.getString(PARAM_URL);
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
            substitutionSchedule.setWebsite(website + "/loginPasswordOnly");

            parseVPO(substitutionSchedule, changes, grades, teachers, messages);
        }
        return substitutionSchedule;
    }

    @Override
    public LocalDateTime getLastChange() throws IOException, JSONException, CredentialInvalidException {
        if (lastUpdate == null) {
            login();
        }
        return lastUpdate;
    }

    private Boolean login() throws CredentialInvalidException, IOException {
        String password = "";
        String username = "";
        if (credential.getClass() == PasswordCredential.class) {
            final PasswordCredential PasswordCredential = (PasswordCredential) credential;
            password = PasswordCredential.getPassword();
        } else if (credential.getClass() == UserPasswordCredential.class) {
            final UserPasswordCredential UserPasswordCredential = (UserPasswordCredential) credential;
            password = UserPasswordCredential.getPassword();
            username = UserPasswordCredential.getUsername();
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("user", username);
            payload.put("type", scheduleData.getType());
            payload.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        final String httpResponse = httpPost(api + "/login", "UTF-8", payload.toString(), ContentType.APPLICATION_JSON);
        final JSONObject token;
        try {
            token = new JSONObject(httpResponse);
            authToken = token.getString("access_token");
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

        final String url = api + "/changes"; //" + startBy + "/" + endBy;
        return getJSONArray(url);
    }

    /**
     * Returns a JSONArray with all messages.
     */
    private void getMessages() throws IOException, JSONException, CredentialInvalidException {
        if (messages == null) {
            final String url = api + "/messages";
            messages = getJSONArray(url);
        }
    }

    /**
     * Returns a JSONArray with all grades.
     */
    private void getGrades() throws IOException, JSONException, CredentialInvalidException {
        if (grades == null) {
            final String url = api + "/grades";
            grades = getJSONArray(url);
        }
    }

    /**
     * Returns a JSONArray with all teachers.
     */
    private void getTeachers() throws IOException, CredentialInvalidException {
        if (teachers == null) {
            final String url = api + "/teachers";
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

    void parseVPO(SubstitutionSchedule substitutionSchedule, JSONArray changes, JSONArray grades,
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
                coursesHashMap.put(grade.get("id").toString(), grade.getString("name"));
            }
        }
        // Link teacher IDs to their names
        HashMap<String, String> teachersHashMap = null;
        if (teachers != null) {
            teachersHashMap = new HashMap<>();
            for (int i = 0; i < teachers.length(); i++) {
                JSONObject teacher = teachers.getJSONObject(i);
                teachersHashMap.put(teacher.get("id").toString(), teacher.getString("name"));
            }
        }

        // Add Messages
        List<AdditionalInfo> infos = new ArrayList<>(messages.length());

        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);

            if (!message.has("date")) {
                AdditionalInfo info = new AdditionalInfo();
                info.setHasInformation(false);
                info.setTitle(message.getString("title").trim());
                info.setText(message.getString("message").trim());
                info.setFromSchedule(true);
                infos.add(info);
            }
        }

        substitutionSchedule.getAdditionalInfos().addAll(infos);
        substitutionSchedule.setLastChange(lastUpdate);

        // Add changes to SubstitutionSchedule
        LocalDate currentDate = LocalDate.now();
        SubstitutionScheduleDay substitutionScheduleDay = new SubstitutionScheduleDay();
        substitutionScheduleDay.setDate(currentDate);
        for (int i = 0; i < changes.length(); i++) {
            final JSONObject change = changes.getJSONObject(i);
            final LocalDate substitutionDate = new LocalDate(change.getString("date"));

            // If starting date of change does not equal date of SubstitutionScheduleDay
            if (!substitutionDate.isEqual(currentDate)) {
                for (int m = 0; i < messages.length(); m++) {
                    JSONObject message = messages.getJSONObject(m);

                    if (message.has("date")) {
                        final LocalDate messageDate = new LocalDate(message.getString("date"));
                        if (messageDate.isEqual(currentDate)) {
                            substitutionScheduleDay.addMessage("<b>" + message.optString("title") + "</b><br />" + message.optString("message"));
                        }
                    }
                }


                if (!substitutionScheduleDay.getSubstitutions().isEmpty()
                        || !substitutionScheduleDay.getMessages().isEmpty()) {
                    substitutionSchedule.addDay(substitutionScheduleDay);
                }
                substitutionScheduleDay = new SubstitutionScheduleDay();
                substitutionScheduleDay.setDate(substitutionDate);
                currentDate = substitutionDate;
            }

            final Substitution substitution = getSubstitution(change, coursesHashMap, teachersHashMap);

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
        if (change.getString("color") != null) {
            substitution.setColor(change.getString("color"));
        } else {
            substitution.setColor(colorProvider.getColor(type));
        }
        // Set covering teacher
        final String[] coveringTeacherIds = getSQLArray(change.getString("id_person_verantwortlich"));
        if (coveringTeacherIds.length > 0) {
            if (teachersHashMap == null) {
                throw new IOException("Change references a covering teacher but teachers are empty.");
            }
            final HashSet<String> teachers = new HashSet<>();
            for (String coveringTeacherId : coveringTeacherIds) {
                if (!coveringTeacherId.equalsIgnoreCase("null") && teachersHashMap.get(coveringTeacherId) != null) {
                    teachers.add(teachersHashMap.get(coveringTeacherId));
                }
            }
            substitution.setTeachers(teachers);
        }
        // Set teacher
        final String[] teacherIds = getSQLArray(change.getString("id_person_verantwortlich_orig"));
        final HashSet<String> coveringTeachers = new HashSet<>();
        if (teacherIds.length > 0) {
            if (teachersHashMap == null) {
                throw new IOException("Change references a teacher but teachers are empty.");
            }
            final HashSet<String> teachers = new HashSet<>();
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
        Collections.sort(classesList, new NaturalOrderComparator());
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

    @Override
    public boolean isPersonal() {
        return isPersonal;
    }
}
