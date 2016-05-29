/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects.diff;

import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.joda.time.LocalDate;

import java.util.*;

/**
 * Represents the difference between two {@link SubstitutionScheduleDay}s.
 */
public class SubstitutionScheduleDayDiff implements Cloneable {
    private LocalDate date;
    private String dateString;
    private Set<Substitution> newSubstitutions;
    private Set<SubstitutionDiff> editedSubstitutions;
    private Set<Substitution> removedSubstitutions;
    private List<String> newMessages;
    private List<String> removedMessages;

    public static SubstitutionScheduleDayDiff compare(SubstitutionScheduleDay a,
                                                      SubstitutionScheduleDay b) {
        if (!a.equalsByDate(b)) {
            throw new IllegalArgumentException("Days must have the same date");
        }
        SubstitutionScheduleDayDiff diff = new SubstitutionScheduleDayDiff();
        diff.date = a.getDate();
        diff.dateString = a.getDateString();
        diff.newMessages = new ArrayList<>();
        diff.removedMessages = new ArrayList<>();
        diff.newSubstitutions = new HashSet<>();
        diff.editedSubstitutions = new HashSet<>();
        diff.removedSubstitutions = new HashSet<>();

        for (String message : b.getMessages()) {
            if (!a.getMessages().contains(message)) {
                diff.newMessages.add(message);
            }
        }

        for (String message : a.getMessages()) {
            if (!b.getMessages().contains(message)) {
                diff.removedMessages.add(message);
            }
        }

        // save all old substitutions that were already handled here to speed up the second run
        Set<Substitution> handledOldSubstitutions = new HashSet<>();

        // first run: go through all new substitutions and search for matching old substitutions
        for (Substitution newSubstitution : b.getSubstitutions()) {
            if (a.getSubstitutions().contains(newSubstitution)) {
                // this substitution has not changed in any way
                handledOldSubstitutions.add(newSubstitution);
                continue;
            }

            Substitution oldSubstitution = findEqualSubtitutionExcludingClasses(newSubstitution, a.getSubstitutions(),
                    handledOldSubstitutions);
            if (oldSubstitution != null) {
                // The same substitution was already there, but the set of classes has changed
                Set<String> newClasses = new HashSet<>();
                for (String currentClass : newSubstitution.getClasses()) {
                    if (!oldSubstitution.getClasses().contains(currentClass)) {
                        newClasses.add(currentClass);
                    }
                }
                if (!newClasses.isEmpty()) {
                    diff.newSubstitutions.add(new Substitution(newSubstitution, newClasses));
                }

                Set<String> removedClasses = new HashSet<>();
                for (String currentClass : oldSubstitution.getClasses()) {
                    if (!newSubstitution.getClasses().contains(currentClass)) {
                        removedClasses.add(currentClass);
                    }
                }
                if (!removedClasses.isEmpty()) {
                    diff.removedSubstitutions.add(new Substitution(newSubstitution,
                            removedClasses));
                }
                handledOldSubstitutions.add(oldSubstitution);
                continue;
            }

            oldSubstitution = findSimilarSubstitution(newSubstitution, a.getSubstitutions(), handledOldSubstitutions);
            if (oldSubstitution != null) {
                // there is a similar substitution, create a SubstitutionDiff
                SubstitutionDiff substitutionDiff = SubstitutionDiff.compare(oldSubstitution, newSubstitution);
                // Only use the diff if its complexity is low enough. Otherwise, regard this as a new substitution.
                if (substitutionDiff.getComplexity() <= SubstitutionDiff.MAX_COMPLEXITY) {
                    diff.editedSubstitutions.add(substitutionDiff);
                    handledOldSubstitutions.add(oldSubstitution);
                    continue;
                }
            }

            // There seems to be no matching substitution, so this is new
            diff.newSubstitutions.add(newSubstitution);
        }

        // second run: go through all unhandled old substitutions -> they must have been removed
        for (Substitution oldSubstitution : a.getSubstitutions()) {
            if (!handledOldSubstitutions.contains(oldSubstitution)) {
                diff.removedSubstitutions.add(oldSubstitution);
            }
        }

        return diff;
    }

    private static int calculateSimilarityScore(Substitution a, Substitution b) {
        int score = 0;
        if (Objects.equals(a.getLesson(), b.getLesson())) score++;
        if (Objects.equals(a.getType(), b.getType())) score++;
        if (Objects.equals(a.getSubject(), b.getSubject())) score++;
        if (Objects.equals(a.getPreviousSubject(), b.getPreviousSubject())) score++;
        if (Objects.equals(a.getTeacher(), b.getTeacher())) score++;
        if (Objects.equals(a.getPreviousTeacher(), b.getPreviousTeacher())) score++;
        if (Objects.equals(a.getRoom(), b.getRoom())) score++;
        if (Objects.equals(a.getPreviousRoom(), b.getPreviousRoom())) score++;
        if (Objects.equals(a.getDesc(), b.getDesc())) score++;
        return score;
    }

    private static Substitution findSimilarSubstitution(Substitution subst, Set<Substitution> substs,
                                                        Set<Substitution> handledSubsts) {
        int maxScore = 0;
        Substitution maxScoreSubstitution = null;
        for (Substitution currentSubst : substs) {
            if (currentSubst.getClasses().equals(subst.getClasses())
                    && !handledSubsts.contains(currentSubst)) {
                int score = calculateSimilarityScore(currentSubst, subst);
                if (score > maxScore) {
                    maxScore = score;
                    maxScoreSubstitution = currentSubst;
                }
            }
        }
        if (maxScore > 0) {
            return maxScoreSubstitution;
        } else {
            return null;
        }
    }

    private static Substitution findEqualSubtitutionExcludingClasses(Substitution subst,
                                                                     Set<Substitution> substs,
                                                                     Set<Substitution> handledSubsts) {
        for (Substitution currentSubst : substs) {
            if (currentSubst.equalsExcludingClasses(subst) && !handledSubsts.contains(currentSubst)) {
                return currentSubst;
            }
        }
        return null;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDateString() {
        return dateString;
    }

    public void setDateString(String dateString) {
        this.dateString = dateString;
    }

    /**
     * @return the set of substitutions that were added to the schedule for this day
     */
    public Set<Substitution> getNewSubstitutions() {
        return newSubstitutions;
    }

    public void setNewSubstitutions(Set<Substitution> newSubstitutions) {
        this.newSubstitutions = newSubstitutions;
    }

    /**
     * @return the set of substitutions that were edited on the schedule for this day
     */
    public Set<SubstitutionDiff> getEditedSubstitutions() {
        return editedSubstitutions;
    }

    public void setEditedSubstitutions(Set<SubstitutionDiff> editedSubstitutions) {
        this.editedSubstitutions = editedSubstitutions;
    }

    /**
     * @return the set of substitutions that were removed from the schedule for this day
     */
    public Set<Substitution> getRemovedSubstitutions() {
        return removedSubstitutions;
    }

    public void setRemovedSubstitutions(Set<Substitution> removedSubstitutions) {
        this.removedSubstitutions = removedSubstitutions;
    }

    /**
     * @return the list of messages that were added to the schedule for this day
     */
    public List<String> getNewMessages() {
        return newMessages;
    }

    public void setNewMessages(List<String> newMessages) {
        this.newMessages = newMessages;
    }

    /**
     * @return the list of messages that were removed from the schedule for this day
     */
    public List<String> getRemovedMessages() {
        return removedMessages;
    }

    public void setRemovedMessages(List<String> removedMessages) {
        this.removedMessages = removedMessages;
    }

    public boolean isNotEmpty() {
        return !newMessages.isEmpty() || !removedMessages.isEmpty() || !newSubstitutions.isEmpty()
                || !removedSubstitutions.isEmpty() || !editedSubstitutions.isEmpty();
    }

    public Set<Substitution> getNewSubstitutionsByClassAndExcludedSubject(String theClass, Set<String> excludedSubjects) {
        return SubstitutionSchedule.filterBySubject(excludedSubjects, SubstitutionSchedule
                .filterByClass(theClass, newSubstitutions));
    }

    public Set<Substitution> getRemovedSubstitutionsByClassAndExcludedSubject(String theClass,
                                                                              Set<String> excludedSubjects) {
        return SubstitutionSchedule.filterBySubject(excludedSubjects, SubstitutionSchedule
                .filterByClass(theClass, removedSubstitutions));
    }

    public Set<SubstitutionDiff> getEditedSubstitutionsByClassAndExcludedSubject(String theClass,
                                                                                 Set<String> excludedSubjects) {
        return SubstitutionScheduleDiff.filterBySubject(excludedSubjects, SubstitutionScheduleDiff
                .filterByClass(theClass, editedSubstitutions));
    }

    public SubstitutionScheduleDayDiff clone() {
        try {
            return (SubstitutionScheduleDayDiff) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
