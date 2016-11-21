/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.AdditionalInfo;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import me.vertretungsplan.objects.authentication.SchoolNumberPasswordAuthenticationData;
import me.vertretungsplan.objects.credential.SchoolNumberPasswordCredential;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Parser for Untis and DaVinci substitution schedules served by
 * <a href="http://www.digitales-schwarzes-brett.de/">DSB</a>mobile
 * (<a href="http://mobile.dsbcontrol.de/">mobile.dsbcontrol.de</a>).
 * <p>
 * This parser can be accessed using <code>"dsbmobile"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>classes</code> (Array of Strings, required)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 *
 * <dt><code>type</code> (String, optional)</dt>
 * <dd>Can be set to either <code>"untis"</code> or <code>"davinci"</code> to specify which type of schedule is
 * used. By default, the parser tries to detect this automatically, but this does not always work.</dd>
 *
 * <dt><code>encoding</code> (String, optional)</dt>
 * <dd>The charset of the Untis/DaVinci schedule. DSBmobile itself always uses UTF-8, but the hosted HTML schedule can
 * also be ISO-8859-1. Default: UTF-8</dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link UntisCommonParser} (if it is an Untis
 * schedule).
 *
 * DSBmobile schedules always need a login using a school number and a password. You have to set a
 * {@link SchoolNumberPasswordAuthenticationData} which specifies the 6-digit school number.
 */
public class DSBMobileParser extends UntisCommonParser {

    private static final String URL = "https://app.dsbcontrol.de/JsonHandlerWeb.ashx/GetData";
    private static final String ENCODING = "UTF-8";
    private static final String PARAM_ENCODING = "encoding";
    private static final String PARAM_TYPE = "type";
    private JSONObject data;

    public DSBMobileParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }

    private static String decode(String input) throws IOException {
        byte[] inputBytes = Base64.decodeBase64(input);
        InputStream is = new GZIPInputStream(new ByteArrayInputStream(inputBytes));
        return IOUtils.toString(is, ENCODING);
    }

    private static String encode(String input) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        OutputStream gzipOs = new GZIPOutputStream(os);
        gzipOs.write(input.getBytes(ENCODING));
        gzipOs.close();
        byte[] outputBytes = os.toByteArray();
        return Base64.encodeBase64String(outputBytes);
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException,
            CredentialInvalidException {
        if (credential == null || !(credential instanceof SchoolNumberPasswordCredential)
                || ((SchoolNumberPasswordCredential) credential).getPassword() == null
                || ((SchoolNumberPasswordCredential) credential).getPassword().isEmpty()) {
            throw new IOException("no login");
        }
        String login = ((SchoolNumberPasswordAuthenticationData) scheduleData.getAuthenticationData()).getSchoolNumber();
        String password = ((SchoolNumberPasswordCredential) credential).getPassword();

        JSONObject data = getDataFromApi(login, password);

        int resultcode = data.getInt("Resultcode");
        if (resultcode == 1) {
            throw new CredentialInvalidException();
        } else if (resultcode != 0) throw new IOException("Resultcode " + resultcode);

        JSONObject timetablePage = getPageByType(data, "timetable");
        if (timetablePage == null) throw new IOException("no timetable page found");
        JSONArray timetableModules = timetablePage.getJSONObject("Root").getJSONArray("Childs" /* sic! */);

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        List<String> usedUrls = new ArrayList<>();
        for (int i = 0; i < timetableModules.length(); i++) {
            JSONArray timetableParts = timetableModules.getJSONObject(i).getJSONArray("Childs" /* sic! */);
            for (int j = 0; j < timetableParts.length(); j++) {
                JSONObject timetablePart = timetableParts.getJSONObject(j);
                if (timetablePart.getInt("ConType") != 6) continue;

                String url = timetablePart.getString("Detail");
                loadScheduleFromUrl(v, url, usedUrls);
                if (timetablePart.has("Date") && v.getLastChangeString() == null) {
                    v.setLastChange(DateTimeFormat.forPattern("dd.MM.yyyy HH:mm").parseLocalDateTime(
                            timetablePart.getString("Date")));
                }
            }
        }

        JSONObject newsPage = getPageByType(data, "news");
        if (newsPage != null) {
            JSONArray newsItems = newsPage.getJSONObject("Root").getJSONArray("Childs" /* sic! */);
            List<AdditionalInfo> infos = new ArrayList<>(newsItems.length());

            for (int i = 0; i < newsItems.length(); i++) {
                JSONObject newsItem = newsItems.getJSONObject(i);
                if (!newsItem.getString("Id").equals("00000000-0000-0000-0000-000000000000")) {
                    AdditionalInfo info = new AdditionalInfo();
                    info.setHasInformation(false);
                    info.setTitle(newsItem.getString("Title") + " (" + newsItem.getString("Date") + ")");
                    String message = newsItem.getString("Detail");
                    message = message.replaceAll("-+\\s*\\n+\\s*-+", "");
                    info.setText(message);
                    infos.add(info);
                }
            }

            v.getAdditionalInfos().addAll(infos);
        }

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());
        v.setWebsite("http://mobile.dsbcontrol.de/");

        return v;
    }

    private JSONObject getPageByType(JSONObject data, String type) throws JSONException {
        JSONArray resultMenuItems = data.getJSONArray("ResultMenuItems");
        JSONArray content = resultMenuItems.getJSONObject(0).getJSONArray("Childs" /* sic! */);
        for (int i = 0; i < content.length(); i++) {
            JSONObject child = content.getJSONObject(i);
            if (child.getString("MethodName").equals(type)) {
                return child;
            }
        }
        return null;
    }

    private JSONObject getDataFromApi(String login, String password)
            throws JSONException, IOException, CredentialInvalidException {
        JSONObject json = new JSONObject();
        json.put("AppId", "");
        json.put("PushId", "");
        json.put("UserId", login);
        json.put("UserPw", password);
        json.put("AppVersion", "0.8");
        json.put("Device", "WebApp");
        json.put("OsVersion",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2490.71 Safari/537.36");
        json.put("Language", "de");
        json.put("Date", ISODateTimeFormat.dateTime().print(DateTime.now()));
        json.put("LastUpdate", ISODateTimeFormat.dateTime().print(DateTime.now()));
        json.put("BundleId", "de.heinekingmedia.inhouse.dsbmobile.web");
        JSONObject payload = new JSONObject();
        JSONObject req = new JSONObject();
        req.put("Data", encode(json.toString()));
        req.put("DataType", 1);
        payload.put("req", req);

        JSONObject responseJson = new JSONObject(
                httpPost(URL, ENCODING, payload.toString(), ContentType.APPLICATION_JSON));
        String data = decode(responseJson.getString("d"));
        return new JSONObject(data);
    }

    private void loadScheduleFromUrl(SubstitutionSchedule v, String url, List<String> usedUrls)
            throws IOException, JSONException, CredentialInvalidException {
        usedUrls.add(url);
        String html = httpGet(url, data.has(PARAM_ENCODING) ? data.getString(PARAM_ENCODING) : "UTF-8");
        Document doc = Jsoup.parse(html);

        if (doc.title().toLowerCase().contains("untis") || doc.html().toLowerCase().contains("untis")
                || data.optString(PARAM_TYPE, "").equals("untis")) {
            if (doc.select(".mon_head").size() > 1) {
                for (int j = 0; j < doc.select(".mon_head").size(); j++) {
                    Document doc2 = Document.createShell(doc.baseUri());
                    doc2.body().appendChild(doc.select(".mon_head").get(j).clone());
                    Element next = doc.select(".mon_head").get(j).nextElementSibling();
                    if (next != null && next.tagName().equals("center")) {
                        doc2.body().appendChild(next.select(".mon_title").first().clone());
                        if (next.select("table:has(tr.list)").size() > 0)
                            doc2.body().appendChild(next.select("table:has(tr.list)").first());
                        if (next.select("table.info").size() > 0)
                            doc2.body().appendChild(next.select("table.info").first());
                    } else {
                        doc2.body().appendChild(doc.select(".mon_title").get(j).clone());
                        doc2.body().appendChild(doc.select("table:has(tr.list)").get(j).clone());
                    }
                    SubstitutionScheduleDay day = parseMonitorDay(doc2, data);
                    v.addDay(day);
                }
            } else {
                SubstitutionScheduleDay day = parseMonitorDay(doc, data);
                v.addDay(day);
            }
        } else if (doc.html().toLowerCase().contains("created by davinci")
                || data.optString(PARAM_TYPE, "").equals("davinci")) {
            Elements titles = doc.select("h2");
            Elements tables = doc.select("h2 + p + table");
            if (titles.size() != tables.size()) throw new IOException("Anzahl Überschriften != Anzahl Tabellen");
            for (int i = 0; i < titles.size(); i++) {
                SubstitutionScheduleDay day = new SubstitutionScheduleDay();
                String date = titles.get(i).text();
                day.setDateString(date);
                day.setDate(ParserUtils.parseDate(date));
                DaVinciParser.parseDaVinciTable(tables.get(i), day, colorProvider);
                v.addDay(day);
            }
        } else if (doc.text().matches(".*Für diesen Bereich.*wurde kein Inhalt bereitgestellt\\.")) {
            return;
        } else if (doc.select(".headline").text().contains("Pläne")) {
            // heinekingmedia schedule. Currently not supported, but skip without error because some schools have an
            // Untis schedule too
            return;
        } else {
            throw new IOException("Kein Untis- oder DaVinci-Vertretungsplan?");
        }

        if (doc.select("meta[http-equiv=refresh]").size() > 0) {
            Element meta = doc.select("meta[http-equiv=refresh]").first();
            String attr = meta.attr("content").toLowerCase();
            String redirectUrl = url.substring(0, url.lastIndexOf("/") + 1) + attr.substring(attr.indexOf("url=") + 4);
            if (!usedUrls.contains(redirectUrl)) {
                loadScheduleFromUrl(v, redirectUrl, usedUrls);
            }
        }
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }

}
