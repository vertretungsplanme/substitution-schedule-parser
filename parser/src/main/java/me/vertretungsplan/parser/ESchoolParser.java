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
import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import me.vertretungsplan.objects.authentication.PasswordAuthenticationData;
import me.vertretungsplan.objects.credential.PasswordCredential;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ESchoolParser extends BaseParser {
    private static final String BASE_URL = "http://eschool.topackt.com/";
    private static final String ENCODING = "ISO-8859-1";

    public ESchoolParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException, CredentialInvalidException {
        if (credential == null || !(credential instanceof PasswordCredential)
                || ((PasswordCredential) credential).getPassword() == null
                || ((PasswordCredential) credential).getPassword().isEmpty()) {
            throw new IOException("no login");
        }

        SubstitutionSchedule schedule = SubstitutionSchedule.fromData(scheduleData);

        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("wp", scheduleData.getData().getString("id")));
        nvps.add(new BasicNameValuePair("go", "vplan"));
        nvps.add(new BasicNameValuePair("content", "x14"));
        nvps.add(new BasicNameValuePair("sortby", "S"));

        String url = BASE_URL + "?" + URLEncodedUtils.format(nvps, "UTF-8");

        Document doc = Jsoup.parse(httpGet(url, ENCODING));
        if (doc.select("form[name=loginform]").size() > 0
                && scheduleData.getAuthenticationData() instanceof PasswordAuthenticationData) {
            // Login required
            List<NameValuePair> formParams = new ArrayList<>();
            formParams.add(new BasicNameValuePair("password", ((PasswordCredential) credential).getPassword()));
            formParams.add(new BasicNameValuePair("login", ""));
            doc = Jsoup.parse(httpPost(url, ENCODING, formParams));

            if (doc.select("font[color=red]").text().contains("fehlgeschlagen")) {
                throw new CredentialInvalidException();
            }
        }

        String infoString = doc.select("#Content table").first().select("td").get(1).ownText();
        Pattern pattern = Pattern.compile("Letzte Aktualisierung:\u00a0(\\d{2}.\\d{2}.\\d{4} - \\d{2}:\\d{2})");
        Matcher matcher = pattern.matcher(infoString);
        if (matcher.find()) {
            LocalDateTime lastChange = DateTimeFormat.forPattern("dd.MM.yyyy - HH:mm")
                    .parseLocalDateTime(matcher.group(1));
            schedule.setLastChange(lastChange);
        }

        Elements titles = doc.select("center b");
        Elements tables = doc.select("table#DATA");
        if (titles.size() != tables.size()) throw new IOException("Anzahl Überschriften != Anzahl Tabellen");

        for (int i = 0; i < titles.size(); i++) {
            SubstitutionScheduleDay day = new SubstitutionScheduleDay();
            System.out.println(titles.get(i).text());
            day.setDate(ParserUtils.parseDate(titles.get(i).text()));
            parseTable(tables.get(i), day);
            schedule.addDay(day);
        }

        schedule.setClasses(getAllClasses());
        schedule.setTeachers(getAllTeachers());

        return schedule;
    }

    private void parseTable(Element table, SubstitutionScheduleDay day) {
        for (Element th:table.select("th[colspan=10]")) {
            String lesson;

            Pattern pattern = Pattern.compile("(\\d+)\\. Stunde");
            Matcher matcher = pattern.matcher(th.text());
            if (matcher.find()) {
                lesson = matcher.group(1);
            } else {
                lesson = th.text();
            }

            // skip over table headers
            Element row = th.parent().nextElementSibling().nextElementSibling();
            while (row != null && row.select("th").size() == 0) {
                Substitution subst = new Substitution();
                subst.setLesson(lesson);

                Elements columns = row.select("td");
                
                String[] classes = columns.get(0).text().split(", ");
                subst.setClasses(new HashSet<>(Arrays.asList(classes)));

                subst.setPreviousTeacher(getPreviousValue(columns.get(1)));
                subst.setTeacher(getNewValue(columns.get(1)));
                subst.setPreviousSubject(getPreviousValue(columns.get(2)));
                subst.setSubject(getNewValue(columns.get(2)));
                subst.setPreviousRoom(getPreviousValue(columns.get(3)));
                subst.setRoom(getNewValue(columns.get(3)));
                if (columns.get(4).text().isEmpty()) {
                    subst.setType("Vertretung");
                    subst.setColor(colorProvider.getColor("Vertretung"));
                } else {
                    String desc = columns.get(4).text();
                    subst.setDesc(desc);
                    String recognizedType = recognizeType(desc);
                    if (recognizedType == null) recognizedType = "Vertretung";
                    subst.setType(recognizedType);
                    subst.setColor(colorProvider.getColor(recognizedType));
                }

                day.addSubstitution(subst);

                row = row.nextElementSibling();
            }
        }
    }

    private String getNewValue(Element cell) {
        List<TextNode> textNodes = cell.textNodes();
        if (textNodes.size() == 1) {
            return textNodes.get(0).text().trim();
        } else if (textNodes.size() == 2) {
            return textNodes.get(1).text().trim();
        } else {
            return null;
        }
    }

    private String getPreviousValue(Element cell) {
        List<TextNode> textNodes = cell.textNodes();
        if (textNodes.size() == 1) {
            return null;
        } else if (textNodes.size() == 2) {
            return textNodes.get(0).text().trim();
        } else {
            return null;
        }
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException {
        return getClassesFromJson();
    }

    @Override
    public List<String> getAllTeachers() {
        return null;
    }
}
