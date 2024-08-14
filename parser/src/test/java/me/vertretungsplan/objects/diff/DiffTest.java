/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects.diff;

import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.*;

public class DiffTest {
    @Test
    public void testDiffSame() {
        Substitution s1 = new Substitution();
        s1.setClasses(new HashSet<>(Arrays.asList(new String[]{"07B"})));
        s1.setLesson("6");
        s1.setType("Enfall");
        s1.setSubject("Entfall");
        s1.setPreviousSubject("SPA");
        s1.setTeachers(new HashSet<>(Arrays.asList(new String[]{"KW", "ER"})));
        s1.setPreviousTeachers(new HashSet<>());
        s1.setColor("#F44336");
        s1.setDesc("fällt aus");

        Substitution s2 = new Substitution();
        s2.setClasses(new HashSet<>(Arrays.asList(new String[]{"07B"})));
        s2.setLesson("6");
        s2.setType("Enfall");
        s2.setSubject("Entfall");
        s2.setPreviousSubject("SPA");
        s2.setTeachers(new HashSet<>(Arrays.asList(new String[]{"KW", "ER"})));
        s2.setPreviousTeachers(new HashSet<>());
        s2.setColor("#F44336");
        s2.setDesc("fällt aus");

        assertEquals(s1, s2);

        SubstitutionScheduleDay d1 = new SubstitutionScheduleDay();
        d1.addSubstitution(s1);
        SubstitutionScheduleDay d2 = new SubstitutionScheduleDay();
        d2.addSubstitution(s2);

        SubstitutionScheduleDayDiff dayDiff = SubstitutionScheduleDayDiff.compare(d1, d2);
        assertFalse(dayDiff.isNotEmpty());
    }
}
