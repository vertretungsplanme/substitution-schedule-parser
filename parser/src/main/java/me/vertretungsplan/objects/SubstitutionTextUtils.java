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

import me.vertretungsplan.objects.diff.SubstitutionDiff;
import name.fraser.neil.plaintext.DiffMatchPatch;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jetbrains.annotations.Contract;

import java.util.List;

public class SubstitutionTextUtils {
    public static String getText(Substitution substitution) {
        String subjectAndTeacher = subjectAndTeacher(substitution);
        String room = room(substitution);
        String desc = hasData(substitution.getDesc()) ? substitution.getDesc() : "";
        return formatOutput(subjectAndTeacher, room, desc);
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
        if (hasData(room) && hasData(previousRoom)) {
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
    public static boolean hasData(String string) {
        if (string != null) {
            String s = string.replaceAll("\\s", "");
            return !(s.equals("") || s.equals("---"));
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
            return String.format("%s in %s - %s", subjectAndTeacher, room, desc);
        } else if (!subjectAndTeacher.isEmpty() && !room.isEmpty()) {
            return String.format("%s in %s", subjectAndTeacher, room);
        } else if (!room.isEmpty() && !desc.isEmpty()) {
            return String.format("%s - %s", room, desc);
        } else if (!room.isEmpty()) {
            return room;
        } else if (!subjectAndTeacher.isEmpty() && !desc.isEmpty()) {
            return String.format("%s - %s", subjectAndTeacher, desc);
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
