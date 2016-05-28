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
