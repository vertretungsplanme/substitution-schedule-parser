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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        Matcher matcher = IndiwareParser.substitutionPattern.matcher("für ETH Röh , Aufgaben Frau Röhling");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "ETH");
        assertEquals(matcher.group(2), "Röh");
        assertEquals(matcher.group(3), "Aufgaben Frau Röhling");
    }

    @Test
    public void testCancelPattern() {
        Matcher matcher = IndiwareParser.cancelPattern.matcher("FR Wen fällt aus");
        assertTrue(matcher.matches());
        assertEquals(matcher.group(1), "FR");
        assertEquals(matcher.group(2), "Wen");
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
}
