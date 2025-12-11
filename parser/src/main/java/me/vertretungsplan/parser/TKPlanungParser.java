package me.vertretungsplan.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.paour.comparator.NaturalOrderComparator;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.AdditionalInfo;
import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import me.vertretungsplan.objects.credential.UserPasswordCredential;

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
     * array of notifications retrieved from the api
     */
    private JSONArray notifications;
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
        getNotifications();
        
        final String url = api + "/substitutions";
        JSONArray changes = getJSON(url);
        
        // Add changes to SubstitutionSchedule
        for (int i = 0; i < changes.length(); i++) {
            SubstitutionScheduleDay substitutionScheduleDay = new SubstitutionScheduleDay();
            JSONObject change = changes.getJSONObject(i);
            LocalDate substitutionDate = new LocalDate(change.getString("date"));
            substitutionScheduleDay.setDate(substitutionDate);

            Substitution substitution = new Substitution();

            String type = change.getString("substitutionType");
            substitution.setType(type);
            substitution.setColor(colorProvider.getColor(type));

            if (type.equals("Zusammengelegt") || type.equals("Fällt aus")) {
                substitution.setClasses(jsonArrayToSet(change.getJSONArray("originalClassNames")));
            } else {
                substitution.setClasses(jsonArrayToSet(change.getJSONArray("classNames")));
            }

            substitution.setTeachers(jsonArrayToSet(change.getJSONArray("coveringTeacherNames")));
            substitution.setPreviousTeachers(jsonArrayToSet(change.getJSONArray("originalTeacherNames")));

            substitution.setSubject(change.getString("subject"));
            if (!change.optString("originalSubject").isEmpty() && change.optString("originalSubject") != "null") {
                substitution.setPreviousSubject(change.optString("originalSubject"));
            }            

            if (type.equals("Fällt aus")) {
                substitution.setRoom(jsonArrayToPlainString(change.getJSONArray("originalRoomNames")));
            } else {
                substitution.setRoom(jsonArrayToPlainString(change.getJSONArray("roomNames")));
                substitution.setPreviousRoom(jsonArrayToPlainString(change.getJSONArray("originalRoomNames")));
            }

            //String start = change.getString("startTime").substring(0, 5);
            //String end = change.getString("endTime").substring(0, 5);
            //substitution.setLesson(start + " - " + end);
            substitution.setLesson(change.getString("lesson"));

            if (!change.optString("description").isEmpty() && change.optString("description") != "null") {
                substitution.setDesc(change.optString("description"));
            }

            substitutionScheduleDay.addSubstitution(substitution);
            substitutionSchedule.addDay(substitutionScheduleDay);
        }

        // Add Messages
        for (int i = 0; i < notifications.length(); i++) {
            JSONObject notification = notifications.getJSONObject(i);
            if (notification.has("date") && !notification.isNull("date") && !notification.getString("date").trim().isEmpty() && notification.optString("description") != "null") {
                LocalDate substitutionDate = new LocalDate(notification.getString("date"));
                SubstitutionScheduleDay substitutionScheduleDay = new SubstitutionScheduleDay();
                substitutionScheduleDay.setDate(substitutionDate);
                String message = notification.getString("message").trim();
                String title = notification.getString("title").trim();
                if (title != "") {
                    message = "<b>" + title + "</b>: " + message;
                }
                substitutionScheduleDay.addMessage(message);
                substitutionSchedule.addDay(substitutionScheduleDay);
            } else {
                AdditionalInfo additionalInfo = new AdditionalInfo();
                additionalInfo.setTitle(notification.getString("title").trim());
                additionalInfo.setText(notification.getString("message").trim());
                additionalInfo.setHasInformation(notification.getBoolean("sendNotification"));
                additionalInfo.setFromSchedule(true);
                substitutionSchedule.getAdditionalInfos().add(additionalInfo);
            }
        }

        substitutionSchedule.setClasses(getAllClasses());
        substitutionSchedule.setTeachers(getAllTeachers());
        substitutionSchedule.setLastChange(lastUpdate);
        substitutionSchedule.setWebsite(website);

        return substitutionSchedule;
    }

    /**
     * Returns a JSONArray with all notifications.
     */
    private void getNotifications() throws IOException, JSONException, CredentialInvalidException {
        if (notifications == null) {
            final String url = api + "/notification";
            notifications = getJSON(url);
        }
    }

    /**
     * Returns a JSONArray with all grades.
     */
    private void getGrades() throws IOException, JSONException, CredentialInvalidException {
        if (grades == null) {
            final String url = api + "/classes";
            grades = getJSON(url);
        }
    }

    /**
     * Returns a JSONArray with all teachers.
     */
    private void getTeachers() throws IOException, CredentialInvalidException {
        if (teachers == null) {
            final String url = api + "/teachers";
            teachers = getJSON(url);
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

    private JSONArray getJSON(String url) throws IOException, CredentialInvalidException {
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

    @Override
    protected String executeRequest(String encoding, Request request)
            throws IOException, CredentialInvalidException {
        try {
            HttpResponse httpResponse = executor.execute(request).returnResponse();
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == 401 || statusCode == 403) {
                throw new CredentialInvalidException();
            }
            if (lastUpdate == null) {
                try {
                    if (httpResponse.containsHeader("last-modified")) {
                        String lastModified = httpResponse.getHeaders("last-modified")[0].getValue();
                        DateTimeFormatter fmt = DateTimeFormat
                            .forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                            .withLocale(Locale.GERMAN)
                            .withZoneUTC();
                        DateTime utcDateTime = fmt.parseDateTime(lastModified);
                        DateTime berlinDateTime = utcDateTime.withZone(DateTimeZone.forID("Europe/Berlin"));
                        lastUpdate = berlinDateTime.toLocalDateTime();
                    }
                }  catch (Exception e) {
                    // ignore
                }
            }
            byte[] bytes = EntityUtils.toByteArray(httpResponse.getEntity());
            encoding = getEncoding(encoding, bytes);
            return new String(bytes, encoding);
        } catch (HttpResponseException e) {
            handleHttpResponseException(e);
            return null;
        } finally {
            encodingDetector.reset();
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
