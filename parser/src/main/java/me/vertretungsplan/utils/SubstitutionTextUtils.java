/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.utils;

import com.paour.comparator.NaturalOrderComparator;
import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.diff.SubstitutionDiff;
import name.fraser.neil.plaintext.DiffMatchPatch;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubstitutionTextUtils {
    public static String getText(Substitution substitution) {
        String subjectAndTeacher = subjectAndTeacher(substitution);
        String room = room(substitution);
        String desc = hasData(substitution.getDesc()) ? substitution.getDesc() : "";
        return formatOutput(subjectAndTeacher, room, desc);
    }

    public static String getTeacherText(Substitution substitution) {
        String subjectAndClass = subjectAndClass(substitution);
        String room = room(substitution);
        String desc = hasData(substitution.getDesc()) ? substitution.getDesc() : "";
        return formatOutput(subjectAndClass, room, desc);
    }

    public static String getTeachers(Substitution substitution) {
        if (hasData(substitution.getTeacher()) && hasData(substitution.getPreviousTeacher()) && !substitution
                .getTeacher().equals(substitution.getPreviousTeacher())) {
            return substitution.getTeacher() + " statt " + substitution.getPreviousTeacher();
        } else if (hasData(substitution.getTeacher())) {
            return substitution.getTeacher();
        } else if (hasData(substitution.getPreviousTeacher())) {
            return substitution.getPreviousTeacher();
        } else {
            return "";
        }
    }

    public static String getText(SubstitutionDiff diff) {
        Substitution oldSubst = diff.getOldSubstitution();
        Substitution newSubst = diff.getNewSubstitution();

        String subjectAndTeacher = diff(subjectAndTeacher(oldSubst), subjectAndTeacher(newSubst));
        String room = diff(room(oldSubst), room(newSubst));
        String desc = diff(oldSubst.getDesc(), newSubst.getDesc());
        return formatOutput(subjectAndTeacher, room, desc);
    }

    private static String room(Substitution substitution) {
        String room = substitution.getRoom();
        String previousRoom = substitution.getPreviousRoom();
        if (hasData(room) && hasData(previousRoom) && !room.equals(previousRoom)) {
            return String.format("%s statt %s", room, previousRoom);
        } else if (hasData(room)) {
            return room;
        } else if (hasData(previousRoom)) {
            return previousRoom;
        } else {
            return "";
        }
    }

    private static String subjectAndTeacher(Substitution substitution) {
        String subject = substitution.getSubject();
        String previousSubject = substitution.getPreviousSubject();
        String teacher = substitution.getTeacher();
        String previousTeacher = substitution.getPreviousTeacher();
        if (hasData(subject) && hasData(previousSubject) && !subject.equals(previousSubject)) {
            return subjectAndTeacher(subject, previousSubject, teacher, previousTeacher);
        } else if (hasData(subject) && hasData(previousSubject) && subject.equals(previousSubject) ||
                hasData(subject) && !hasData(previousSubject)) {
            return subjectAndTeacher(subject, teacher, previousTeacher);
        } else if (!hasData(subject) && hasData(previousSubject)) {
            return subjectAndTeacher(previousSubject, teacher, previousTeacher);
        } else if (!hasData(subject) && !hasData(previousSubject)) {
            return subjectAndTeacher(teacher, previousTeacher);
        }
        throw new MissingCaseException();
    }

    private static String subjectAndClass(Substitution substitution) {
        String subject = substitution.getSubject();
        String previousSubject = substitution.getPreviousSubject();
        String klasse = joinClasses(substitution.getClasses());
        if (hasData(subject) && hasData(previousSubject) && !subject.equals(previousSubject)) {
            return subjectAndTeacher(subject, previousSubject, klasse, klasse);
        } else if (hasData(subject) && hasData(previousSubject) && subject.equals(previousSubject) ||
                hasData(subject) && !hasData(previousSubject)) {
            return subjectAndTeacher(subject, klasse, klasse);
        } else if (!hasData(subject) && hasData(previousSubject)) {
            return subjectAndTeacher(previousSubject, klasse, klasse);
        } else if (!hasData(subject) && !hasData(previousSubject)) {
            return subjectAndTeacher(klasse, klasse);
        }
        throw new MissingCaseException();
    }

    public static String joinClasses(Set<String> classes) {
        List<String> list = new ArrayList<>(classes);
        Collections.sort(list, new NaturalOrderComparator());
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        String beginning = null;
        Pattern beginningRegex = Pattern.compile("^(.*\\d+)(\\w+)$");
        for (String string : list) {
            if (first) {
                Matcher matcher = beginningRegex.matcher(string);
                if (matcher.find()) {
                    beginning = matcher.group(1);
                }
                builder.append(string);
                first = false;
            } else {
                Matcher matcher = beginningRegex.matcher(string);
                if (matcher.find()) {
                    String newBeginning = matcher.group(1);
                    if (newBeginning.equals(beginning)) {
                        builder.append(matcher.group(2));
                    } else {
                        builder.append(", ");
                        builder.append(string);
                        beginning = newBeginning;
                    }
                } else {
                    builder.append(", ");
                    builder.append(string);
                    beginning = null;
                }
            }
        }
        return builder.toString();
    }

    public static String joinTeachers(Set<String> teachers) {
        List<String> list = new ArrayList<>(teachers);
        Collections.sort(list);
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String teacher : list) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append(teacher);
        }
        return builder.toString();
    }

    private static String subjectAndTeacher(String subject, String previousSubject, String teacher,
                                            String previousTeacher) {
        if (hasData(teacher) && hasData(previousTeacher) && !teacher.equals(previousTeacher)) {
            return changedSubjectWithChangedTeacher(subject, previousSubject, teacher, previousTeacher);
        } else if (hasData(teacher) && hasData(previousTeacher) && teacher.equals(previousTeacher) ||
                hasData(teacher) && !hasData(previousTeacher)) {
            return changedSubjectWithTeacher(subject, previousSubject, teacher);
        } else if (!hasData(teacher) && hasData(previousTeacher)) {
            return changedSubjectWithTeacher(subject, previousSubject, previousTeacher);
        } else if (!hasData(teacher) && !hasData(previousTeacher)) {
            return changedSubject(subject, previousSubject);
        }
        throw new MissingCaseException();
    }

    private static String subjectAndTeacher(String subject, String teacher, String previousTeacher) {
        if (hasData(teacher) && hasData(previousTeacher) && !teacher.equals(previousTeacher)) {
            return subjectWithChangedTeacher(subject, teacher, previousTeacher);
        } else if (hasData(teacher) && hasData(previousTeacher) && teacher.equals(previousTeacher) ||
                hasData(teacher) && !hasData(previousTeacher)) {
            return subjectWithTeacher(subject, teacher);
        } else if (!hasData(teacher) && hasData(previousTeacher)) {
            return subjectWithTeacher(subject, previousTeacher);
        } else if (!hasData(teacher) && !hasData(previousTeacher)) {
            return subject;
        }
        throw new MissingCaseException();
    }

    private static String subjectAndTeacher(String teacher, String previousTeacher) {
        if (hasData(teacher) && hasData(previousTeacher) && !teacher.equals(previousTeacher)) {
            return changedTeacher(teacher, previousTeacher);
        } else if (hasData(teacher) && hasData(previousTeacher) && teacher.equals(previousTeacher) ||
                hasData(teacher) && !hasData(previousTeacher)) {
            return teacher;
        } else if (!hasData(teacher) && hasData(previousTeacher)) {
            return previousTeacher;
        } else if (!hasData(teacher) && !hasData(previousTeacher)) {
            return "";
        }
        throw new MissingCaseException();
    }

    private static String changedSubjectWithChangedTeacher(String subject, String previousSubject, String teacher,
                                                           String previousTeacher) {
        return String.format("%s (%s) statt %s (%s)", subject, teacher, previousSubject, previousTeacher);
    }

    private static String subjectWithChangedTeacher(String subject, String teacher, String previousTeacher) {
        return String.format("%s (%s statt %s)", subject, teacher, previousTeacher);
    }

    private static String changedSubjectWithTeacher(String subject, String previousSubject, String teacher) {
        return String.format("%s statt %s (%s)", subject, previousSubject, teacher);
    }

    private static String subjectWithTeacher(String subject, String teacher) {
        return String.format("%s (%s)", subject, teacher);
    }

    private static String changedSubject(String subject, String previousSubject) {
        return String.format("%s statt %s", subject, previousSubject);
    }

    private static String changedTeacher(String teacher, String previousTeacher) {
        return String.format("%s statt %s", teacher, previousTeacher);
    }

    @Contract(pure = true)
    static boolean hasData(String string) {
        if (string != null) {
            String s = string.replaceAll("\\s", "");
            return !(s.isEmpty() || s.equals("---"));
        } else {
            return false;
        }
    }

    private static String diff(String oldS, String newS) {
        if (hasData(oldS) && hasData(newS)) {
            List<DiffMatchPatch.Diff> diffs = new DiffMatchPatch().diff_main(oldS, newS);
            StringBuilder builder = new StringBuilder();
            for (DiffMatchPatch.Diff diff : diffs) {
                String text = StringEscapeUtils.escapeHtml4(diff.text);
                switch (diff.operation) {
                    case INSERT:
                        builder.append("<ins>").append(text).append("</ins>");
                        break;
                    case DELETE:
                        builder.append("<del>").append(text).append("</del>");
                        break;
                    case EQUAL:
                        builder.append(text);
                        break;
                }
            }
            return builder.toString();
        } else {
            return commonDiff(oldS, newS);
        }
    }

    private static String simpleDiff(String oldS, String newS) {
        if (hasData(oldS) && hasData(newS)) {
            if (oldS.equals(newS)) {
                return StringEscapeUtils.escapeHtml4(oldS);
            } else {
                return String.format("<del>%s</del><ins>%s</ins>", StringEscapeUtils.escapeHtml4(oldS),
                        StringEscapeUtils.escapeHtml4(newS));
            }
        } else {
            return commonDiff(oldS, newS);
        }
    }

    private static String commonDiff(String oldS, String newS) {
        if (hasData(oldS)) {
            return String.format("<del>%s</del>", StringEscapeUtils.escapeHtml4(oldS));
        } else if (hasData(newS)) {
            return String.format("<ins>%s</ins>", StringEscapeUtils.escapeHtml4(newS));
        } else {
            return "";
        }
    }

    private static String formatOutput(String subjectAndTeacher, String room, String desc) {
        if (!subjectAndTeacher.isEmpty() && !room.isEmpty() && !desc.isEmpty()) {
            return String.format("%s in %s – %s", subjectAndTeacher, room, desc);
        } else if (!subjectAndTeacher.isEmpty() && !room.isEmpty()) {
            return String.format("%s in %s", subjectAndTeacher, room);
        } else if (!room.isEmpty() && !desc.isEmpty()) {
            return String.format("%s – %s", room, desc);
        } else if (!room.isEmpty()) {
            return room;
        } else if (!subjectAndTeacher.isEmpty() && !desc.isEmpty()) {
            return String.format("%s – %s", subjectAndTeacher, desc);
        } else if (!subjectAndTeacher.isEmpty()) {
            return subjectAndTeacher;
        } else if (!desc.isEmpty()) {
            return desc;
        } else {
            return "";
        }
    }

    private static class MissingCaseException extends RuntimeException {
    }
}
