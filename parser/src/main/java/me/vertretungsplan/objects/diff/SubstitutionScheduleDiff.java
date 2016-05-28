package me.vertretungsplan.objects.diff;

import me.vertretungsplan.objects.AdditionalInfo;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleDay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents the difference between two {@link SubstitutionSchedule}s
 */
public class SubstitutionScheduleDiff implements Cloneable {
    private List<AdditionalInfo> newAdditionalInfos;
    private List<AdditionalInfo> removedAdditionalInfos;
    private List<SubstitutionScheduleDay> newDays;
    private List<SubstitutionScheduleDayDiff> editedDays;
    private List<SubstitutionScheduleDay> removedDays;

    /**
     * Constructs a {@link SubstitutionScheduleDiff} from two {@link SubstitutionSchedule}s
     * @param a Old substitution schedule
     * @param b New substitution schedule
     * @return difference between the two schedules
     */
    public static SubstitutionScheduleDiff compare(SubstitutionSchedule a, SubstitutionSchedule b) {
        SubstitutionScheduleDiff diff = new SubstitutionScheduleDiff();
        diff.newAdditionalInfos = new ArrayList<>();
        diff.removedAdditionalInfos = new ArrayList<>();
        diff.newDays = new ArrayList<>();
        diff.editedDays = new ArrayList<>();
        diff.removedDays = new ArrayList<>();

        for (AdditionalInfo newInfo:b.getAdditionalInfos()) {
            if (!a.getAdditionalInfos().contains(newInfo)) {
                diff.newAdditionalInfos.add(newInfo);
            }
        }
        for (AdditionalInfo oldInfo:a.getAdditionalInfos()) {
            if (!b.getAdditionalInfos().contains(oldInfo)) {
                diff.removedAdditionalInfos.add(oldInfo);
            }
        }

        for (SubstitutionScheduleDay newDay:b.getDays()) {
            SubstitutionScheduleDay oldDay = findSameDateDay(newDay, a.getDays());
            if (oldDay != null) {
                SubstitutionScheduleDayDiff dayDiff = SubstitutionScheduleDayDiff.compare(oldDay,
                        newDay);
                if (dayDiff.isNotEmpty()) diff.editedDays.add(dayDiff);
            } else {
                diff.newDays.add(newDay);
            }
        }
        for (SubstitutionScheduleDay oldDay:a.getDays()) {
            SubstitutionScheduleDay newDay = findSameDateDay(oldDay, b.getDays());
            if (newDay == null) {
                diff.removedDays.add(oldDay);
            }
        }

        return diff;
    }

    private static SubstitutionScheduleDay findSameDateDay(SubstitutionScheduleDay day,
                                                           List<SubstitutionScheduleDay> days) {
        for (SubstitutionScheduleDay currentDay:days) {
            if (currentDay.equalsByDate(day)) {
                return currentDay;
            }
        }
        return null;
    }

    public static Set<SubstitutionDiff> filterByClass(String theClass, Set<SubstitutionDiff> substitutions) {
        if (theClass == null) return substitutions;
        Set<SubstitutionDiff> classSubstitutions = new HashSet<>();
        for (SubstitutionDiff substitution : substitutions) {
            if (substitution.getClasses().contains(theClass)) classSubstitutions.add(substitution);
        }
        return classSubstitutions;
    }

    public static Set<SubstitutionDiff> filterBySubject(Set<String> excludedSubjects,
                                                        Set<SubstitutionDiff> substitutions) {
        if (excludedSubjects == null || excludedSubjects.isEmpty()) return substitutions;
        Set<SubstitutionDiff> filteredSubstitutions = new HashSet<>();
        for (SubstitutionDiff substitution : substitutions) {
            if (!excludedSubjects.contains(substitution.getNewSubstitution().getSubject())) {
                filteredSubstitutions.add(substitution);
            }
        }
        return filteredSubstitutions;
    }

    /**
     * @return The list of {@link AdditionalInfo}s that were added to the schedule
     */
    public List<AdditionalInfo> getNewAdditionalInfos() {
        return newAdditionalInfos;
    }

    public void setNewAdditionalInfos(List<AdditionalInfo> newAdditionalInfos) {
        this.newAdditionalInfos = newAdditionalInfos;
    }

    /**
     * @return The list of {@link AdditionalInfo}s that were removed from the schedule
     */
    public List<AdditionalInfo> getRemovedAdditionalInfos() {
        return removedAdditionalInfos;
    }

    public void setRemovedAdditionalInfos(List<AdditionalInfo> removedAdditionalInfos) {
        this.removedAdditionalInfos = removedAdditionalInfos;
    }

    /**
     * @return The list of {@link SubstitutionScheduleDay}s that were added to the schedule
     */
    public List<SubstitutionScheduleDay> getNewDays() {
        return newDays;
    }

    public void setNewDays(List<SubstitutionScheduleDay> newDays) {
        this.newDays = newDays;
    }

    /**
     * @return The list of {@link SubstitutionScheduleDay}s that stayed on the schedule with
     * their differences represented by {@link SubstitutionScheduleDayDiff}s
     */
    public List<SubstitutionScheduleDayDiff> getEditedDays() {
        return editedDays;
    }

    public void setEditedDays(List<SubstitutionScheduleDayDiff> editedDays) {
        this.editedDays = editedDays;
    }

    /**
     * @return The list of {@link SubstitutionScheduleDay}s that were removed from the schedule
     */
    public List<SubstitutionScheduleDay> getRemovedDays() {
        return removedDays;
    }

    public void setRemovedDays(List<SubstitutionScheduleDay> removedDays) {
        this.removedDays = removedDays;
    }

    public boolean isEmpty() {
        boolean dayDiffsEmpty = true;
        for (SubstitutionScheduleDayDiff dayDiff:editedDays) {
            if (dayDiff.isNotEmpty()) {
                dayDiffsEmpty = false;
                break;
            }
        }
        return newAdditionalInfos.isEmpty() && removedAdditionalInfos.isEmpty() && newDays
                .isEmpty() && removedDays.isEmpty() && dayDiffsEmpty;
    }

    public SubstitutionScheduleDiff clone() {
        try {
            return (SubstitutionScheduleDiff) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public SubstitutionScheduleDiff filteredByClassAndExcludedSubject(String theClass, Set<String> excludedSubjects) {
        SubstitutionScheduleDiff filteredScheduleDiff = this.clone();
        filterDays(theClass, excludedSubjects, filteredScheduleDiff.getNewDays());
        filterDayDiffs(theClass, excludedSubjects, filteredScheduleDiff.getEditedDays());
        filterDays(theClass, excludedSubjects, filteredScheduleDiff.getRemovedDays());
        return filteredScheduleDiff;
    }

    private void filterDayDiffs(String theClass, Set<String> excludedSubjects,
                                List<SubstitutionScheduleDayDiff> dayDiffs) {
        for (int i = 0; i < dayDiffs.size(); i++) {
            SubstitutionScheduleDayDiff dayDiff = dayDiffs.get(i);
            SubstitutionScheduleDayDiff filteredDayDiff = dayDiff.clone();
            filteredDayDiff.setNewSubstitutions(
                    dayDiff.getNewSubstitutionsByClassAndExcludedSubject(theClass, excludedSubjects));
            filteredDayDiff.setRemovedSubstitutions(
                    dayDiff.getRemovedSubstitutionsByClassAndExcludedSubject(theClass, excludedSubjects));
            filteredDayDiff.setEditedSubstitutions(
                    dayDiff.getEditedSubstitutionsByClassAndExcludedSubject(theClass, excludedSubjects));
            dayDiffs.set(i, filteredDayDiff);
        }
    }

    private void filterDays(String theClass, Set<String> excludedSubjects, List<SubstitutionScheduleDay> days) {
        for (int i = 0; i < days.size(); i++) {
            SubstitutionScheduleDay day = days.get(i);
            SubstitutionScheduleDay filteredDay = day.clone();
            filteredDay.setSubstitutions(day.getSubstitutionsByClassAndExcludedSubject(theClass, excludedSubjects));
            days.set(i, filteredDay);
        }
    }
}
