package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.*;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpResponseException;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.util.JSONPObject;
import com.paour.comparator.NaturalOrderComparator;

import java.io.IOException;
import java.util.*;

public class TKPlanungParser extends BaseParser {
    private static final String PARAM_URL = "url";
    private static final String PARAM_UUID = "uuid";

    /**
     * URL of given TK Planung instance
     */
    private String api;
    /**
     * UUID of given TK Planung instance
     */
    private String uuid;
    /**
     * custom website instance
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
     * hold the lastUpdate Date
     */
    private LocalDateTime lastUpdate;

    public TKPlanungParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        JSONObject data = scheduleData.getData();
        try {
            api = "https://" + data.getString(PARAM_URL) + "/v1/app";
            uuid = data.getString(PARAM_UUID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        final SubstitutionSchedule substitutionSchedule = SubstitutionSchedule.fromData(scheduleData);

        getGrades();
        getTeachers();
        getMessages();
        
        final String url = api + "/substitutions";
        JSONObject substitutions = (JSONObject) getJSON(url);
        JSONArray changes = (JSONArray) substitutions.getJSONArray("changes");
        
        // Add changes to SubstitutionSchedule
        SubstitutionScheduleDay substitutionScheduleDay = new SubstitutionScheduleDay();
        for (int i = 0; i < changes.length(); i++) {
            JSONObject change = changes.getJSONObject(i);
            LocalDate substitutionDate = new LocalDate(change.getString("date").substring(0, 10));
            substitutionScheduleDay.setDate(substitutionDate);

            Substitution substitution = new Substitution();

            substitution.setClasses(jsonArrayToSet(change.getJSONArray("classNames")));

            String type = change.getString("substitutionType");
            substitution.setType(type);
            substitution.setColor(colorProvider.getColor(type));

            substitution.setTeachers(jsonArrayToSet(change.getJSONArray("coveringTeacherNames")));
            substitution.setPreviousTeachers(jsonArrayToSet(change.getJSONArray("originalTeacherNames")));

            substitution.setLesson(change.getString("subject"));
            if (!change.optString("originalSubject").isEmpty()) {
                substitution.setPreviousSubject(change.optString("originalSubject"));
            }            

            substitution.setRoom(jsonArrayToPlainString(change.getJSONArray("roomNames")));
            substitution.setPreviousRoom(jsonArrayToPlainString(change.getJSONArray("originalRoomNames")));

            String start = change.getString("startTime").substring(11, 16);
            String end = change.getString("endTime").substring(11, 16);
            substitution.setLesson(start + " - " + end);

            if (!change.optString("description").isEmpty()) {
                substitution.setDesc(change.optString("description"));
            }

            substitutionScheduleDay.addSubstitution(substitution);
            substitutionSchedule.addDay(substitutionScheduleDay);
        }

        // Add Messages
        List<AdditionalInfo> infos = new ArrayList<>(messages.length());
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            AdditionalInfo additionalInfo = new AdditionalInfo();
            additionalInfo.setHasInformation(message.getBoolean("sendNotification"));
            additionalInfo.setTitle(message.getString("title").trim());
            additionalInfo.setText(message.getString("message").trim());
            additionalInfo.setFromSchedule(true);
            infos.add(additionalInfo);
        }
        // Add AdditionalInfo absentTeachers
        if (!substitutions.optString("absentTeachers").isEmpty()) {
            AdditionalInfo absentTeachersInfo = new AdditionalInfo();
            absentTeachersInfo.setHasInformation(true);
            absentTeachersInfo.setTitle("Abwesende Lehrer");
            absentTeachersInfo.setText(substitutions.optString("absentTeachers"));
            absentTeachersInfo.setFromSchedule(true);
            infos.add(absentTeachersInfo);
        }
        // Add AdditionalInfo info
        if (!substitutions.optString("info").isEmpty()) {
            AdditionalInfo absentTeachersInfo = new AdditionalInfo();
            absentTeachersInfo.setHasInformation(true);
            absentTeachersInfo.setTitle("Infos");
            absentTeachersInfo.setText(substitutions.optString("info"));
            absentTeachersInfo.setFromSchedule(true);
            infos.add(absentTeachersInfo);
        }
        
        substitutionSchedule.getAdditionalInfos().addAll(infos);

        substitutionSchedule.setClasses(getAllClasses());
        substitutionSchedule.setTeachers(getAllTeachers());
        substitutionSchedule.setWebsite(website);

        return substitutionSchedule;
    }

    /**
     * Returns a JSONArray with all messages.
     */
    private void getMessages() throws IOException, JSONException, CredentialInvalidException {
        if (messages == null) {
            final String url = api + "/notification";
            messages = (JSONArray) getJSON(url);
        }
    }

    /**
     * Returns a JSONArray with all grades.
     */
    private void getGrades() throws IOException, JSONException, CredentialInvalidException {
        if (grades == null) {
            final String url = api + "/classes";
            grades = (JSONArray) getJSON(url);
        }
    }

    /**
     * Returns a JSONArray with all teachers.
     */
    private void getTeachers() throws IOException, CredentialInvalidException {
        if (teachers == null) {
            final String url = api + "/teachers";
            teachers = (JSONArray) getJSON(url);
        }
    }

    public Set<String> jsonArrayToSet(JSONArray jsonArray) throws JSONException {
        Set<String> resultSet = new HashSet<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            resultSet.add(jsonArray.getString(i));
        }
        return resultSet;
    }

    public String jsonArrayToPlainString(JSONArray jsonArray) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.append(jsonArray.optString(i));
            if (i < jsonArray.length() - 1) {
                builder.append(" & ");
            }
        }
        return builder.toString();
    }

    private Object getJSON(String url) throws IOException, CredentialInvalidException {
        try {
            final UserPasswordCredential userPasswordCredential = (UserPasswordCredential) credential;
            final String username = userPasswordCredential.getUsername();
            final String password = userPasswordCredential.getPassword();
            
            String auth = username + ":" + password;
            String encodedAuth = Base64.encodeBase64String(auth.getBytes());
            String authHeader = "Basic " + encodedAuth;

            Map<String, String> headers = new HashMap<>();
            headers.put("x-vplan-app", "true");
            headers.put("x-planung-organization", uuid);
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");
            headers.put("Authorization", authHeader); 

            final String httpResponse = httpGet(url, "UTF-8", headers);
            try {
                return new JSONArray(httpResponse);
            } catch (JSONException e) {
                return new JSONObject(httpResponse);
            }
        } catch (HttpResponseException httpResponseException) {
            if (httpResponseException.getStatusCode() == 404) {
                return null;
            }
            throw httpResponseException;
        } catch (JSONException e) {
            throw new IOException(e);
        }
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
    public LocalDateTime getLastChange() throws IOException, JSONException, CredentialInvalidException {
        return lastUpdate;
    }

    @Override
    public boolean isPersonal() {
        return true;
    }
}
