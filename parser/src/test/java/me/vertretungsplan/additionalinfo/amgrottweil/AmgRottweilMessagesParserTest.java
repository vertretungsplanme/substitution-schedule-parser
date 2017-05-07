/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo.amgrottweil;

import me.vertretungsplan.objects.AdditionalInfo;
import me.vertretungsplan.parser.BaseDemoTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AmgRottweilMessagesParserTest extends BaseDemoTest {
    @Test
    public void test() {
        String html = readResource("/amgrottweil/messages.html");
        AdditionalInfo info = new AmgRottweilStudentMessagesParser().parse(html);
        assertEquals("Nachrichten für Schüler", info.getTitle());
        assertEquals("Kunst-Kurse KS1 und KS2 / Hageloch : Foto-Projekt findet erst am 17.3.statt !" +
                "(Unterricht am 10.3. fällt aus.) /Ha<br><br>USA-Austausch: Bewerbungsschluss Mi, 8.03.! " +
                "AJ<br><br>JtfO Fußball Mädchen WK III: Schülerinnen der Klassen 6+7, Treff Montag, 6.03., 2. gr. " +
                "Pause, R. 111! AJ<br><br>Mysterienspiel-AG: Mittwoch,8.März, 1.gr.Pause vor 005-Treffen zur Planung " +
                "-WICHTIG! / Ha<br><br>Kletter-AG fällt diese Woche aus! AJ", info.getText());
    }
}
