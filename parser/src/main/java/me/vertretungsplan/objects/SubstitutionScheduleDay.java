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

import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import java.util.*;

public class SubstitutionScheduleDay implements Cloneable {

	private LocalDate date;
	private String dateString;
	private LocalDateTime lastChange;
	private String lastChangeString;
	private Set<Substitution> substitutions;
	private List<String> messages;

	public SubstitutionScheduleDay() {
		substitutions = new HashSet<>();
		messages = new ArrayList<>();
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public String getDateString() {
		if (date != null) {
			return SubstitutionSchedule.DAY_DATE_FORMAT.print(date);
		} else if (dateString != null) {
			return dateString;
		} else {
			return null;
		}
	}

	public void setDateString(String textDate) {
		this.dateString = textDate;
	}

	public LocalDateTime getLastChange() {
		return lastChange;
	}

	public void setLastChange(LocalDateTime lastChange) {
		this.lastChange = lastChange;
	}

	public String getLastChangeString() {
		if (lastChange != null) {
			return SubstitutionSchedule.LAST_CHANGE_DATE_FORMAT.print(lastChange);
		} else if (lastChangeString != null) {
			return lastChangeString;
		} else {
			return null;
		}
	}

	public void setLastChangeString(String lastChangeString) {
		this.lastChangeString = lastChangeString;
	}

	public Set<Substitution> getSubstitutions() {
		return substitutions;
	}

	public void setSubstitutions(Set<Substitution> substitutions) {
		this.substitutions = substitutions;
	}

	public Set<Substitution> getSubstitutionsByClass(String theClass) {
		return SubstitutionSchedule.filterByClass(theClass, substitutions);
	}

	public List<String> getMessages() {
		return messages;
	}

	public void addMessage(String message) {
		messages.add(message);
	}

	public void addSubstitution(Substitution s) {
		// Look for equal substitutions for different classes and merge them to make dataset as small as possible
		for (Substitution substitution : getSubstitutions()) {
			if (substitution.equalsExcludingClasses(s)) {
				substitution.getClasses().addAll(s.getClasses());
				return;
			}
		}
		getSubstitutions().add(s);
	}

	public void addAllSubstitutions(Substitution... substitutions) {
		for (Substitution s : substitutions) addSubstitution(s);
	}

	public void addAllSubstitutions(Collection<? extends Substitution> substitutions) {
		for (Substitution s : substitutions) addSubstitution(s);
	}

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

	public boolean equalsByDate(SubstitutionScheduleDay other) {
		if (getDate() != null) {
			return getDate().equals(other.getDate());
		} else if (getDateString() != null) {
			return getDateString().equals(other.getDateString());
		} else {
			return other.getDate() == null && other.getDateString() == null;
		}
	}

	public Set<Substitution> getSubstitutionsByClassAndExcludedSubject(String theClass,
																	   Set<String> excludedSubjects) {
		return SubstitutionSchedule.filterBySubject(excludedSubjects, SubstitutionSchedule
				.filterByClass(theClass, substitutions));
	}

	public SubstitutionScheduleDay clone() {
		try {
			return (SubstitutionScheduleDay) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();

		builder.append(getDateString()).append("\n");
		builder.append("----------------------\n\n");

		builder.append("last change: ").append(getLastChangeString()).append("\n\n");

		for (Substitution subst:substitutions) builder.append(subst.toString()).append("\n");

		builder.append("\n");
		for (String message:messages) builder.append(message).append("\n");

		return builder.toString();
	}
}
