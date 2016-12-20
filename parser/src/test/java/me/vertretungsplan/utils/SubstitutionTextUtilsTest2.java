/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.utils;

import me.vertretungsplan.objects.Substitution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SubstitutionTextUtilsTest2 {
    @Test
    public void testGetTeachers1() {
        Substitution s = new Substitution();
        s.setPreviousTeacher("A");
        s.setTeacher("B");
        assertEquals("B statt A", SubstitutionTextUtils.getTeachers(s));
    }

    @Test
    public void testGetTeachers2() {
        Substitution s = new Substitution();
        s.setTeacher("B");
        s.setPreviousTeacher(null);
        assertEquals("B", SubstitutionTextUtils.getTeachers(s));
        SubstitutionTextUtils.getText(s);
    }

    @Test
    public void testGetTeachers3() {
        Substitution s = new Substitution();
        s.setTeacher(null);
        s.setPreviousTeacher("A");
        assertEquals("A", SubstitutionTextUtils.getTeachers(s));
    }
}
