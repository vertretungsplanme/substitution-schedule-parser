/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import me.vertretungsplan.objects.diff.SubstitutionDiff;
import org.junit.experimental.theories.*;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import static me.vertretungsplan.objects.SubstitutionTextUtils.hasData;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

@RunWith(Theories.class)
public class SubstitutionTextUtilsTest {

    @Theory
    public void testSubstitutionText(@Strings(values = {"", "Fach"}) String subject,
                                     @Strings(values = {"", "vorheriges Fach"}) String previousSubject,
                                     @Strings(values = {"", "Lehrer"}) String teacher,
                                     @Strings(values = {"", "vorheriger Lehrer"}) String previousTeacher,
                                     @Strings(values = {"", "Raum"}) String room,
                                     @Strings(values = {"", "vorheriger Raum"}) String previousRoom,
                                     @Strings(values = {"", "Beschreibung"}) String desc) throws Exception {
        Substitution subst = new Substitution();
        subst.setSubject(subject);
        subst.setPreviousSubject(previousSubject);
        subst.setTeacher(teacher);
        subst.setPreviousTeacher(previousTeacher);
        subst.setRoom(room);
        subst.setPreviousRoom(previousRoom);
        subst.setDesc(desc);

        String text = SubstitutionTextUtils.getText(subst);
        if (hasData(subject) || hasData(previousSubject) || hasData(teacher) || hasData(previousTeacher) ||
                hasData(room) || hasData(previousRoom) || hasData(desc)) {
            assertNotEquals(text, "");
        }
        //System.out.println(text);
    }

    @Theory
    public void testSubstitutionDiffText(@Strings(values = {"", "Fach"}) String subject1,
                                         @Strings(values = {"", "vorheriges Fach"}) String previousSubject1,
                                         @Strings(values = {"", "Lehrer"}) String teacher1,
                                         @Strings(values = {"", "vorheriger Lehrer"}) String previousTeacher1,
                                         @Strings(values = {"", "Raum"}) String room1,
                                         @Strings(values = {"", "vorheriger Raum"}) String previousRoom1,
                                         @Strings(values = {"", "Beschreibung"}) String desc1,

                                         @Strings(values = {"", "Fach", "Fach2"}) String subject2,
                                         @Strings(values = {"", "vorheriges Fach", "vorheriges Fach2"})
                                                 String previousSubject2,
                                         @Strings(values = {"", "Lehrer", "Lehrer2"}) String teacher2,
                                         @Strings(values = {"", "vorheriger Lehrer", "vorheriger Lehrer2"})
                                                 String previousTeacher2,
                                         @Strings(values = {"", "Raum", "Raum2"}) String room2,
                                         @Strings(values = {"", "vorheriger Raum", "vorheriger Raum2"})
                                                 String previousRoom2,
                                         @Strings(values = {"", "Beschreibung", "Beschreibung2"}) String desc2)
            throws Exception {
        assumeTrue(bothOrNoneEmpty(subject1, subject2));
        assumeTrue(bothOrNoneEmpty(previousSubject1, previousSubject2));
        assumeTrue(bothOrNoneEmpty(teacher1, teacher2));
        assumeTrue(bothOrNoneEmpty(previousTeacher1, previousTeacher2));
        assumeTrue(bothOrNoneEmpty(room1, room2));
        assumeTrue(bothOrNoneEmpty(previousRoom1, previousRoom2));
        assumeTrue(bothOrNoneEmpty(desc1, desc2));
        assumeTrue(complexity(subject1, subject2,
                previousSubject1, previousSubject2,
                teacher1, teacher2,
                previousTeacher1, previousTeacher2,
                room1, room2,
                previousRoom1, previousRoom2,
                desc1, desc2) <= SubstitutionDiff.MAX_COMPLEXITY);

        Substitution subst1 = new Substitution();
        subst1.setSubject(subject1);
        subst1.setPreviousSubject(previousSubject1);
        subst1.setTeacher(teacher1);
        subst1.setPreviousTeacher(previousTeacher1);
        subst1.setRoom(room1);
        subst1.setPreviousRoom(previousRoom1);
        subst1.setDesc(desc1);

        Substitution subst2 = new Substitution();
        subst2.setSubject(subject2);
        subst2.setPreviousSubject(previousSubject2);
        subst2.setTeacher(teacher2);
        subst2.setPreviousTeacher(previousTeacher2);
        subst2.setRoom(room2);
        subst2.setPreviousRoom(previousRoom2);
        subst2.setDesc(desc2);

        SubstitutionDiff diff = new SubstitutionDiff();
        diff.setOldSubstitution(subst1);
        diff.setNewSubstitution(subst2);

        String text = SubstitutionTextUtils.getText(diff);
        /*if (hasData(subject) || hasData(previousSubject) || hasData(teacher) || hasData(previousTeacher) ||
                hasData(room) || hasData(previousRoom) || hasData(desc)) {
            assertNotEquals(text, "");
        }*/
        //System.out.println(text);
    }

    private int complexity(String... args) {
        int complexity = 0;
        if (args.length % 2 != 0) throw new IllegalArgumentException("even number of arguments needed");
        for (int i = 0; i < args.length - 1; i += 2) {
            if (!args[i].equals(args[i + 1])) complexity++;
        }
        return complexity;
    }

    private boolean bothOrNoneEmpty(@Strings(values = {"", "Fach"}) String subject1,
                                    @Strings(values = {"", "Fach", "Fach2"})
                                            String subject2) {
        return subject1.equals("") && subject2.equals("") || !subject1.equals("") && !subject2.equals("");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @ParametersSuppliedBy(BetweenSupplier.class)
    public @interface Strings {
        String[] values();
    }

    public static class BetweenSupplier extends ParameterSupplier {
        @Override
        public List<PotentialAssignment> getValueSources(ParameterSignature sig) {
            List<PotentialAssignment> list = new ArrayList<>();
            Strings annotation = sig.getAnnotation(Strings.class);

            for (int i = 0; i < annotation.values().length; i++) {
                list.add(PotentialAssignment.forValue("strings", annotation.values()[i]));
            }
            return list;
        }
    }
}