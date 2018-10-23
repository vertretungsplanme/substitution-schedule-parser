/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for substitution schedules in HTML format created by the <a href="http://untis.de/">Untis</a> software
 * using the "Monitor-Vertretungsplan" layout.
 * <p>
 * Example: <a href="http://vertretung.lornsenschule.de/schueler/subst_001.htm">Lornsenschule Schleswig</a>
 * <p>
 * This parser can be accessed using <code>"untis-monitor"</code> for {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>urls</code> (Array of JSONObjects, required)</dt>
 * <dd>The URLs of the HTML files of the schedule. There is one file for each day. Each JSONObject has a
 * <code>url</code> parameter specifying the URL and a <code>following</code> parameter to set if the parser
 * should follow HTML <code>meta</code> tag redirects to load multiple pages. If you are using
 * {@link LoginHandler} for a HTTP POST login, the <code>url</code> parameter can also be set to
 * <code>"loginResponse"</code>
 * </dd>
 *
 * <dt><code>encoding</code> (String, required)</dt>
 * <dd>The charset of the XML files. It's probably either UTF-8 or ISO-8859-1.</dd>
 *
 * <dt><code>classes</code> (Array of Strings, required)</dt>
 * <dd>The list of all classes, as they can appear in the schedule</dd>
 *
 * <dt><code>website</code> (String, recommended)</dt>
 * <dd>The URL of a website where the substitution schedule can be seen online</dd>
 *
 * <dt><code>lastChangeSelector</code> (String, optional)</dt>
 * <dd>When this is specified, the date of last change is read from the first HTML element that matches this CSS
 * selector. The CSS selector syntax is supported as specified by
 * <a href="https://jsoup.org/cookbook/extracting-data/selector-syntax">JSoup</a>.</dd>
 *
 * <dt><code>embeddedContentSelector</code> (String, optional)</dt>
 * <dd>When the Untis schedule is embedded in another HTML file using server-side code, you can use this to
 * specify which HTML elements should be considered as the containers for the Untis schedule. The CSS selector
 * syntax is supported as specified by
 * <a href="https://jsoup.org/cookbook/extracting-data/selector-syntax">JSoup</a>.</dd>
 *
 * <dt><code>forceAllPages</code> (Boolean, optional, default: false)</dt>
 * <dd>If the first page was loaded successfully, but additional pages failed due to HTTP error codes, don't ignore
 * these errors
 * </dd>
 * </dl>
 *
 * Additionally, this parser supports the parameters specified in {@link LoginHandler} for login-protected schedules
 * and those specified in {@link UntisCommonParser}.
 */
public class UntisMonitorParser extends UntisCommonParser {

    private static final int MAX_RECURSION_DEPTH = 30;
    private static final String PARAM_URLS = "urls";
    private static final String PARAM_ENCODING = "encoding";
    private static final String PARAM_EMBEDDED_CONTENT_SELECTOR = "embeddedContentSelector";
    private static final String PARAM_LAST_CHANGE_SELECTOR = "lastChangeSelector";
    private static final String PARAM_WEBSITE = "website";
    private static final String PARAM_FORCE_ALL_PAGES = "forceAllPages";
    private static final String SUBPARAM_FOLLOWING = "following";
    private static final String SUBPARAM_URL = "url";
    private static final String VALUE_URL_LOGIN_RESPONSE = "loginResponse";
    private static final Pattern VALUE_URL_LOGIN_RESPONSE_LINK_SELECTOR = Pattern.compile("loginResponse\\((.*)\\)");
    private String loginResponse;

    public UntisMonitorParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
    }

    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException, CredentialInvalidException {
        loginResponse = new LoginHandler(scheduleData, credential, cookieProvider)
                .handleLoginWithResponse(executor, cookieStore);

        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);

        JSONArray urls = scheduleData.getData().getJSONArray(PARAM_URLS);
        String encoding = scheduleData.getData().optString(PARAM_ENCODING, null);
        List<Document> docs = new ArrayList<>();

        for (int i = 0; i < urls.length(); i++) {
            JSONObject url = urls.getJSONObject(i);
            final String urlStr = url.getString(SUBPARAM_URL);
            for (String dateUrl : ParserUtils.handleUrlWithDateFormat(urlStr)) {
                loadUrl(dateUrl, encoding, url.getBoolean(SUBPARAM_FOLLOWING), docs);
            }
        }

        for (Document doc : docs) {
            if (scheduleData.getData().has(PARAM_EMBEDDED_CONTENT_SELECTOR)) {
                for (Element part : doc.select(scheduleData.getData().getString(PARAM_EMBEDDED_CONTENT_SELECTOR))) {
                    SubstitutionScheduleDay day = parseMonitorDay(part, scheduleData.getData());
                    v.addDay(day);
                }
            } else if (doc.title().contains("Untis") || doc.html().contains("<!--<title>Untis")) {
                SubstitutionScheduleDay day = parseMonitorDay(doc, scheduleData.getData());
                v.addDay(day);
            } else if (docs.size() == 0 || scheduleData.getData().optBoolean(PARAM_FORCE_ALL_PAGES)) {
                // error
                throw new IOException("Seems like there is no Untis schedule here");
            }

            if (scheduleData.getData().has(PARAM_LAST_CHANGE_SELECTOR)
                    && doc.select(scheduleData.getData().getString(PARAM_LAST_CHANGE_SELECTOR)).size() > 0) {
                String text = doc.select(scheduleData.getData().getString(PARAM_LAST_CHANGE_SELECTOR)).first().text();
                String lastChange;
                Pattern pattern = Pattern.compile("\\d\\d\\.\\d\\d\\.\\d\\d\\d\\d,? \\d\\d:\\d\\d");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    lastChange = matcher.group();
                } else {
                    lastChange = text;
                }
                v.setLastChangeString(lastChange);
                v.setLastChange(ParserUtils.parseDateTime(lastChange));
            }
        }

        if (scheduleData.getData().has(PARAM_WEBSITE)) {
            v.setWebsite(scheduleData.getData().getString(PARAM_WEBSITE));
        } else if (urls.length() == 1) {
            v.setWebsite(urls.getJSONObject(0).getString("url"));
        }

        v.setClasses(getAllClasses());
        v.setTeachers(getAllTeachers());

        return v;
    }

    private void loadUrl(String url, String encoding, boolean following, List<Document> docs, String startUrl,
                         int recursionDepth) throws IOException, CredentialInvalidException {
        String html;
        final Matcher matcher = VALUE_URL_LOGIN_RESPONSE_LINK_SELECTOR.matcher(url);
        if (url.equals(VALUE_URL_LOGIN_RESPONSE)) {
            html = loginResponse;
        } else if (matcher.matches()) {
            Document doc = Jsoup.parse(loginResponse);
            try {
                doc.setBaseUri(scheduleData.getData().getJSONObject("login").getString("url"));
                html = httpGet(doc.select(matcher.group(1)).first().absUrl("href"));
            } catch (JSONException e) {
                throw new IOException(e);
            }
        } else {
            try {
                html = httpGet(url, encoding).replace("&nbsp;", "");
            } catch (HttpResponseException e) {
                if (docs.size() == 0 || scheduleData.getData().optBoolean(PARAM_FORCE_ALL_PAGES)) {
                    throw e;
                } else {
                    return; // ignore if first page was loaded and redirect didn't work
                }
            }
        }
        Document doc = Jsoup.parse(html);
        doc.setBaseUri(url);

        if (doc.select(".mon_title").size() == 0) {
            // We have a problem - there seems to be no substitution schedule. Maybe it is hiding
            // inside a frame?
            if (doc.select("frameset frame[name").size() > 0) {
                for (Element frame : doc.select("frameset frame")) {
                    if (frame.attr("src").matches(".*subst_\\d\\d\\d.html?") && recursionDepth < MAX_RECURSION_DEPTH) {
                        String frameUrl = frame.absUrl("src");
                        loadUrl(frame.absUrl("src"), encoding, following, docs, frameUrl, recursionDepth + 1);
                    }
                }
            } else if (doc.text().contains("registriert")) {
                throw new CredentialInvalidException();
            } else {
                if (docs.size() == 0 || scheduleData.getData().optBoolean(PARAM_FORCE_ALL_PAGES)) {
                    // ignore if first page was loaded and redirect didn't work
                    throw new IOException("Could not find .mon-title, seems like there is no Untis " +
                            "schedule here");
                }
            }
        } else {
            findSubDocs(docs, html, doc);

            if (following && doc.select("meta[http-equiv=refresh]").size() > 0) {
                Element meta = doc.select("meta[http-equiv=refresh]").first();
                String attr = meta.attr("content").toLowerCase();
                String redirectUrl = url.substring(0, url.lastIndexOf("/") + 1) + attr.substring(attr.indexOf("url=") + 4);
                if (!redirectUrl.equals(startUrl) && recursionDepth < MAX_RECURSION_DEPTH) {
                    loadUrl(redirectUrl, encoding, true, docs, startUrl, recursionDepth + 1);
                }
            }
        }
    }

    static void findSubDocs(List<Document> docs, String html, Document doc) {
        // Some schools concatenate multiple HTML files for multiple days
        Pattern pattern = Pattern.compile("(<html>.*?</html>)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        List<String> subHtmls = new ArrayList<>();
        while (matcher.find()) {
            subHtmls.add(matcher.group());
        }

        if (subHtmls.size() > 1) {
            for (String subHtml : subHtmls) {
                docs.add(Jsoup.parse(subHtml));
            }
        } else {
            docs.add(doc);
        }
    }

    private void loadUrl(String url, String encoding, boolean following, List<Document> docs) throws IOException, CredentialInvalidException {
        loadUrl(url, encoding, following, docs, url, 0);
    }

    public List<String> getAllTeachers() {
        return null;
    }
}
