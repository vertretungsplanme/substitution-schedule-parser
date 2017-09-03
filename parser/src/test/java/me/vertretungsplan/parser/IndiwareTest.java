/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import org.junit.Test;

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
    }

    @Test
    public void testCancelPattern2() {
        Matcher matcher = IndiwareParser.cancelPattern.matcher("RE/k-st6 GAE fällt leider aus");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "RE/k-st6");
        assertEquals(matcher.group(2), "GAE");
    }

    @Test
    public void testCancelPatternShift() {
        Matcher matcher = IndiwareParser.cancelPattern.matcher("verlegt von St.7-8; EN Herr Plietzsch fällt aus");
        assertFalse(matcher.matches());
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
}
