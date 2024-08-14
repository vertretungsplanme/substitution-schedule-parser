/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import com.mifmif.common.regex.Generex;
import com.paour.comparator.NaturalOrderComparator;
import org.apache.http.client.fluent.Request;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ParserUtils {

    private static final List<DateTimeFormatter> dateTimeFormatters = new ArrayList<>();
    private static final List<DateTimeFormatter> dateFormatters = new ArrayList<>();
    private static final String[] dateFormats = new String[]{
            "dd.M.yy EEEE",
            "dd.M.yyyy EEEE",
            "dd.M. EEEE",
            "d.M. EEEE",
            "EEEE, dd.M.yy",
            "EEEE, dd.M.yyyy",
            "EEEE, dd.M",
            "EEEE dd.M.yy",
            "EEEE dd.M.yyyy",
            "EEEE dd.M",
            "EEEE', den 'dd.M.yy",
            "EEEE', den 'dd.M.yyyy",
            "EEEE', den 'dd.M",
            "dd.M.yy",
            "dd.M.yyyy",
            "dd.M.",
            "dd.MM.yy EEEE",
            "dd.MM.yyyy EEEE",
            "dd/MM/yyyy EEEE",
            "dd.MM EEEE",
            "EEEE, dd.MM.yy",
            "EEEE, dd.MM.yyyy",
            "EEEE, dd.MM",
            "EEEE dd.MM.yy",
            "EEEE dd.MM.yyyy",
            "EEEE dd.MM",
            "EEEE', den 'dd.MM.yy",
            "EEEE', den 'dd.MM.yyyy",
            "EEEE', den 'dd.MM",
            "dd.MM.yy",
            "dd.MM.yyyy",
            "dd.MM.",
            "d.M.yy EEEE",
            "d.M.yyyy EEEE",
            "d.M. EEEE",
            "dd.MM. EEEE",
            "d.M. / EEEE",
            "dd.MM. / EEEE",
            "EEEE, d.M.yy",
            "EEEE, d.M.yyyy",
            "EEEE, d.M",
            "EEEE d.M.yy",
            "EEEE d.M.yyyy",
            "EEEE d.M",
            "EEEE', den 'd.M.yy",
            "EEEE', den 'd.M.yyyy",
            "EEEE', den 'd.M",
            "d.M.yy",
            "d.M.yyyy",
            "d.M.",
            "EEEE, d. MMMM yy",
            "EEEE, d. MMMM yyyy"
    };
    private static final String[] separators = new String[]{
            " ",
            ", ",
            " 'um' "
    };
    private static final String[] timeFormats = new String[]{
            "HH:mm",
            "HH:mm 'Uhr'",
            "(HH:mm 'Uhr')",
            "HH:mm:ss"
    };
    private static final String[] dateTimeFormats = new String[dateFormats.length * timeFormats.length * separators.length];

    @TestOnly
    static synchronized void init() {
        int i = 0;
        dateFormatters.clear();
        dateTimeFormatters.clear();

        for (String date : dateFormats) {
            dateFormatters.add(DateTimeFormat.forPattern(date)
                    .withLocale(Locale.GERMAN).withDefaultYear(DateTime.now().getYear()));
            for (String time : timeFormats) {
                for (String separator : separators) {
                    dateTimeFormats[i] = date + separator + time;
                    dateTimeFormatters.add(DateTimeFormat.forPattern(dateTimeFormats[i])
                            .withLocale(Locale.GERMAN).withDefaultYear(DateTime.now().getYear()));
                    i++;
                }
            }
        }
    }

    private static synchronized void reinitIfNeeded() {
        if (dateFormatters.isEmpty() || dateFormatters.get(0).getDefaultYear() != DateTime.now().getYear()) {
            init();
        }
    }

    static LocalDateTime parseDateTime(String string) {
        if (string == null) return null;
        reinitIfNeeded();

        string = string.replace("Stand:", "").replace("Import:", "").trim();
        int i = 0;
        for (DateTimeFormatter f : dateTimeFormatters) {
            try {
                LocalDateTime dt = f.parseLocalDateTime(string);
                if (dateTimeFormats[i].contains("yyyy")) {
                    return dt;
                } else {
                    Duration currentYearDifference = abs(new Duration(DateTime.now(), dt.toDateTime()));
                    Duration lastYearDifference = abs(new Duration(DateTime.now(), dt.minusYears(1).toDateTime()));
                    Duration nextYearDifference = abs(new Duration(DateTime.now(), dt.plusYears(1).toDateTime()));
                    if (lastYearDifference.isShorterThan(currentYearDifference)) {
                        return DateTimeFormat.forPattern(dateTimeFormats[i])
                                .withLocale(Locale.GERMAN).withDefaultYear(f.getDefaultYear() - 1)
                                .parseLocalDateTime(string);
                    } else if (nextYearDifference.isShorterThan(currentYearDifference)) {
                        return DateTimeFormat.forPattern(dateTimeFormats[i])
                                .withLocale(Locale.GERMAN).withDefaultYear(f.getDefaultYear() + 1)
                                .parseLocalDateTime(string);
                    } else {
                        return dt;
                    }
                }
            } catch (IllegalArgumentException e) {
                // Does not match this format, try the next one
            }
            i++;
        }
        // Does not match any known format :(
        return null;
    }

    private static Duration abs(Duration duration) {
        Duration nothing = new Duration(0);
        if (duration.isShorterThan(nothing)) {
            return duration.negated();
        } else {
            return duration;
        }
    }

    static LocalDate parseDate(String string) {
        if (string == null) return null;
        reinitIfNeeded();

        string = string
                .replace("Stand:", "")
                .replace("Import:", "")
                .replaceAll(", Woche [A-Z]", "")
                .replaceAll(", .*unterricht Gruppe .*", "")
                .replaceAll(", Unterrichts.* Gruppe .*", "")
                .trim();
        int i = 0;
        for (DateTimeFormatter f : dateFormatters) {
            try {
                LocalDate d = f.parseLocalDate(string);
                if (dateFormats[i].contains("yyyy")) {
                    return d;
                } else {
                    Duration currentYearDifference = abs(new Duration(DateTime.now(), d.toDateTimeAtCurrentTime()));
                    Duration lastYearDifference =
                            abs(new Duration(DateTime.now(), d.minusYears(1).toDateTimeAtCurrentTime()));
                    Duration nextYearDifference =
                            abs(new Duration(DateTime.now(), d.plusYears(1).toDateTimeAtCurrentTime()));
                    if (lastYearDifference.isShorterThan(currentYearDifference)) {
                        return DateTimeFormat.forPattern(dateFormats[i])
                                .withLocale(Locale.GERMAN).withDefaultYear(f.getDefaultYear() - 1)
                                .parseLocalDate(string);
                    } else if (nextYearDifference.isShorterThan(currentYearDifference)) {
                        return DateTimeFormat.forPattern(dateFormats[i])
                                .withLocale(Locale.GERMAN).withDefaultYear(f.getDefaultYear() + 1)
                                .parseLocalDate(string);
                    } else {
                        return d;
                    }
                }
            } catch (IllegalArgumentException e) {
                // Does not match this format, try the next one
            }
            i++;
        }
        // Does not match any known format :(
        return null;
    }

    static List<String> handleUrl(String inputUrl) {
        return handleUrl(inputUrl, null);
    }

    static List<String> handleUrl(String inputUrl, String loginResponse) {
        List<String> urls = handleUrlWithDateFormat(inputUrl);
        List<String> urlsWithHidrive = new ArrayList<>();
        for (String url : urls) {
            urlsWithHidrive.add(handleUrlWithHidriveToken(url, loginResponse));
        }
        return urlsWithHidrive;
    }

    private static String handleUrlWithHidriveToken(String url, String loginResponse) {
        if (loginResponse == null) return url;
        Pattern hidriveTokenPattern = Pattern.compile("\\{hidrive-token\\}");
        Matcher matcher = hidriveTokenPattern.matcher(url);
        if (matcher.find()) {
            url = matcher.replaceFirst(loginResponse);
        }

        Pattern hidrivePattern = Pattern.compile("\\{hidrive-pid\\(([^)]+)\\)\\}");
        matcher = hidrivePattern.matcher(url);
        if (matcher.find()) {
            String[] path = matcher.group(1).split("/");
            String filename = path[path.length - 1];
            String filepath = String.join("/", Arrays.copyOfRange(path, 0, path.length - 1));

            String apiUrl = "https://my.hidrive.com/api/dir?path=" + filepath + "&fields=members.name%2Cmembers" +
                    ".id&members=all&limit=0%2C5000";
            Request request = Request.Get(apiUrl).connectTimeout(1000).socketTimeout(1000)
                    .addHeader("Authorization", "Bearer " + loginResponse);
            try {
                JSONObject result = new JSONObject(request.execute().returnContent().asString());
                JSONArray members = result.getJSONArray("members");
                for (int i = 0; i < members.length(); i++) {
                    JSONObject file = members.getJSONObject(i);
                    if (file.getString("name").equals(filename)) {
                        url = matcher.replaceFirst(file.getString("id"));
                        break;
                    }
                }
            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }
        }


        return url;
    }

    @NotNull private static List<String> handleUrlWithDateFormat(String url) {
        List<String> urls = new ArrayList<>();
        Pattern dateFormatPattern = Pattern.compile("\\{date\\(([^)]+)\\)\\}");
        Matcher matcher = dateFormatPattern.matcher(url);
        if (matcher.find()) {
            String pattern = matcher.group(1);
            for (int j = 0; j < 7; j++) {
                LocalDate date = LocalDate.now().plusDays(j);
                String dateStr = DateTimeFormat.forPattern(pattern).print(date);
                String urlWithDate = matcher.replaceFirst(dateStr);
                urls.add(urlWithDate);
            }
        } else {
            urls.add(url);
        }
        return urls;
    }

    static List<String> handleUrls(List<String> urls, String loginResponse) {
        List<String> urlsWithDate = new ArrayList<>();
        for (String url:urls) {
            urlsWithDate.addAll(handleUrl(url, loginResponse));
        }
        return urlsWithDate;
    }

    static List<String> handleUrls(List<String> urls) {
        return handleUrls(urls, null);
    }


    @Nullable
    static List<String> getClassesFromJson(JSONObject data) throws JSONException {
        if (data.has("classes")) {
            if (data.get("classes") instanceof JSONArray) {
                JSONArray classesJson = data.getJSONArray("classes");
                List<String> classes = new ArrayList<>();
                for (int i = 0; i < classesJson.length(); i++) {
                    classes.add(classesJson.getString(i));
                }
                return classes;
            } else if (data.get("classes") instanceof String) {
                String regex = data.getString("classes");
                Generex generex = new Generex(regex);
                final List<String> classes = generex.getAllMatchedStrings();
                //noinspection unchecked
                classes.sort(new NaturalOrderComparator());
                return classes;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }


}
