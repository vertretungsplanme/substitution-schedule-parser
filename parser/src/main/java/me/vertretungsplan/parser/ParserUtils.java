/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ParserUtils {

    private static List<DateTimeFormatter> dateTimeFormatters = new ArrayList<>();
    private static List<DateTimeFormatter> dateFormatters = new ArrayList<>();
    private static String[] dateFormats = new String[]{
            "dd.M.yyyy EEEE",
            "dd.M. EEEE",
            "d.M. EEEE",
            "EEEE, dd.M.yyyy",
            "EEEE, dd.M",
            "EEEE dd.M.yyyy",
            "EEEE dd.M",
            "EEEE', den 'dd.M.yyyy",
            "EEEE', den 'dd.M",
            "dd.M.yyyy",
            "dd.M",
            "dd.MM.yyyy EEEE",
            "dd.MM EEEE",
            "EEEE, dd.MM.yyyy",
            "EEEE, dd.MM",
            "EEEE dd.MM.yyyy",
            "EEEE dd.MM",
            "EEEE', den 'dd.MM.yyyy",
            "EEEE', den 'dd.MM",
            "dd.MM.yyyy",
            "dd.MM",
            "d.M.yyyy EEEE",
            "d.M. EEEE",
            "d.M. EEEE",
            "EEEE, d.M.yyyy",
            "EEEE, d.M",
            "EEEE d.M.yyyy",
            "EEEE d.M",
            "EEEE', den 'd.M.yyyy",
            "EEEE', den 'd.M",
            "d.M.yyyy",
            "d.M"
    };
    private static String[] separators = new String[]{
            " ",
            ", ",
            " 'um' "
    };
    private static String[] timeFormats = new String[]{
            "HH:mm",
            "HH:mm 'Uhr'",
            "(HH:mm 'Uhr')"
    };
    private static String[] dateTimeFormats = new String[dateFormats.length * timeFormats.length * separators.length];

    static {
        int i = 0;
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

    static LocalDateTime parseDateTime(String string) {
        if (string == null) return null;

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
                                .withLocale(Locale.GERMAN).withDefaultYear(DateTime.now().getYear() - 1)
                                .parseLocalDateTime(string);
                    } else if (nextYearDifference.isShorterThan(currentYearDifference)) {
                        return DateTimeFormat.forPattern(dateTimeFormats[i])
                                .withLocale(Locale.GERMAN).withDefaultYear(DateTime.now().getYear() + 1)
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
        string = string.replace("Stand:", "").replace("Import:", "").replaceAll(", Woche [A-Z]", "").trim();
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
                                .withLocale(Locale.GERMAN).withDefaultYear(DateTime.now().getYear() - 1)
                                .parseLocalDate(string);
                    } else if (nextYearDifference.isShorterThan(currentYearDifference)) {
                        return DateTimeFormat.forPattern(dateFormats[i])
                                .withLocale(Locale.GERMAN).withDefaultYear(DateTime.now().getYear() + 1)
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

}
