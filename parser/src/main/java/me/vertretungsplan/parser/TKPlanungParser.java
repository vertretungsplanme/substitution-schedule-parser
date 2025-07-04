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
            LocalDate substitutionDate = new LocalDate(change.getString("date"));
            substitutionScheduleDay.setDate(substitutionDate);

            Substitution substitution = new Substitution();

            String type = change.getString("substitutionType");
            substitution.setType(type);
            substitution.setColor(colorProvider.getColor(type));

            if (type.equals("Zusammengelegt") || type.equals("Abgesagt")) {
                substitution.setClasses(jsonArrayToSet(change.getJSONArray("originalClassNames")));
            } else {
                substitution.setClasses(jsonArrayToSet(change.getJSONArray("classNames")));
            }

            substitution.setTeachers(jsonArrayToSet(change.getJSONArray("coveringTeacherNames")));
            substitution.setPreviousTeachers(jsonArrayToSet(change.getJSONArray("originalTeacherNames")));

            substitution.setSubject(change.getString("subject"));
            if (!change.optString("originalSubject").isEmpty()) {
                substitution.setPreviousSubject(change.optString("originalSubject"));
            }            

            if (type.equals("Abgesagt")) {
                substitution.setRoom(jsonArrayToPlainString(change.getJSONArray("originalRoomNames")));
            } else {
                substitution.setRoom(jsonArrayToPlainString(change.getJSONArray("roomNames")));
                substitution.setPreviousRoom(jsonArrayToPlainString(change.getJSONArray("originalRoomNames")));
            }

            //String start = change.getString("startTime").substring(0, 5);
            //String end = change.getString("endTime").substring(0, 5);
            //substitution.setLesson(start + " - " + end);
            substitution.setLesson(change.getString("lesson"));

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
        substitutionSchedule.getAdditionalInfos().addAll(infos);
        // Add AdditionalInfo absentTeachers
        JSONArray absentTeachersDays = (JSONArray) substitutions.optJSONArray("absentTeachers");
            if (absentTeachersDays != null) {
            for (int i = 0; i < absentTeachersDays.length(); i++) {
                JSONObject absentTeachersDay = absentTeachersDays.getJSONObject(i);
                String absentTeachersDate = absentTeachersDay.getString("date");
                String absentTeachersMessage = absentTeachersDay.getString("message");
                if (!absentTeachersMessage.isBlank()) {
                    substitutionScheduleDay = new SubstitutionScheduleDay();
                    LocalDate substitutionDate = new LocalDate(absentTeachersDate);
                    substitutionScheduleDay.setDate(substitutionDate);
                    substitutionScheduleDay.addMessage("Abwesend: " + absentTeachersMessage);
                    substitutionSchedule.addDay(substitutionScheduleDay);
                }
            }
        }
        // Add AdditionalInfo info
        JSONArray infoDays = (JSONArray) substitutions.getJSONArray("info");
        for (int i = 0; i < infoDays.length(); i++) {
            JSONObject infoDay = infoDays.getJSONObject(i);
            String infoDate = infoDay.getString("date");
            String infoMessage = infoDay.getString("message");
            if (!infoMessage.isBlank()) {
                substitutionScheduleDay = new SubstitutionScheduleDay();
                LocalDate substitutionDate = new LocalDate(infoDate);
                substitutionScheduleDay.setDate(substitutionDate);
                substitutionScheduleDay.addMessage(infoMessage);
                substitutionSchedule.addDay(substitutionScheduleDay);
            }
        }

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
