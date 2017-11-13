/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.io.TimezoneAssignment;
import biweekly.io.TimezoneInfo;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import me.vertretungsplan.objects.AdditionalInfo;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public abstract class BaseIcalParser extends BaseAdditionalInfoParser {
    private static final int MAX_ITEMS_COUNT = 4;

    protected abstract String getIcalUrl();

    protected String getTitle() {
        return "Termine";
    }

    @Override
    public AdditionalInfo getAdditionalInfo() throws IOException {
        AdditionalInfo info = new AdditionalInfo();
        info.setTitle(getTitle());

        String rawdata = httpGet(getIcalUrl(), "UTF-8");

        if (shouldStripTimezoneInfo()) {
            Pattern pattern = Pattern.compile("BEGIN:VTIMEZONE.*END:VTIMEZONE", Pattern.DOTALL);
            rawdata = pattern.matcher(rawdata).replaceAll("");
        }

        DateTime now = DateTime.now().withTimeAtStartOfDay();
        List<ICalendar> icals = Biweekly.parse(rawdata).all();

        List<Event> events = new ArrayList<>();
        for (ICalendar ical : icals) {
            for (VEvent event : ical.getEvents()) {
                Event item = new Event();

                TimeZone timezoneStart = getTimeZoneStart(ical, event);

                if (event.getDescription() != null) {
                    item.description = event.getDescription().getValue();
                }
                if (event.getSummary() != null) {
                    item.summary = event.getSummary().getValue();
                }
                if (event.getDateStart() != null) {
                    item.startDate = new DateTime(event.getDateStart().getValue());
                    item.startHasTime = event.getDateStart().getValue().hasTime();
                } else {
                    continue;
                }
                if (event.getDateEnd() != null) {
                    item.endDate = new DateTime(event.getDateEnd().getValue());
                    item.endHasTime = event.getDateEnd().getValue().hasTime();
                }
                if (event.getLocation() != null) {
                    item.location = event.getLocation().getValue();
                }
                if (event.getUrl() != null) {
                    item.url = event.getUrl().getValue();
                }

                if (event.getRecurrenceRule() == null
                        && item.endDate != null
                        && (item.endDate.compareTo(now) < 0)) {
                    continue;
                } else if (event.getRecurrenceRule() == null
                        && (item.startDate.compareTo(now) < 0)) {
                    continue;
                }
                if (event.getRecurrenceRule() != null
                        && event.getRecurrenceRule().getValue().getUntil() != null
                        && event.getRecurrenceRule().getValue().getUntil()
                        .compareTo(now.toDate()) < 0) {
                    continue;
                }

                if (event.getRecurrenceRule() != null) {
                    Duration duration = null;
                    if (event.getDateEnd() != null) {
                        duration = new Duration(new DateTime(event.getDateStart().getValue()), new DateTime(event
                                .getDateEnd().getValue()));
                    }

                    DateIterator iterator = event.getDateIterator(timezoneStart);
                    while (iterator.hasNext()) {
                        Date date = iterator.next();
                        Event reccitem = item.clone();
                        reccitem.startDate = new DateTime(date);
                        reccitem.endDate = reccitem.startDate.plus(duration);

                        if (item.startDate.equals(reccitem.startDate)) continue;

                        if (item.endDate != null
                                && (item.endDate.compareTo(now) < 0)) {
                            continue;
                        } else if (item.endDate == null
                                && (item.startDate.compareTo(now) < 0)) {
                            continue;
                        }

                        events.add(reccitem);
                    }
                }

                if (item.endDate != null
                        && (item.endDate.compareTo(now) < 0)) {
                    continue;
                } else if (item.endDate == null
                        && (item.startDate.compareTo(now) < 0)) {
                    continue;
                }

                events.add(item);
            }
        }
        Collections.sort(events, new Comparator<Event>() {
            @Override public int compare(Event o1, Event o2) {
                return o1.startDate.compareTo(o2.startDate);
            }
        });

        StringBuilder content = new StringBuilder();

        int count = 0;

        DateTimeFormatter fmtDt = DateTimeFormat.shortDateTime().withLocale(Locale.GERMANY);
        DateTimeFormatter fmtD = DateTimeFormat.shortDate().withLocale(Locale.GERMANY);

        for (Event item : events) {
            if (count >= MAX_ITEMS_COUNT) {
                break;
            } else if (count != 0) {
                content.append("<br><br>\n\n");
            }

            DateTime start = item.startDate;

            if (item.endDate != null) {
                DateTime end = item.endDate;

                if (!item.endHasTime) {
                    end = end.minusDays(1);
                }

                content.append((item.startHasTime ? fmtDt : fmtD).print(start));
                if (!end.equals(start)) {
                    content.append(" - ");
                    content.append((item.endHasTime ? fmtDt : fmtD).print(end));
                }
            } else {
                content.append(fmtDt.print(start));
            }
            content.append("<br>\n");

            content.append("<b>");
            content.append(item.summary);
            content.append("</b>");

            count++;
        }

        info.setText(content.toString());

        return info;
    }

    protected boolean shouldStripTimezoneInfo() {
        return false;
    }

    private TimeZone getTimeZoneStart(ICalendar ical, VEvent event) {
        if (event.getDateStart() == null) {
            return null;
        }

        TimezoneInfo tzinfo = ical.getTimezoneInfo();
        TimeZone timezone;
        if (tzinfo.isFloating(event.getDateStart())) {
            timezone = TimeZone.getDefault();
        } else {
            TimezoneAssignment dtstartTimezone = tzinfo.getTimezone(event.getDateStart());
            timezone = (dtstartTimezone == null) ? TimeZone.getTimeZone("UTC") : dtstartTimezone.getTimeZone();
        }
        return timezone;
    }

    private class Event implements Cloneable {
        public String summary;
        public String description;
        public String location;
        public DateTime startDate;
        public DateTime endDate;
        public String url;
        public boolean startHasTime;
        public boolean endHasTime;

        @Override
        protected Event clone() {
            Event clone = new Event();
            clone.summary = this.summary;
            clone.description = this.description;
            clone.location = this.location;
            clone.startDate = this.startDate;
            clone.endDate = this.endDate;
            clone.url = this.url;
            return clone;
        }
    }
}
