/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import me.vertretungsplan.utils.SubstitutionTextUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents one substitution on a {@link SubstitutionSchedule}
 */
public class Substitution implements Cloneable {

    private Set<String> classes;
    private String lesson;
    private String type;
    private String subject;
    private String previousSubject;
    private Set<String> teachers;
    private Set<String> previousTeachers;
    private String room;
    private String previousRoom;
    private String desc;
    private String color;
    private String substitutionFrom;
    private String teacherTo;

    public Substitution() {
        classes = new HashSet<>();
        teachers = new HashSet<>();
        previousTeachers = new HashSet<>();
    }

    /**
     * Creates a copy of a substitution with a different set of classes
     *
     * @param substitution the substitution to copy
     * @param classes      the set of classes to use
     */
    public Substitution(Substitution substitution, Set<String> classes) {
        this.classes = classes;
        this.lesson = substitution.lesson;
        this.type = substitution.type;
        this.subject = substitution.type;
        this.previousSubject = substitution.previousSubject;
        this.teachers = substitution.teachers;
        this.previousTeachers = substitution.previousTeachers;
        this.room = substitution.room;
        this.previousRoom = substitution.previousRoom;
        this.desc = substitution.desc;
        this.color = substitution.color;
        this.substitutionFrom = substitution.substitutionFrom;
        this.teacherTo = substitution.teacherTo;
    }

    /**
     * @return A text describing the substitution (designed to be shown together with class, type and lesson)
     */
    public String getText() {
        return SubstitutionTextUtils.getText(this);
    }

    /**
     * @return A text describing the substitution (designed to be shown together with
     * {@link #getPreviousAndCurrentTeacherText()}, type and
     * lesson)
     */
    public String getTeacherText() {
        return SubstitutionTextUtils.getTeacherText(this);
    }

    /**
     * @return A text describing the current and previous teacher
     */
    public String getPreviousAndCurrentTeacherText() {
        return SubstitutionTextUtils.getTeachers(this);
    }

    /**
     * Get the classes this substitution applies for
     *
     * @return the classes
     */
    @NotNull
    public Set<String> getClasses() {
        return classes;
    }

    /**
     * Set the classes this substitution applies for. Required.
     *
     * @param classes the classes to set
     */
    public void setClasses(@NotNull Set<String> classes) {
        this.classes = classes;
    }

    /**
     * Get the lesson which this substitution is for.
     *
     * Keep it short, the recommended format is e.g. "1" for single lessons and "5-6" for multiple lessons. But some
     * schools use different ways to name their lessons.
     *
     * @return the lesson
     */
    @NotNull
    public String getLesson() {
        return lesson;
    }

    /**
     * Set the lesson which this substitution is for.
     *
     * Keep it short, the recommended format is e.g. "1" for single lessons and "5-6" for multiple lessons. But some
     * schools use different ways to name their lessons. Required.
     *
     * @param lesson the lesson
     */
    public void setLesson(@NotNull String lesson) {
        this.lesson = lesson;
    }

    /**
     * Get the type of this substitution. Something like "substitution",
     * "sancellation", "different room" ("Vertretung", "Entfall", "anderer Raum") etc.
     *
     * @return the type
     */
    @NotNull
    public String getType() {
        return type;
    }

    /**
     * Set the type of this substitution. Something like "substitution", "cancellation", "different room"
     * ("Vertretung", "Entfall", "anderer Raum") etc. Required.
     *
     * @param type the type to set
     */
    public void setType(@NotNull String type) {
        this.type = type;
    }

    /**
     * Get the subject that will be taught in this lesson.
     *
     * @return the subject
     */
    @Nullable
    public String getSubject() {
        return subject;
    }

    /**
     * Set the subject that will be taught in this lesson.
     *
     * @param subject the subject to set
     */
    public void setSubject(@Nullable String subject) {
        this.subject = subject;
    }

    /**
     * Get the subject that would have been taught in this lesson according to the regular schedule.
     *
     * @return the previous subject
     */
    @Nullable
    public String getPreviousSubject() {
        return previousSubject;
    }

    /**
     * Set the subject that would have been taught in this lesson according to the regular schedule.
     *
     * @param previousSubject the previous subject to set
     */
    public void setPreviousSubject(@Nullable String previousSubject) {
        this.previousSubject = previousSubject;
    }

    /**
     * Get the teacher giving this lesson. Abbreviations are used in most cases.
     * If there are multiple teachers, they are returned as a comma-separated list. See also {@link #getTeachers()}.
     *
     * @return the teacher
     */
    @Nullable
    public String getTeacher() {
        return teachers.size() > 0 ? SubstitutionTextUtils.joinTeachers(teachers) : null;
    }

    /**
     * Get the teachers giving this lesson. Abbreviations are used in most cases.
     *
     * @return the teachers
     */
    public Set<String> getTeachers() {
        return teachers;
    }

    /**
     * Set the teacher giving this lesson. Abbreviations are used in most cases.
     * If there are multiple teachers, use {@link #setTeachers(Set)} instead.
     *
     * @param teacher the teacher to set
     */
    public void setTeacher(@Nullable String teacher) {
        teachers.clear();
        if (teacher != null) teachers.add(teacher);
    }

    /**
     * Set the teachers giving this lesson. Abbreviations are used in most cases.
     *
     * @param teachers the teachers to set
     */
    public void setTeachers(Set<String> teachers) {
        this.teachers = teachers;
    }

    /**
     * Get the teacher who would have given this lesson according to the regular schedule. Abbreviations are used in
     * most cases.
     * If there are multiple teachers, they are returned as a comma-separated list. See also
     * {@link #getPreviousTeachers()}.
     *
     * @return the previous teacher
     */
    @Nullable
    public String getPreviousTeacher() {
        return previousTeachers.size() > 0 ? SubstitutionTextUtils.joinTeachers(previousTeachers) : null;
    }

    /**
     * Get the teachers who would have given this lesson according to the regular schedule. Abbreviations are used in
     * most cases.
     *
     * @return the previous teachers
     */
    public Set<String> getPreviousTeachers() {
        return previousTeachers;
    }

    /**
     * Set the teacher who would have given this lesson according to the regular schedule. Abbreviations are used in
     * most cases.
     * If there are multiple teachers, use {@link #setPreviousTeachers(Set)} instead.
     *
     * @param previousTeacher the previous teacher to set
     */
    public void setPreviousTeacher(@Nullable String previousTeacher) {
        previousTeachers.clear();
        if (previousTeacher != null) previousTeachers.add(previousTeacher);
    }

    /**
     * Set the teachers who would have given this lesson according to the regular schedule. Abbreviations are used in
     * most cases.
     *
     * @param previousTeachers the previous teachers to set
     */
    public void setPreviousTeachers(Set<String> previousTeachers) {
        this.previousTeachers = previousTeachers;
    }

    /**
     * Get the room in which this lesson will be taught.
     *
     * @return the room
     */
    @Nullable
    public String getRoom() {
        return room;
    }

    /**
     * Set the room in which this lesson will be taught.
     *
     * @param room the room to set
     */
    public void setRoom(@Nullable String room) {
        this.room = room;
    }

    /**
     * Get the room in which this lesson would have been taught according to the regular schedule.
     *
     * @return the previous room
     */
    @Nullable
    public String getPreviousRoom() {
        return previousRoom;
    }

    /**
     * Set the room in which this lesson would have been taught according to the regular schedule.
     *
     * @param previousRoom the previous room to set
     */
    public void setPreviousRoom(@Nullable String previousRoom) {
        this.previousRoom = previousRoom;
    }

    /**
     * Get an additional description of this substitution, such as "tasks on page 42" or "moved to 6th lesson."
     *
     * @return the description
     */
    @Nullable
    public String getDesc() {
        return desc;
    }

    /**
     * Set an additional description of this substitution, such as "tasks on page 42" or "moved to 6th lesson."
     *
     * @param desc the description to set
     */
    public void setDesc(@Nullable String desc) {
        this.desc = desc;
    }

    /**
     * Get the color in which this substitution should be shown on the schedule as hexadecimal representation (such
     * as {@code #FF0000} for red).
     *
     * @return the color to set
     */
    @NotNull
    public String getColor() {
        return color;
    }

    /**
     * Set the color in which this substitution should be shown on the schedule as hexadecimal representation (such
     * as {@code #FF0000} for red). Normally, this should only be done by
     * {@link me.vertretungsplan.parser.ColorProvider}.
     * Required.
     *
     * @param color the color to set
     */
    public void setColor(@NotNull String color) {
        this.color = color;
    }

    /**
     * Get the "substitution from" ("Vertr. von") value. This can be found on some Untis schedules when a lesson was
     * moved to another lesson or day and determines the date and lesson where this lesson would have been taught
     * according to the normal schedule.
     *
     * @return the substitution from
     */
    @Nullable
    public String getSubstitutionFrom() {
        return substitutionFrom;
    }

    /**
     * Set the "substitution from" ("Vertr. von") value. This can be found on some Untis schedules when a lesson was
     * moved to another lesson or day and determines the date and lesson where this lesson would have been taught
     * according to the normal schedule.
     *
     * @param substitutionFrom the substitution from to set
     */
    public void setSubstitutionFrom(@Nullable String substitutionFrom) {
        this.substitutionFrom = substitutionFrom;
    }

    /**
     * Get the "teacher to" ("(Le.) nach") value. This can be found on some Untis schedules when a lesson was
     * moved to another lesson or day and determines the date and lesson where this lesson will be taught instead.
     *
     * @return the teacher to
     */
    @Nullable
    public String getTeacherTo() {
        return teacherTo;
    }

    /**
     * Set the "teacher to" ("(Le.) nach") value. This can be found on some Untis schedules when a lesson was
     * moved to another lesson or day and determines the date and lesson where this lesson will be taught instead.
     *
     * @param teacherTo the teacher to to set
     */
    public void setTeacherTo(@Nullable String teacherTo) {
        this.teacherTo = teacherTo;
    }

    /**
     * Check if this substitution equals another one, but excluding the classes. This is used to merge two
     * substitutions with the same data and different classes automatically.
     *
     * @param o the substitution (or other object) to compare
     * @return boolean indicating whether all fields of the two substitutions, excluding the classes, are equal
     */
    @SuppressWarnings("NegatedConditionalExpression")
    public boolean equalsExcludingClasses(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Substitution that = (Substitution) o;

        return new EqualsBuilder()
                .append(lesson, that.lesson)
                .append(type, that.type)
                .append(subject, that.subject)
                .append(previousSubject, that.previousSubject)
                .append(teachers, that.teachers)
                .append(previousTeachers, that.previousTeachers)
                .append(room, that.room)
                .append(previousRoom, that.previousRoom)
                .append(desc, that.desc)
                .append(color, that.color)
                .append(substitutionFrom, that.substitutionFrom)
                .append(teacherTo, that.teacherTo).isEquals();
    }

    /**
     * Check if this substitution equals another one, but excluding the teachers. This is used to merge two
     * substitutions with the same data and different teachers automatically.
     *
     * @param o the substitution (or other object) to compare
     * @return boolean indicating whether all fields of the two substitutions, excluding the teachers, are equal
     */
    @SuppressWarnings("NegatedConditionalExpression")
    public boolean equalsExcludingTeachers(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Substitution that = (Substitution) o;

        return new EqualsBuilder()
                .append(lesson, that.lesson)
                .append(type, that.type)
                .append(subject, that.subject)
                .append(previousSubject, that.previousSubject)
                .append(classes, that.classes)
                .append(previousTeachers, that.previousTeachers)
                .append(room, that.room)
                .append(previousRoom, that.previousRoom)
                .append(desc, that.desc)
                .append(color, that.color)
                .append(substitutionFrom, that.substitutionFrom)
                .append(teacherTo, that.teacherTo).isEquals();
    }

    /**
     * Check if this substitution equals another one, but excluding the previous teachers. This is used to merge two
     * substitutions with the same data and different previous teachers automatically.
     *
     * @param o the substitution (or other object) to compare
     * @return boolean indicating whether all fields of the two substitutions, excluding the previous teachers, are
     * equal
     */
    @SuppressWarnings("NegatedConditionalExpression")
    public boolean equalsExcludingPreviousTeachers(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Substitution that = (Substitution) o;

        return new EqualsBuilder()
                .append(lesson, that.lesson)
                .append(type, that.type)
                .append(subject, that.subject)
                .append(previousSubject, that.previousSubject)
                .append(teachers, that.teachers)
                .append(classes, that.classes)
                .append(room, that.room)
                .append(previousRoom, that.previousRoom)
                .append(desc, that.desc)
                .append(color, that.color)
                .append(substitutionFrom, that.substitutionFrom)
                .append(teacherTo, that.teacherTo).isEquals();
    }

    @Override
    public String toString() {
        return toString(SubstitutionSchedule.Type.STUDENT);
    }

    /**
     * Get a string representation of the substitution, using different wording depending on the type. Useful for
     * debugging (console output).
     *
     * @param type the type of the {@link SubstitutionSchedule}. Affects the format in which the fields are output
     * @return a string representation of this substitution
     */
    public String toString(SubstitutionSchedule.Type type) {
        switch (type) {
            case STUDENT:
                return lesson + " " + classes.toString() + " " + getType() + " " + getText();
            case TEACHER:
                return lesson + " " + getPreviousAndCurrentTeacherText() + " " + getType() + " " + getTeacherText() +
                        " (" +
                        (substitutionFrom != null ? substitutionFrom : "") + "/" +
                        (teacherTo != null ? teacherTo : "") + ")";
            default:
                return null;
        }
    }

    public Substitution clone() throws CloneNotSupportedException {
        return (Substitution) super.clone();
    }
}