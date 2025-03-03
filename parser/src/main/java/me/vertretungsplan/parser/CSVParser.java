/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.paour.comparator.NaturalOrderComparator;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.AdditionalInfo;
import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.fluent.Request;
import org.jetbrains.annotations.NotNull;
import org.joda.time.LocalDateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Generic parser for substitution schedules in CSV format.
 * <p>
 * This parser can be accessed using <code>"csv"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * # Configuration parameters
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>url</code> (String, required)</dt>
 * <dd>The url of the CSV file to be fetched</dd>
 *
 * <dt><code>separator</code> (String, required)</dt>
 * <dd>The separator used in the CSV file (such as <code>","</code>, <code>";"</code> or <code>"\t"</code>)</dd>
 *
 * <dt><code>quote</code> (String, optional, default: ")</dt>
 * <dd>The quote character used in the CSV file to denote the start and end of strings in which additional separator characters should be ignored</dd>
 *
 * <dt><code>columns</code> (Array of Strings, required)</dt>
 * <dd>The order of columns used in the CSV file. Entries can be: <code>"lesson", "subject",
 * "previousSubject", "type", "type-entfall", "room", "previousRoom", "teacher", "previousTeacher", desc",
 * "desc-type", "class", "day", "stand", "ignore"</code></dd>
 *
 * <dt><code>classes</code> (Array of Strings, required if <code>classesUrl</code> not specified)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 *
 * <dt><code>website</code> (String, recommended)</dt>
 * <dd>The URL of a website where the substitution schedule can be seen online</dd>
 *
 * <dt><code>skipLines</code> (Integer, optional)</dt>
 * <dd>The number of lines to skip at the beginning of the CSV file. Default: <code>0</code></dd>
 *
 * <dt><code>classesUrl</code> (String, optional)</dt>
 * <dd>The URL of an additional CSV file containing the classes, one per line</dd>
 *
 * <dt><code>additionalUrl</code> (String, optional)</dt>
 * <dd>The URL of an additional CSV file containing the additional info. need additionalColumns</dd>
 *
 * <dt><code>additionalColumns</code> (String, optional)</dt>
 * <dd>The order of columns used in the additional info CSV file. Entries can be: <code>"title", "text"</code></dd>
 *
 * <dt><code>classRegex</code> (String, optional)</dt>
 * <dd>RegEx to modify the classes set on the schedule (in {@link #getSubstitutionSchedule()}, not
 * {@link #getAllClasses()}. The RegEx is matched against the class using {@link Matcher#find()}. If the RegEx
 * contains a group, the content of the first group {@link Matcher#group(int)} is used as the resulting class.
 * Otherwise, {@link Matcher#group()} is used. If the RegEx cannot be matched ({@link Matcher#find()} returns
 * <code>false</code>), the class is set to an empty string.
 * </dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules.
 */
public class CSVParser extends BaseParser {

    private static final String PARAM_SEPARATOR = "separator";
    private static final String PARAM_SKIP_LINES = "skipLines";
    private static final String PARAM_COLUMNS = "columns";
    private static final String PARAM_WEBSITE = "website";
    private static final String PARAM_ADDITIONAL_INFO_URL = "additionalUrl";
    private static final String PARAM_ADDITIONAL_INFO_COLUMNS = "additionalColumns";
    private static final String PARAM_CLASSES_URL = "classesUrl";
    private static final String PARAM_CLASSES = "classes";
    private static final String PARAM_URL = "url";
    private static final String PARAM_QUOTE = "quote";
    private JSONObject data;

    public CSVParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException,
            CredentialInvalidException {
        SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);
        String url = data.getString(PARAM_URL);

        if (url.startsWith("webdav")) {
            UserPasswordCredential credential = (UserPasswordCredential) this.credential;
            try {
                Sardine client = getWebdavClient(credential);
                String httpUrl = url.replaceFirst("webdav", "http");
                List<DavResource> files = client.list(httpUrl);
                for (DavResource file : files) {
                    if (!file.isDirectory() && !file.getName().startsWith(".")) {
                        LocalDateTime modified = new LocalDateTime(file.getModified());
                        if (schedule.getLastChange() == null || schedule.getLastChange().isBefore(modified)) {
                            schedule.setLastChange(modified);
                        }
                        InputStream stream = client.get(new URI(httpUrl).resolve(file.getHref()).toString());
                        parseCSV(IOUtils.toString(stream, "UTF-8"), schedule);
                    }
                }
            } catch (GeneralSecurityException | URISyntaxException e) {
                throw new IOException(e);
            }
        } else {
            new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);
            String response = httpGet(url);
            schedule = parseCSV(response, schedule);
        }

        if (data.has(PARAM_ADDITIONAL_INFO_URL) && data.has(PARAM_ADDITIONAL_INFO_COLUMNS)) {
            String additionalUrl = data.getString(PARAM_ADDITIONAL_INFO_URL);
            if (additionalUrl.startsWith("webdav")) {
                UserPasswordCredential credential = (UserPasswordCredential) this.credential;
                try {
                    Sardine client = getWebdavClient(credential);
                    String httpUrl = additionalUrl.replaceFirst("webdav", "http");
                    List<DavResource> files = client.list(httpUrl);
                    for (DavResource file : files) {
                        if (!file.isDirectory() && !file.getName().startsWith(".")) {
                            LocalDateTime modified = new LocalDateTime(file.getModified());
                            if (schedule.getLastChange() == null || schedule.getLastChange().isBefore(modified)) {
                                schedule.setLastChange(modified);
                            }
                            InputStream stream = client.get(new URI(httpUrl).resolve(file.getHref()).toString());
                            schedule = parseCSVAdditionalInfos(IOUtils.toString(stream, "UTF-8"), schedule);
                        }
                    }
                } catch (GeneralSecurityException | URISyntaxException e) {
                    throw new IOException(e);
                }
            } else {
                new LoginHandler(scheduleData, credential, cookieProvider).handleLogin(executor, cookieStore);
                String additionalResponse = httpGet(additionalUrl);
                schedule = parseCSVAdditionalInfos(additionalResponse, schedule);
            }
        }

        return schedule;
    }

    @NotNull
    SubstitutionSchedule parseCSVAdditionalInfos(String response, SubstitutionSchedule schedule)
            throws JSONException, IOException, CredentialInvalidException {
        String[] lines = response.split("\n");

        String separator = data.getString(PARAM_SEPARATOR);
        String quote = data.optString(PARAM_QUOTE, "\"");
        String regex = separator + "(?=([^" + quote + "]*\"[^" + quote + "]*" + quote + ")" + "*[^" + quote + "]*$)";
        JSONArray columnsArray = data.getJSONArray(PARAM_ADDITIONAL_INFO_COLUMNS);
        for (int i = data.optInt(PARAM_SKIP_LINES, 0); i < lines.length; i++) {
            String[] columns = lines[i].split(regex);
            AdditionalInfo info = new AdditionalInfo();

            int j = 0;
            for (String column:columns) {
                // ignore blank columns after last valid column
                if (j >= columnsArray.length() && column.trim().isEmpty()) continue;

                // remove quotes
                if (column.startsWith(quote) && column.endsWith(quote)) {
                    column = column.substring(quote.length(), column.length() - quote.length());
                }

                String type = columnsArray.getString(j);
                switch (type) {
                    case "title":
                        info.setTitle(column);
                        break;
                    case "text":
                        info.setText(column);
                        break;
                    case "ignore":
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown column type: " + type);
                }
                j++;
            }
            if (info.getText() != null && !info.getText().trim().equals("")) {
                Boolean isDayMessage = false;
                for (SubstitutionScheduleDay day : schedule.getDays()) {
                    if (day.getDate().equals(ParserUtils.parseDate(info.getTitle()))) {
                        day.addMessage(info.getText());
                        isDayMessage = true;
                    }
                }
                if (!isDayMessage) {
                    info.setFromSchedule(true);
                    schedule.getAdditionalInfos().add(info);
                }
            }
        }
        return schedule;
    }

    @NotNull
    SubstitutionSchedule parseCSV(String response, SubstitutionSchedule schedule)
            throws JSONException, IOException, CredentialInvalidException {
        String[] lines = response.split("\n");

        String separator = data.getString(PARAM_SEPARATOR);
        String quote = data.optString(PARAM_QUOTE, "\"");
        String regex = separator + "(?=([^" + quote + "]*\"[^" + quote + "]*" + quote + ")" + "*[^" + quote + "]*$)";
        JSONArray columnsArray = data.getJSONArray(PARAM_COLUMNS);
        for (int i = data.optInt(PARAM_SKIP_LINES, 0); i < lines.length; i++) {
            String[] columns = lines[i].split(regex);
            Substitution v = new Substitution();
            String dayName = null;
            String stand = "";
            int j = 0;
            for (String column:columns) {
                // ignore blank columns after last valid column
                if (j >= columnsArray.length() && column.trim().isEmpty()) continue;

                // remove quotes
                if (column.startsWith(quote) && column.endsWith(quote)) {
                    column = column.substring(quote.length(), column.length() - quote.length());
                }

                String type = columnsArray.getString(j);
                switch (type) {
                    case "lesson":
                        v.setLesson(column);
                        break;
                    case "subject":
                        v.setSubject(column);
                        break;
                    case "previousSubject":
                        v.setPreviousSubject(column);
                        break;
                    case "type":
                        v.setType(column);
                        v.setColor(colorProvider.getColor(column));
                        break;
                    case "type-entfall":
                        if (column.equals("x")) {
                            v.setType("Entfall");
                            v.setColor(colorProvider.getColor("Entfall"));
                        } else {
                            v.setType("Vertretung");
                            v.setColor(colorProvider.getColor("Vertretung"));
                        }
                        break;
                    case "room":
                        v.setRoom(column);
                        break;
                    case "teacher":
                        v.setTeacher(column);
                        break;
                    case "previousTeacher":
                        v.setPreviousTeacher(column);
                        break;
                    case "desc":
                        v.setDesc(column);
                        break;
                    case "desc-type":
                        v.setDesc(column);
                        String recognizedType = recognizeType(column);
                        v.setType(recognizedType);
                        v.setColor(colorProvider.getColor(recognizedType));
                        break;
                    case "previousRoom":
                        v.setPreviousRoom(column);
                        break;
                    case "class":
                        UntisCommonParser.handleClasses(data, v, column, getAllClasses());
                        break;
                    case "day":
                        dayName = column;
                        break;
                    case "stand":
                        stand = column;
                        break;
                    case "ignore":
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown column type: " + type);
                }
                j++;
            }
            if (v.getType() == null) {
                v.setType("Vertretung");
                v.setColor(colorProvider.getColor("Vertretung"));
            }

            if (dayName != null && !dayName.isEmpty()) {
                SubstitutionScheduleDay day = new SubstitutionScheduleDay();
                day.setDateString(dayName);
                day.setDate(ParserUtils.parseDate(dayName));
                day.setLastChangeString(stand);
                day.setLastChange(ParserUtils.parseDateTime(stand));
                day.addSubstitution(v);
                schedule.addDay(day);
            }
        }

        if (scheduleData.getData().has(PARAM_WEBSITE)) {
            schedule.setWebsite(scheduleData.getData().getString(PARAM_WEBSITE));
        }

        schedule.setClasses(getAllClasses());
        schedule.setTeachers(getAllTeachers());

        return schedule;
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException {
        if (data.has(PARAM_CLASSES_URL)) {
            String url = data.getString(PARAM_CLASSES_URL);
            String response = executor.execute(Request.Get(url)).returnContent().asString();
            List<String> classes = new ArrayList<>();
            for (String string:response.split("\n")) {
                classes.add(string.trim());
            }
            Collections.sort(classes, new NaturalOrderComparator());
            return classes;
        } else {
            return getClassesFromJson();
        }
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }
}
