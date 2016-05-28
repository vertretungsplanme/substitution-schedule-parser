/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DSBLightParser extends UntisCommonParser {

    private static final String BASE_URL = "https://light.dsbcontrol.de/DSBlightWebsite/Homepage/";
    private static final String ENCODING = "UTF-8";

    private JSONObject data;

    public DSBLightParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        data = scheduleData.getData();
    }


    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException,
            JSONException, CredentialInvalidException {
        String id = data.getString("id");
        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        Map<String, String> referer = new HashMap<String, String>();
        referer.put("Referer", BASE_URL + "/Player.aspx?ID=" + id);

        String response = httpGet(BASE_URL + "/Player.aspx?ID=" + id, ENCODING, referer);
        Document doc = Jsoup.parse(response);
        // IFrame.aspx
        String iframeUrl = doc.select("iframe").first().attr("src");

        response = httpGet(iframeUrl, ENCODING, referer);

        doc = Jsoup.parse(response);

        if (data.has("login") && data.getBoolean("login")) {
            if (!(credential instanceof UserPasswordCredential)) {
                throw new IllegalArgumentException("no login");
            }
            String username = ((UserPasswordCredential) credential).getUsername();
            String password = ((UserPasswordCredential) credential).getPassword();

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("__VIEWSTATE", doc.select(
                    "#__VIEWSTATE").attr("value")));
            params.add(new BasicNameValuePair("__VIEWSTATEGENERATOR", doc.select(
                    "#__VIEWSTATEGENERATOR").attr("value")));
            params.add(new BasicNameValuePair("__EVENTVALIDATION", doc.select(
                    "#__EVENTVALIDATION").attr("value")));
            params.add(new BasicNameValuePair("ctl02$txtBenutzername", username));
            params.add(new BasicNameValuePair("ctl02$txtPasswort", password));
            params.add(new BasicNameValuePair("ctl02$btnLogin", "weiter"));
            response = httpPost(iframeUrl, ENCODING, params, referer);
            doc = Jsoup.parse(response);
            if (doc.select("#ctl02_lblLoginFehlgeschlagen").size() > 0) throw new CredentialInvalidException();
        }
        Pattern regex = Pattern.compile("location\\.href=\"([^\"]*)\"");

        for (Element iframe : doc.select("iframe")) {
            // PreProgram.aspx
            String response2 = httpGet(iframe.attr("src"), ENCODING, referer);
            Matcher matcher = regex.matcher(response2);
            if (matcher.find()) {
                // Program.aspx
                String url = matcher.group(1);
                parseProgram(url, v, referer);
            } else {
                throw new IOException("URL nicht gefunden");
            }
        }

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());
        v.setWebsite(BASE_URL + "/Player.aspx?ID=" + id);

        return v;
    }

    private void parseProgram(String url, SubstitutionSchedule schedule, Map<String, String> referer) throws
            IOException, JSONException {
        parseProgram(url, schedule, referer, null);
    }

    private void parseProgram(String url, SubstitutionSchedule schedule, Map<String, String> referer, String
            firstUrl) throws IOException, JSONException {
        String response = httpGet(url, ENCODING, referer);
        Document doc = Jsoup.parse(response, url);
        if (doc.select("iframe").attr("src").equals(firstUrl))
            return;
        for (Element iframe : doc.select("iframe")) {
            // Data
            parseDay(iframe.attr("src"), referer, schedule, iframe.attr("src"));
        }
        if (doc.select("#hlNext").size() > 0) {
            String nextUrl = doc.select("#hlNext").first().attr("abs:href");
            if (firstUrl == null)
                parseProgram(nextUrl, schedule, referer, doc.select("iframe").attr("src"));
            else
                parseProgram(nextUrl, schedule, referer, firstUrl);
        }
    }

    private void parseDay(String url, Map<String, String> referer, SubstitutionSchedule schedule, String startUrl)
            throws IOException, JSONException {
        String html = httpGet(url, data.getString("encoding"), referer);
        Document doc = Jsoup.parse(html);
        if (doc.title().toLowerCase().contains("untis")
                || doc.html().toLowerCase().contains("untis") || doc.select(".mon_list").size() > 0) {
            schedule.addDay(parseMonitorVertretungsplanTag(doc, data));
            if (doc.select("meta[http-equiv=refresh]").size() > 0) {
                Element meta = doc.select("meta[http-equiv=refresh]").first();
                String attr = meta.attr("content").toLowerCase();
                String redirectUrl = url.substring(0, url.lastIndexOf("/") + 1) +
                        attr.substring(attr.indexOf("url=") + 4);
                if (!redirectUrl.equals(startUrl))
                    parseDay(redirectUrl, referer, schedule, startUrl);
            }
        }
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException {
        JSONArray classesJson = data.getJSONArray("classes");
        List<String> classes = new ArrayList<String>();
        for (int i = 0; i < classesJson.length(); i++) {
            classes.add(classesJson.getString(i));
        }
        return classes;
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }

}
