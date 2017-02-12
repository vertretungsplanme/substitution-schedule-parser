package me.vertretungsplan.additionalinfo;

import me.vertretungsplan.objects.AdditionalInfo;
import me.vertretungsplan.parser.BaseDemoTest;
import org.junit.Test;

import static org.junit.Assert.*;

public class WinterShParserTest extends BaseDemoTest {
    @Test
    public void testNoInfo() throws Exception {
        String xml = readResource("/winter-sh/no-info.xml");
        AdditionalInfo info = WinterShParser.handleXML(xml);
        assertFalse(info.hasInformation());
        assertEquals("Witterungsbedingter Unterrichtsausfall (Stand: 23.11.2015 14:15)", info.getTitle());
    }

}