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
