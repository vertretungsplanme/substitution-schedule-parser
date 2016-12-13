/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class SubstitutionMergingTest {
    private Substitution s1;

    @Before
    public void setUp() {
        s1 = new Substitution();
        s1.setClasses(new HashSet<>(Arrays.asList("1a", "2a")));
        s1.setSubject("Deu");
        s1.setPreviousSubject("Spa");
        s1.setRoom("42");
        s1.setTeachers(new HashSet<>(Arrays.asList("Mül", "Mei")));
        s1.setPreviousTeachers(new HashSet<>(Arrays.asList("Sm", "Sn")));
    }

    @Test
    public void differentClassTest() throws CloneNotSupportedException {
        Substitution s2 = s1.clone();
        s2.setClasses(new HashSet<>(Arrays.asList("3a", "4a")));
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();
        day.addAllSubstitutions(s1, s2);

        assertEquals(1, day.getSubstitutions().size());
        assertEquals(new HashSet<>(Arrays.asList("1a", "2a", "3a", "4a")),
                day.getSubstitutions().iterator().next().getClasses());
    }

    @Test
    public void differentTeacherTest() throws CloneNotSupportedException {
        Substitution s2 = s1.clone();
        s2.setTeachers(new HashSet<>(Arrays.asList("Sm", "Sn")));
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();
        day.addAllSubstitutions(s1, s2);

        assertEquals(1, day.getSubstitutions().size());
        assertEquals(new HashSet<>(Arrays.asList("Mül", "Mei", "Sm", "Sn")),
                day.getSubstitutions().iterator().next().getTeachers());
    }

    @Test
    public void differentPreviousTeacherTest() throws CloneNotSupportedException {
        Substitution s2 = s1.clone();
        s2.setPreviousTeachers(new HashSet<>(Arrays.asList("Mül", "Mei")));
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();
        day.addAllSubstitutions(s1, s2);

        assertEquals(1, day.getSubstitutions().size());
        assertEquals(new HashSet<>(Arrays.asList("Mül", "Mei", "Sm", "Sn")), day.getSubstitutions().iterator().next()
                .getPreviousTeachers());
    }
}
