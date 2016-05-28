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

package me.vertretungsplan.objects.diff;

import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionTextUtils;

import java.util.Objects;
import java.util.Set;

public class SubstitutionDiff {
    public static final int MAX_COMPLEXITY = 3;
    private Substitution oldSubstitution;
    private Substitution newSubstitution;

    public static SubstitutionDiff compare(Substitution oldSubstitution, Substitution newSubstitution) {
        SubstitutionDiff diff = new SubstitutionDiff();
        diff.oldSubstitution = oldSubstitution;
        diff.newSubstitution = newSubstitution;
        if (!oldSubstitution.getClasses().equals(newSubstitution.getClasses())) {
            throw new IllegalArgumentException("classes must be equal");
        }
        return diff;
    }

    public Substitution getOldSubstitution() {
        return oldSubstitution;
    }

    public void setOldSubstitution(Substitution oldSubstitution) {
        this.oldSubstitution = oldSubstitution;
    }

    public Substitution getNewSubstitution() {
        return newSubstitution;
    }

    public void setNewSubstitution(Substitution newSubstitution) {
        this.newSubstitution = newSubstitution;
    }

    public String getText() {
        return SubstitutionTextUtils.getText(this);
    }

    @SuppressWarnings({"EmptyMethod", "UnusedParameters"})
    public void setText(String text) {
        // Do nothing. Needed for Jackson
    }

    public int getComplexity() {
        int complexity = 0;
        if (!Objects.equals(oldSubstitution.getLesson(), newSubstitution.getLesson())) complexity++;
        if (!Objects.equals(oldSubstitution.getType(), newSubstitution.getType())) complexity++;
        if (!Objects.equals(oldSubstitution.getSubject(), newSubstitution.getSubject())) complexity++;
        if (!Objects.equals(oldSubstitution.getPreviousSubject(), newSubstitution.getPreviousSubject())) complexity++;
        if (!Objects.equals(oldSubstitution.getTeacher(), newSubstitution.getTeacher())) complexity++;
        if (!Objects.equals(oldSubstitution.getPreviousTeacher(), newSubstitution.getPreviousTeacher())) complexity++;
        if (!Objects.equals(oldSubstitution.getRoom(), newSubstitution.getRoom())) complexity++;
        if (!Objects.equals(oldSubstitution.getPreviousRoom(), newSubstitution.getPreviousRoom())) complexity++;
        if (!Objects.equals(oldSubstitution.getDesc(), newSubstitution.getDesc())) complexity++;
        return complexity;
    }

    public Set<String> getClasses() {
        if (!oldSubstitution.getClasses().equals(newSubstitution.getClasses())) {
            throw new IllegalArgumentException("classes must be equal");
        }
        return oldSubstitution.getClasses();
    }
}
