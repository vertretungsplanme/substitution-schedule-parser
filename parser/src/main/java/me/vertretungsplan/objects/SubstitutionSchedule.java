/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import com.paour.comparator.NaturalOrderComparator;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.*;

/**
 * Represents a school's substitution schedule
 */
public class SubstitutionSchedule implements Cloneable {
    static final DateTimeFormatter DAY_DATE_FORMAT = DateTimeFormat.forPattern("EEEE, dd.MM.yyyy").withLocale(
            Locale.GERMAN);
    static final DateTimeFormatter LAST_CHANGE_DATE_FORMAT = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm").withLocale(
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

    /**
     * Creates a new SubstitutionSchedule containing the same data as the given one
     *
     * @param other the SubstitutionSchedule whose data to use
     */
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


    /**
     * Initialize a SubstitutionSchedule with the correct type taken from a {@link SubstitutionScheduleData}
     *
     * @param scheduleData a SubstitutionScheduleData to create a schedule for
     * @return a schedule initialized with the correct type
     */
    public static SubstitutionSchedule fromData(SubstitutionScheduleData scheduleData) {
        SubstitutionSchedule schedule = new SubstitutionSchedule();
        schedule.setType(scheduleData.getType());
        return schedule;
    }

    /**
     * Filter a set of substitutions by class
     *
     * @param theClass      the name of a class
     * @param substitutions a set of {@link Substitution}s
     * @return the substitutions from the set that apply to the specified class
     */
    public static Set<Substitution> filterByClass(String theClass, Set<Substitution> substitutions) {
        if (theClass == null) return substitutions;
        Set<Substitution> classSubstitutions = new HashSet<>();
        for (Substitution substitution : substitutions) {
            if (substitution.getClasses().contains(theClass)) classSubstitutions.add(substitution);
        }
        return classSubstitutions;
    }

    /**
     * Filter a set of substitutions by excluding a set of subjects
     *
     * @param excludedSubjects a set of subjects to exclude
     * @param substitutions    a set of {@link Substitution}s
     * @return the substitutions from the set that are not for one of the specified subjects
     */
    public static Set<Substitution> filterBySubject(Set<String> excludedSubjects, Set<Substitution> substitutions) {
        if (excludedSubjects == null || excludedSubjects.isEmpty()) return substitutions;
        Set<Substitution> filteredSubstitutions = new HashSet<>();
        for (Substitution substitution : substitutions) {
            if (substitution.getPreviousSubject() != null) {
                if (!excludedSubjects.contains(substitution.getPreviousSubject())) {
                    filteredSubstitutions.add(substitution);
                }
            } else if (substitution.getSubject() != null) {
                if (!excludedSubjects.contains(substitution.getSubject())) {
                    filteredSubstitutions.add(substitution);
                }
            } else {
                filteredSubstitutions.add(substitution);
            }
        }
        return filteredSubstitutions;
    }

    /**
     * Filter a set of substitutions by teacher
     *
     * @param teacher       a teacher's name/abbreviation
     * @param substitutions a set of {@link Substitution}s
     * @return the substitutions from the set that apply to the specified teacher
     */
    public static Set<Substitution> filterByTeacher(String teacher, Set<Substitution> substitutions) {
        if (teacher == null) return substitutions;
        Set<Substitution> teacherSubstitutions = new HashSet<>();
        for (Substitution substitution : substitutions) {
            if (substitution.getTeachers().contains(teacher)
                    || substitution.getPreviousTeachers().contains(teacher)) {
                teacherSubstitutions.add(substitution);
            }
        }
        return teacherSubstitutions;
    }

    /**
     * Get the type of this schedule
     *
     * @return the type of this schedule
     */
    public Type getType() {
        return type;
    }

    /**
     * Set the type of this schedule
     *
     * @param type the type of this schedule
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Get the date and time where this schedule was last updated. If the date could not be parsed, there is only a
     * string representation available using {@link #getLastChangeString()}.
     *
     * @return the date and time where this schedule was last updated
     */
    public LocalDateTime getLastChange() {
        return lastChange;
    }

    /**
     * Set the date and time where this schedule was last updated. If the date could not be parsed,
     * use {@link #setLastChangeString(String)} to specify a string representation. If you used
     * {@link SubstitutionScheduleDay#setLastChange(LocalDateTime)}, this will automatically be set
     * to the newest date of all the days.
     *
     * @param lastChange the date and time where this schedule was last updated.
     */
    public void setLastChange(LocalDateTime lastChange) {
        this.lastChange = lastChange;
    }

    /**
     * Get the date and time where this schedule was last updated as a string representation
     *
     * @return the date and time where this schedule was last updated, as a string representation
     */
    public String getLastChangeString() {
        if (lastChangeString != null) {
            return lastChangeString;
        } else if (lastChange != null) {
            return LAST_CHANGE_DATE_FORMAT.print(lastChange);
        } else {
            return null;
        }
    }

    /**
     * Set the date and time where this schedule was last updated as a string representation. If you can parse the
     * date, you should use {@link #setLastChange(LocalDateTime)} instead.
     *
     * @param lastChangeString the date and time where this schedule was last updated, as a string representation
     */
    public void setLastChangeString(String lastChangeString) {
        this.lastChangeString = lastChangeString;
    }

    /**
     * Get the website where this schedule can be found online
     *
     * @return the website URL
     */
    public String getWebsite() {
        return website;
    }

    /**
     * Set the website where this schedule can be found online
     *
     * @param website the website URl
     */
    public void setWebsite(String website) {
        this.website = website;
    }

    /**
     * Get the list of days included in this schedule
     *
     * @return the list of days
     */
    public List<SubstitutionScheduleDay> getDays() {
        return days;
    }

    /**
     * Add a day to this substitution schedule
     *
     * @param newDay the day to add
     */
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

    /**
     * Get the list of additional infos on this schedule
     *
     * @return the list of additional infos on this schedule
     */
    public List<AdditionalInfo> getAdditionalInfos() {
        return additionalInfos;
    }

    /**
     * Add an additional info to this schedule
     *
     * @param info the additional info to add
     */
    public void addAdditionalInfo(AdditionalInfo info) {
        additionalInfos.add(info);
    }

    /**
     * Get the list of classes that can appear on this schedule. May only be empty if this is a {@link Type#TEACHER}
     * schedule
     *
     * @return the list of classes
     */
    public List<String> getClasses() {
        return classes;
    }

    /**
     * Set the list of classes. Not required if this is a {@link Type#TEACHER} schedule.
     *
     * @param classes the list of classes to set.
     */
    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    /**
     * Get the list of teachers that can appear on this schedule. May be empty even if this is a {@link Type#TEACHER}
     * schedule.
     *
     * @return the list of teachers
     */
    public List<String> getTeachers() {
        return teachers;
    }

    /**
     * Set the list of teachers. Not required even if this is a {@link Type#TEACHER} schedule.
     *
     * @param teachers the list of teachers to set.
     */
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

    /**
     * Get a new schedule that only contains the data that applies to the specified class, excluding substitutions
     * for the specified subjects.
     *
     * @param theClass         the class whose substitutions should be included
     * @param excludedSubjects the subjects that should be excluded
     * @return a new SubstitutionSchedule containing the filtered data
     */
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

    /**
     * Get a new schedule that only contains the data that applies to the specified teacher, excluding substitutions
     * for the specified subjects.
     *
     * @param teacher          the teacher whose substitutions should be included
     * @param excludedSubjects the subjects that should be excluded
     * @return a new SubstitutionSchedule containing the filtered data
     */
    public SubstitutionSchedule filteredByTeacherAndExcludedSubject(String teacher, Set<String> excludedSubjects) {
        SubstitutionSchedule filteredSchedule = this.clone();
        filterByTeacherAndExcludedSubject(filteredSchedule, teacher, excludedSubjects);
        return filteredSchedule;
    }

    protected void filterByTeacherAndExcludedSubject(SubstitutionSchedule filteredSchedule, String teacher,
                                                     Set<String> excludedSubjects) {
        for (int i = 0; i < filteredSchedule.getDays().size(); i++) {
            SubstitutionScheduleDay day = filteredSchedule.getDays().get(i);
            SubstitutionScheduleDay filteredDay = day.clone();
            filteredDay.setSubstitutions(day.getSubstitutionsByTeacherAndExcludedSubject(teacher, excludedSubjects));
            filteredSchedule.getDays().set(i, filteredDay);
        }
        if (teacher != null) {
            List<String> teachers = new ArrayList<>();
            teachers.add(teacher);
            filteredSchedule.setTeachers(teachers);
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        SubstitutionSchedule that = (SubstitutionSchedule) o;

        return new EqualsBuilder()
                .append(type, that.type)
                .append(lastChange, that.lastChange)
                .append(lastChangeString, that.lastChangeString)
                .append(website, that.website)
                .append(days, that.days)
                .append(additionalInfos, that.additionalInfos)
                .append(classes, that.classes)
                .append(teachers, that.teachers)
                .isEquals();
    }

    @Override public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(type)
                .append(lastChange)
                .append(lastChangeString)
                .append(website)
                .append(days)
                .append(additionalInfos)
                .append(classes)
                .append(teachers)
                .toHashCode();
    }


    /**
     * Represents the type of a substitution schedule
     */
    public enum Type {
        /**
         * Schedules with this type are primarily intended for students. They should contain a list of classes and
         * the substitutions may not contain information about the teachers.
         */
        STUDENT,
        /**
         * Schedules with this type are primarily intended for teachers. The substitutions should contain information
         * about the teachers.
         */
        TEACHER
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

        for (SubstitutionScheduleDay day : days) builder.append(day.toString(type)).append("\n\n");

        if (additionalInfos.size() > 0) {
            builder.append("Additional Infos\n");
            builder.append("----------------\n\n");

            for (AdditionalInfo info : additionalInfos) {
                builder.append(info.getTitle()).append("\n").append(info.getText()).append("\n\n");
            }
        }

        return builder.toString();
    }
}