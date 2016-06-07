/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import me.vertretungsplan.utils.SubstitutionTextUtils;

import java.util.HashSet;
import java.util.Set;

public class Substitution {

	private Set<String> classes;
	private String lesson;
	private String type;
	private String subject;
	private String previousSubject;
	private String teacher;
	private String previousTeacher;
	private String room;
	private String previousRoom;
	private String desc;
	private String color;
	private String substitutionFrom;
	private String teacherTo;

	public Substitution() {
		classes = new HashSet<>();
	}

	/**
	 * Creates a copy of a substitution with a different set of classes
	 * @param substitution the substitution to copy
	 * @param classes the set of classes to use
	 */
	public Substitution(Substitution substitution, Set<String> classes) {
		this.classes = classes;
		this.lesson = substitution.lesson;
		this.type = substitution.type;
		this.subject = substitution.type;
		this.previousSubject = substitution.previousSubject;
		this.teacher = substitution.teacher;
		this.previousTeacher = substitution.previousTeacher;
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
	 * @return A text describing the substitudion (designed to be shown together with {@link #getTeachers()}, type and
	 * lesson)
	 */
	public String getTeacherText() {
		return SubstitutionTextUtils.getTeacherText(this);
	}

	/**
	 * @return A text describing the current and previous teacher
	 */
	public String getTeachers() {
		return SubstitutionTextUtils.getTeachers(this);
	}

	public Set<String> getClasses() {
		return classes;
	}

	public void setClasses(Set<String> classes) {
		this.classes = classes;
	}

	public String getLesson() {
		return lesson;
	}

	public void setLesson(String lesson) {
		this.lesson = lesson;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getPreviousSubject() {
		return previousSubject;
	}

	public void setPreviousSubject(String previousSubject) {
		this.previousSubject = previousSubject;
	}

	public String getTeacher() {
		return teacher;
	}

	public void setTeacher(String teacher) {
		this.teacher = teacher;
	}

	public String getPreviousTeacher() {
		return previousTeacher;
	}

	public void setPreviousTeacher(String previousTeacher) {
		this.previousTeacher = previousTeacher;
	}

	public String getRoom() {
		return room;
	}

	public void setRoom(String room) {
		this.room = room;
	}

	public String getPreviousRoom() {
		return previousRoom;
	}

	public void setPreviousRoom(String previousRoom) {
		this.previousRoom = previousRoom;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public String getSubstitutionFrom() {
		return substitutionFrom;
	}

	public void setSubstitutionFrom(String substitutionFrom) {
		this.substitutionFrom = substitutionFrom;
	}

	public String getTeacherTo() {
		return teacherTo;
	}

	public void setTeacherTo(String teacherTo) {
		this.teacherTo = teacherTo;
	}

	@SuppressWarnings("NegatedConditionalExpression")
	public boolean equalsExcludingClasses(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Substitution that = (Substitution) o;

		if (lesson != null ? !lesson.equals(that.lesson) : that.lesson != null) return false;
		if (type != null ? !type.equals(that.type) : that.type != null) return false;
		if (subject != null ? !subject.equals(that.subject) : that.subject != null) return false;
		if (previousSubject != null ? !previousSubject.equals(that.previousSubject) : that.previousSubject != null)
			return false;
		if (teacher != null ? !teacher.equals(that.teacher) : that.teacher != null) return false;
		if (previousTeacher != null ? !previousTeacher.equals(that.previousTeacher) : that.previousTeacher != null)
			return false;
		if (room != null ? !room.equals(that.room) : that.room != null) return false;
		if (previousRoom != null ? !previousRoom.equals(that.previousRoom) : that.previousRoom != null) return false;
		if (desc != null ? !desc.equals(that.desc) : that.desc != null) return false;
		if (color != null ? !color.equals(that.color) : that.color != null) return false;
		if (substitutionFrom != null ? !substitutionFrom.equals(that.substitutionFrom) :
				that.substitutionFrom != null) {
			return false;
		}
		if (teacherTo != null ? !teacherTo.equals(that.teacherTo) : that.teacherTo != null) return false;
		return true;
	}

	public int hashCodeExcludingClasses() {
		int result = lesson != null ? lesson.hashCode() : 0;
		result = 31 * result + (type != null ? type.hashCode() : 0);
		result = 31 * result + (subject != null ? subject.hashCode() : 0);
		result = 31 * result + (previousSubject != null ? previousSubject.hashCode() : 0);
		result = 31 * result + (teacher != null ? teacher.hashCode() : 0);
		result = 31 * result + (previousTeacher != null ? previousTeacher.hashCode() : 0);
		result = 31 * result + (room != null ? room.hashCode() : 0);
		result = 31 * result + (previousRoom != null ? previousRoom.hashCode() : 0);
		result = 31 * result + (desc != null ? desc.hashCode() : 0);
		result = 31 * result + (color != null ? color.hashCode() : 0);
		result = 31 * result + (substitutionFrom != null ? substitutionFrom.hashCode() : 0);
		result = 31 * result + (teacherTo != null ? teacherTo.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return toString(SubstitutionSchedule.Type.STUDENT);
	}

	public String toString(SubstitutionSchedule.Type type) {
		switch (type) {
			case STUDENT:
				return classes.toString() + " " + getType() + " " + getText();
			case TEACHER:
				return getTeachers() + " " + getType() + " " + getTeacherText() + " (" + (substitutionFrom != null ?
						substitutionFrom : "") + "/" + (teacherTo != null ? teacherTo : "") + ")";
			default:
				return null;
		}
	}
}