/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jetbrains.annotations.Nullable;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import com.paour.comparator.NaturalOrderComparator;

/**
 * Represents one day on the the {@link SubstitutionSchedule} and contains the corresponding substitutions and messages.
 */
public class SubstitutionScheduleDay implements Cloneable {

    private LocalDate date;
    private String dateString;
    private LocalDateTime lastChange;
    private String lastChangeString;
    private Set<Substitution> substitutions;
    private List<String> messages;
    private String comment;

    public SubstitutionScheduleDay() {
        substitutions = new HashSet<>();
        messages = new ArrayList<>();
    }

    /**
     * Get the date of this day. If the date could not be parsed, there is only a
     * string representation available using {@link #getDateString()}.
     *
     * @return the date
     */
    public LocalDate getDate() {
        return date;
    }

    /**
     * Set the date of this day. If the date could not be parsed,
     * use {@link #setDateString(String)} to specify a string representation.
     *
     * @param date the date
     */
    public void setDate(LocalDate date) {
        this.date = date;
    }

    /**
     * Get the date of this day, as a string representation
     *
     * @return the date of this day, as a string representation
     */
    public String getDateString() {
        if (date != null) {
            return SubstitutionSchedule.DAY_DATE_FORMAT.print(date);
        } else if (dateString != null) {
            return dateString;
        } else {
            return null;
        }
    }

    /**
     * Set the date of this day a string representation. If you used {@link #setDate(LocalDate)}, you do
     * not need to add this.
     *
     * @param dateString the date of this day, as a string representation
     */
    public void setDateString(String dateString) {
        this.dateString = dateString;
    }

    /**
     * Get the date and time where this day on the schedule was last updated. If the date could not be parsed, there is
     * only a string representation available using {@link #getLastChangeString()}. May be {@code null} if the last
     * change date is only set for the whole schedule ({@link SubstitutionSchedule#getLastChange()}).
     *
     * @return the date and time where this day was last updated
     */
    @Nullable
    public LocalDateTime getLastChange() {
        return lastChange;
    }

    /**
     * Set the date and time where this day on the schedule was last updated. If the date could not be parsed,
     * use {@link #setLastChangeString(String)} to specify a string representation. If the last change date is only
     * available for the whole schedule, use {@link SubstitutionSchedule#setLastChange(LocalDateTime)}
     *
     * @param lastChange the date and time where this day was last updated.
     */
    public void setLastChange(LocalDateTime lastChange) {
        this.lastChange = lastChange;
    }

    /**
     * Get the date and time where this day on the schedule was last updated as a string representation. May be
     * {@code null} if the last change date is only set for the whole schedule
     * ({@link SubstitutionSchedule#getLastChangeString()}).
     *
     * @return the date and time where this day was last updated, as a string representation
     */
    @Nullable
    public String getLastChangeString() {
        if (lastChange != null) {
            return SubstitutionSchedule.LAST_CHANGE_DATE_FORMAT.print(lastChange);
        } else if (lastChangeString != null) {
            return lastChangeString;
        } else {
            return null;
        }
    }

    /**
     * Set the date and time where this day on the schedule was last updated as a string representation. If you can
     * parse the date, you should use {@link #setLastChange(LocalDateTime)} instead. If the last change date is only
     * available for the whole schedule, use {@link SubstitutionSchedule#setLastChangeString(String)}
     *
     * @param lastChangeString the date and time where this day was last updated, as a string representation
     */
    public void setLastChangeString(String lastChangeString) {
        this.lastChangeString = lastChangeString;
    }

    /**
     * Get all substitutions for this day
     *
     * @return all substitutions for this day
     */
    public Set<Substitution> getSubstitutions() {
        return substitutions;
    }

    /**
     * Set the substitutions for this day
     *
     * @param substitutions the set of substitutions for this day
     */
    public void setSubstitutions(Set<Substitution> substitutions) {
        this.substitutions = substitutions;
    }

    public Set<Substitution> getSubstitutionsByClass(String theClass) {
        return SubstitutionSchedule.filterByClass(theClass, substitutions);
    }

    /**
     * Get all messages for this day. May include simple HTML markup (only a subset of the tags is supported, such as
     * {@code <b>bold</b>} and {@code <i>italic</i>}.
     *
     * @return the list of messages for this day
     */
    public List<String> getMessages() {
        return messages;
    }

    /**
     * Add a message for this day. May include simple HTML markup (only a subset of the tags is supported, such as
     * {@code <b>bold</b>} and {@code <i>italic</i>}.
     *
     * @param message the message to add
     */
    public void addMessage(String message) {
        messages.add(message);
    }

    /**
     * Get the comment for this day (displayed next to the date - e.g. "A-Woche")
     *
     * @return the comment for this day
     */
    public String getComment() {
        return comment;
    }

    /**
     * Set the comment for this day (displayed next to the date - e.g. "A-Woche")
     *
     * @param comment the comment for this day
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Add a substitution for this day
     *
     * @param substitution the substitution to add
     */
    public void addSubstitution(Substitution substitution) {
        // Look for equal substitutions for different classes or teachers and merge them
        for (Substitution s : getSubstitutions()) {
            if (s.equalsExcludingClasses(substitution)) {
                s.getClasses().addAll(substitution.getClasses());
                return;
            } else if (s.equalsExcludingTeachers(substitution)) {
                s.getTeachers().addAll(substitution.getTeachers());
                return;
            } else if (s.equalsExcludingPreviousTeachers(substitution)) {
                s.getPreviousTeachers().addAll(substitution.getPreviousTeachers());
                return;
            }
        }
        getSubstitutions().add(substitution);
    }

    /**
     * Add multiple substitutions for this day
     *
     * @param substitutions the substitutions to add
     */
    public void addAllSubstitutions(Substitution... substitutions) {
        for (Substitution s : substitutions) addSubstitution(s);
    }

    /**
     * Add multiple substitutions for this day
     *
     * @param substitutions the substitutions to add
     */
    public void addAllSubstitutions(Collection<? extends Substitution> substitutions) {
        for (Substitution s : substitutions) addSubstitution(s);
    }

    /**
     * Merge substitutions from this day with those from another {@link SubstitutionScheduleDay}. Both must have the
     * same date.
     *
     * @param day the day to merge with this day
     */
    public void merge(SubstitutionScheduleDay day) {
        if (day.getDate() != null && !day.getDate().equals(getDate())
                || day.getDateString() != null && !day.getDateString().equals(getDateString())) {
            throw new IllegalArgumentException("Cannot merge days with different dates");
        }

        addAllSubstitutions(day.getSubstitutions());
        for (String message : day.getMessages()) {
            if (!messages.contains(message)) messages.add(message);
        }

        if (day.getLastChange() != null && getLastChange() != null && day.getLastChange().isAfter(getLastChange())) {
            setLastChange(day.getLastChange());
        }
    }


    /**
     * Check if this day's date is equal to the one of another {@link SubstitutionScheduleDay}. Also works if only
     * the string representation is specified ({@link #setDateString(String)}).
     *
     * @param other the day to compare with this one
     * @return boolean indicating if the dates are equal
     */
    public boolean equalsByDate(SubstitutionScheduleDay other) {
        if (getDate() != null) {
            return getDate().equals(other.getDate());
        } else if (getDateString() != null) {
            return getDateString().equals(other.getDateString());
        } else {
            return other.getDate() == null && other.getDateString() == null;
        }
    }

    /**
     * Get only the substitutions that apply to the given class and that are not for one of the given subjects.
     *
     * @param theClass         the class to find substitutions for
     * @param excludedSubjects the subjects to exclude
     * @return a set of filtered substitutions
     */
    public Set<Substitution> getSubstitutionsByClassAndExcludedSubject(String theClass,
                                                                       Set<String> excludedSubjects) {
        return SubstitutionSchedule.filterBySubject(excludedSubjects, SubstitutionSchedule
                .filterByClass(theClass, substitutions));
    }

    /**
     * Get only the substitutions that apply to the given teacher and that are not for one of the given subjects.
     *
     * @param teacher          the teacher to find substitutions for
     * @param excludedSubjects the subjects to exclude
     * @return a set of filtered substitutions
     */
    public Set<Substitution> getSubstitutionsByTeacherAndExcludedSubject(String teacher, Set<String>
            excludedSubjects) {
        return SubstitutionSchedule.filterBySubject(excludedSubjects, SubstitutionSchedule
                .filterByTeacher(teacher, substitutions));
    }

    public SubstitutionScheduleDay clone() {
        try {
            return (SubstitutionScheduleDay) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get a string representation of the day, using different wording depending on the type. Useful for debugging
     * (console output).
     *
     * @param type the type of the {@link SubstitutionSchedule}. Affects the format in which the single
     *             {@link Substitution}s are output
     * @return a string representation of this day
     */
    public String toString(SubstitutionSchedule.Type type) {
        StringBuilder builder = new StringBuilder();

        builder.append("DateString: ").append(getDateString()).append("\n");
        builder.append("Date: ").append(getDate()).append("\n");
        if (getComment() != null) builder.append(getComment()).append("\n");
        builder.append("----------------------\n\n");

        if (getLastChangeString() != null) {
            builder.append("last change: ").append(getLastChangeString()).append("\n\n");
        }

        List<Substitution> sortedSubstitutions = new ArrayList<>(substitutions);
        Collections.sort(sortedSubstitutions, new Comparator<Substitution>() {
            @Override public int compare(Substitution o1, Substitution o2) {
                return new NaturalOrderComparator().compare(o1.getLesson(), o2.getLesson());
            }
        });
        for (Substitution subst : sortedSubstitutions) builder.append(subst.toString(type)).append("\n");

        builder.append("\n");
        for (String message : messages) builder.append(message).append("\n");

        return builder.toString();
    }

    @Override
    public String toString() {
        return toString(SubstitutionSchedule.Type.STUDENT);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        SubstitutionScheduleDay that = (SubstitutionScheduleDay) o;

        return new EqualsBuilder()
                .append(date, that.date)
                .append(dateString, that.dateString)
                .append(lastChange, that.lastChange)
                .append(lastChangeString, that.lastChangeString)
                .append(substitutions, that.substitutions)
                .append(messages, that.messages)
                .append(comment, that.comment)
                .isEquals();
    }

    @Override public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(date)
                .append(dateString)
                .append(lastChange)
                .append(lastChangeString)
                .append(substitutions)
                .append(messages)
                .append(comment)
                .toHashCode();
    }
}
