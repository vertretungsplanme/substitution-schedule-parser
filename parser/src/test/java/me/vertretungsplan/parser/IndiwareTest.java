/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.objects.Substitution;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

public class IndiwareTest {
    @Test
    public void testSubstitutionPattern() {
        Matcher matcher = IndiwareParser.substitutionPattern.matcher("für ETH Meh");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "ETH");
        assertEquals(matcher.group(2), "Meh");
        assertEquals(matcher.group(3), "");
    }

    @Test
    public void testSubstitutionPatternWithDesc() {
        Matcher matcher = IndiwareParser.substitutionPattern.matcher("für ETH Frau Röhling , Aufgaben Frau Röhling");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "ETH");
        assertEquals(matcher.group(2), "Frau Röhling");
        assertEquals(matcher.group(3), "Aufgaben Frau Röhling");
    }

    @Test
    public void testSubstitutionPatternWithDescWithoutComma() {
        Matcher matcher = IndiwareParser.substitutionPattern.matcher("für DE Frau Friedel geändert");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "DE");
        assertEquals(matcher.group(2), "Frau Friedel");
        assertEquals(matcher.group(3), "geändert");
    }

    @Test
    public void testCancelPattern1() {
        Matcher matcher = IndiwareParser.cancelPattern.matcher("FR Wen fällt aus");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "FR");
        assertEquals(matcher.group(2), "Wen");

        matcher = IndiwareParser.cancelPattern.matcher("DE fällt aus");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "DE");
    }

    @Test
    public void testCancelPattern2() {
        Matcher matcher = IndiwareParser.cancelPattern.matcher("RE/k-st6 GAE fällt leider aus");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "RE/k-st6");
        assertEquals(matcher.group(2), "GAE");
    }

    @Test
    public void testCancelPatternShift1() {
        Matcher matcher = IndiwareParser.cancelPattern.matcher("verlegt von St.7-8; EN Herr Plietzsch fällt aus");
        assertFalse(matcher.matches());
    }

    @Test
    public void testCancelPatternShift2() {
        Matcher matcher = IndiwareParser.cancelPattern.matcher("statt Mo (10.09.) St.7; GEO Herr Maschke  fällt aus");
        assertFalse(matcher.matches());
    }

    @Test
    public void testClassTeacherLesson() {
        final String input = "Klassenleiterstunden; Mat HEL fällt aus";
        Matcher matcher = IndiwareParser.cancelPattern.matcher(input);
        assertFalse(matcher.matches());

        matcher = IndiwareParser.classTeacherLesson.matcher(input);
        assertTrue(matcher.matches());
        assertEquals("Klassenleiterstunden", matcher.group(1));
        assertEquals("Mat", matcher.group(2));
        assertEquals("HEL", matcher.group(3));
    }

    @Test
    public void testDelayPattern() {
        Matcher matcher = IndiwareParser.delayPattern.matcher("INF KNE verlegt nach St.8");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "INF");
        assertEquals(matcher.group(2), "KNE");
        assertEquals(matcher.group(3), "verlegt nach St.8");
    }

    @Test
    public void testDelayPatternShift() {
        // complicated case, we do not want delayPattern to match here
        Matcher matcher = IndiwareParser.delayPattern.matcher("statt Fr (06.12.) St.5; EN BÄR verlegt nach Fr (06.12.) St.5, Klassenarbeit");
        assertFalse(matcher.matches());
    }

    @Test
    public void testSelfPattern() {
        Matcher matcher = IndiwareParser.selfPattern.matcher("selbst.");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "");
    }

    @Test
    public void testSelfPatternWithDesc() {
        Matcher matcher = IndiwareParser.selfPattern.matcher("selbst., Aufgaben erteilt");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "Aufgaben erteilt");
    }

    @Test
    public void testCoursePattern() {
        Matcher matcher = IndiwareParser.coursePattern.matcher("07/2/ RE/k-st6");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "07/2");
        assertEquals(matcher.group(2), "RE/k-st6");
    }

    @Test
    public void testBracesPattern() {
        Matcher matcher = IndiwareParser.bracesPattern.matcher("(Fdr)");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "Fdr");
    }

    @Test
    public void testTakeOverPattern() {
        Matcher matcher = IndiwareParser.takeOverPattern.matcher("Herr Samuel übernimmt mit");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "Herr Samuel");
    }

    @Test
    public void testNewPattern() {
        Substitution substitution = new Substitution();
        String input = "neu, Prüfung; Sm Herr Weise fällt aus";
        IndiwareParser.handleDescription(substitution, input);
        assertEquals("Prüfung", substitution.getType());
        assertEquals("Sm Herr Weise fällt aus", substitution.getDesc());
    }

    @Test
    public void testSplitTeacher() {
        assertEquals(new HashSet<>(Arrays.asList("Müller", "Meier")),
                IndiwareParser.splitTeachers("Müller, Meier", true));
        assertEquals(new HashSet<>(Collections.singletonList("H. Müller, Michael")),
                IndiwareParser.splitTeachers("H. Müller, Michael", false));
    }

    @Test
    public void testClassAndCourse() throws JSONException {
        IndiwareParser.ClassAndCourse cac = new IndiwareParser.ClassAndCourse("5a,5b", null);
        assertEquals(new HashSet<>(Arrays.asList("5a", "5b")), cac.classes);
        assertNull(cac.course);

        cac = new IndiwareParser.ClassAndCourse("5a,5b/ Deu1", null);
        assertEquals(new HashSet<>(Arrays.asList("5a", "5b")), cac.classes);
        assertEquals("Deu1", cac.course);

        JSONObject data = new JSONObject();
        JSONObject classRanges = new JSONObject();
        classRanges.put(BaseParser.CLASS_RANGES_GRADE_REGEX, "\\d+");
        classRanges.put(BaseParser.CLASS_RANGES_CLASS_REGEX, "[a-z]");
        classRanges.put(BaseParser.CLASS_RANGES_RANGE_FORMAT, "gc-c");
        classRanges.put(BaseParser.CLASS_RANGES_SINGLE_FORMAT, "gc");
        data.put(BaseParser.PARAM_CLASS_RANGES, classRanges);
        cac = new IndiwareParser.ClassAndCourse("5a-d/ Deu1", data);
        assertEquals(new HashSet<>(Arrays.asList("5a", "5b", "5c", "5d")), cac.classes);
        assertEquals("Deu1", cac.course);

        classRanges.put(BaseParser.CLASS_RANGES_CLASS_REGEX, "\\d");
        classRanges.put(BaseParser.CLASS_RANGES_RANGE_FORMAT, "g/c-g/c");
        classRanges.put(BaseParser.CLASS_RANGES_SINGLE_FORMAT, "g/c");
        cac = new IndiwareParser.ClassAndCourse("5/1-5/3,10/1/ Deu1", data);
        assertEquals(new HashSet<>(Arrays.asList("5/1", "5/2", "5/3", "10/1")), cac.classes);
        assertEquals("Deu1", cac.course);
    }
}
