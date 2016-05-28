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

package me.vertretungsplan.parser;

import org.joda.time.DateTimeUtils;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ParserUtilsTest {
    @Test
    public void testNewYear() {
        DateTimeUtils.setCurrentMillisFixed(1450911600000L); // 24.12.2015
        assertTrue(ParserUtils.parseDate("1.1. Freitag").getYear() == 2016);
        assertTrue(ParserUtils.parseDate("31.12. Donnerstag").getYear() == 2015);

        DateTimeUtils.setCurrentMillisFixed(1452034800000L); // 06.01.2016
        assertTrue(ParserUtils.parseDate("1.1. Freitag").getYear() == 2016);
        assertTrue(ParserUtils.parseDate("31.12. Donnerstag").getYear() == 2015);
    }
}
