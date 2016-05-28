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

package me.vertretungsplan.objects;

import com.paour.comparator.NaturalOrderComparator;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.*;

public class SubstitutionSchedule implements Cloneable {
    public static final DateTimeFormatter DAY_DATE_FORMAT = DateTimeFormat.forPattern("EEEE, dd.MM.yyyy").withLocale(
            Locale.GERMAN);
    public static final DateTimeFormatter LAST_CHANGE_DATE_FORMAT = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm").withLocale(
            Locale.GERMAN);

    private Type type;
    private LocalDateTime lastChange;
    private String lastChangeString;
    private String website;
    private List<SubstitutionScheduleDay> days;
    private List<AdditionalInfo> additionalInfos;
    private List<String> classes;
    private List<String> teachers;

    public SubstitutionSchedule() {
        days = new ArrayList<>();
        additionalInfos = new ArrayList<>();
        classes = new ArrayList<>();
        teachers = new ArrayList<>();
    }

    public SubstitutionSchedule(SubstitutionSchedule other) {
        this.type = other.type;
        this.lastChange = other.lastChange;
        this.lastChangeString = other.lastChangeString;
        this.website = other.website;
        this.days = other.days;
        this.additionalInfos = other.additionalInfos;
        this.classes = other.classes;
        this.teachers = other.teachers;
    }

    public static SubstitutionSchedule fromData(SubstitutionScheduleData scheduleData) {
        SubstitutionSchedule schedule = new SubstitutionSchedule();
        schedule.setType(scheduleData.getType());
        return schedule;
    }

    public static Set<Substitution> filterByClass(String theClass, Set<Substitution> substitutions) {
        if (theClass == null) return substitutions;
        Set<Substitution> classSubstitutions = new HashSet<>();
        for (Substitution substitution : substitutions) {
            if (substitution.getClasses().contains(theClass)) classSubstitutions.add(substitution);
        }
        return classSubstitutions;
    }

    public static Set<Substitution> filterBySubject(Set<String> excludedSubjects, Set<Substitution> substitutions) {
        if (excludedSubjects == null || excludedSubjects.isEmpty()) return substitutions;
        Set<Substitution> filteredSubstitutions = new HashSet<>();
        for (Substitution substitution : substitutions) {
            if (!excludedSubjects.contains(substitution.getSubject())) {
                filteredSubstitutions.add(substitution);
            }
        }
        return filteredSubstitutions;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public LocalDateTime getLastChange() {
        return lastChange;
    }

    public void setLastChange(LocalDateTime lastChange) {
        this.lastChange = lastChange;
    }

    public String getLastChangeString() {
        if (lastChangeString != null) {
            return lastChangeString;
        } else if (lastChange != null) {
            return LAST_CHANGE_DATE_FORMAT.print(lastChange);
        } else {
            return null;
        }
    }

    public void setLastChangeString(String lastChangeString) {
        this.lastChangeString = lastChangeString;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public List<SubstitutionScheduleDay> getDays() {
        return days;
    }

    public void addDay(SubstitutionScheduleDay newDay) {
        if (lastChange == null && lastChangeString == null) {
            // Read lastChange or lastChangeString from day
            if (newDay.getLastChange() != null) {
                lastChange = newDay.getLastChange();
            } else if (newDay.getLastChangeString() != null) {
                lastChangeString = newDay.getLastChangeString();
            }
        } else if (lastChange != null && newDay.getLastChange() != null && newDay.getLastChange().isAfter(lastChange)) {
            // Update lastChange from day
            lastChange = newDay.getLastChange();
        }
        addOrMergeDay(newDay);
        Collections.sort(days, new Comparator<SubstitutionScheduleDay>() {
            @Override
            public int compare(SubstitutionScheduleDay a, SubstitutionScheduleDay b) {
                if (a.getDate() != null && b.getDate() != null) {
                    return a.getDate().compareTo(b.getDate());
                } else if (a.getDateString() != null && b.getDateString() != null) {
                    NaturalOrderComparator comp = new NaturalOrderComparator();
                    return comp.compare(a.getDateString(), b.getDateString());
                } else if (a.getDateString() != null) {
                    return 1;
                } else if (b.getDateString() != null) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });
    }

    private void addOrMergeDay(SubstitutionScheduleDay newDay) {
        // Check if day should be merged
        for (SubstitutionScheduleDay day : days) {
            if (day.equalsByDate(newDay)) {
                day.merge(newDay);
                return;
            }
        }
        days.add(newDay);
    }

    public List<AdditionalInfo> getAdditionalInfos() {
        return additionalInfos;
    }

    public void addAdditionalInfo(AdditionalInfo info) {
        additionalInfos.add(info);
    }

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    public List<String> getTeachers() {
        return teachers;
    }

    public void setTeachers(List<String> teachers) {
        this.teachers = teachers;
    }

    public SubstitutionSchedule clone() {
        try {
            return (SubstitutionSchedule) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public SubstitutionSchedule filteredByClassAndExcludedSubject(String theClass, Set<String> excludedSubjects) {
        SubstitutionSchedule filteredSchedule = this.clone();
        filterByClassAndExcludedSubject(filteredSchedule, theClass, excludedSubjects);
        return filteredSchedule;
    }

    protected void filterByClassAndExcludedSubject(SubstitutionSchedule filteredSchedule, String theClass,
                                                   Set<String> excludedSubjects) {
        for (int i = 0; i < filteredSchedule.getDays().size(); i++) {
            SubstitutionScheduleDay day = filteredSchedule.getDays().get(i);
            SubstitutionScheduleDay filteredDay = day.clone();
            filteredDay.setSubstitutions(day.getSubstitutionsByClassAndExcludedSubject(theClass, excludedSubjects));
            filteredSchedule.getDays().set(i, filteredDay);
        }
        if (theClass != null) {
            List<String> classes = new ArrayList<>();
            classes.add(theClass);
            filteredSchedule.setClasses(classes);
        }
    }

    public enum Type {
        STUDENT, TEACHER
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("--------------------\n");
        builder.append("SubstitutionSchedule\n");
        builder.append("--------------------\n\n");
        builder.append("type: ").append(type).append("\n");
        builder.append("last change: ").append(getLastChangeString()).append("\n");
        builder.append("website: ").append(website).append("\n");

        if (classes != null) {
            builder.append("classes: ").append(classes.toString()).append("\n");
        }

        if (teachers != null) {
            builder.append("teachers: ").append(teachers.toString()).append("\n");
        }

        builder.append("\n\n");
        builder.append("Schedule\n");
        builder.append("--------\n\n");

        for (SubstitutionScheduleDay day : days) builder.append(day.toString()).append("\n\n");

        if (additionalInfos.size() > 0) {
            builder.append("Additional Infos\n");
            builder.append("----------------\n\n");

            for (AdditionalInfo info : additionalInfos) builder.append(info.toString()).append("\n\n");
        }

        return builder.toString();
    }
}