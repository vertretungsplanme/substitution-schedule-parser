/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.sample;

import me.vertretungsplan.ParserUtil;
import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.authentication.NoAuthenticationData;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class Sample {
    public static void main(String[] args) throws JSONException, IOException, CredentialInvalidException {
        SubstitutionScheduleData data = new SubstitutionScheduleData();
        data.setType(SubstitutionSchedule.Type.STUDENT);
        data.setApi("untis-monitor");
        data.setAuthenticationData(new NoAuthenticationData());
        data.setData(new JSONObject("{\n" +
                "         \"classes\": [\n" +
                "           \"05a\",\"05b\",\"05c\",\"05d\",\"05e\",\"05f\",\"05g\",\n" +
                "           \"06a\",\"06b\",\"06c\",\"06d\",\"06e\",\"06f\",\"06g\",\n" +
                "           \"07a\",\"07b\",\"07c\",\"07d\",\"07e\",\"07f\",\"07g\",\n" +
                "           \"08a\",\"08b\",\"08c\",\"08d\",\"08e\",\"08f\",\"08g\",\n" +
                "           \"09a\",\"09b\",\"09c\",\"09d\",\"09e\",\"09f\",\"09g\",\n" +
                "           \"Ea\",\"Eb\",\"Ec\",\"Ed\",\"Ee\",\"Ef\",\n" +
                "           \"Q1a\",\"Q1b\",\"Q1c\",\"Q1d\",\"Q1e\",\"Q1f\",\"Q1g\",\"Q1h\",\"Q1i\",\"Q1j\",\n" +
                "           \"Q2a\",\"Q2b\",\"Q2c\",\"Q2d\",\"Q2e\",\"Q2f\",\"Q2g\",\"Q2h\",\"Q2i\",\"Q2j\" \n" +
                "        ],\n" +
                "         \"class_in_extra_line\": true,\n" +
                "         \"website\": \"http://vertretung.lornsenschule.de/schueler/subst_001.htm\",\n" +
                "         \"stand_links\": true,\n" +
                "         \"urls\": [\n" +
                "           {\n" +
                "             \"following\": false,\n" +
                "             \"url\": \"http://vertretung.lornsenschule.de/schueler/f1/subst_001.htm\" \n" +
                "          },\n" +
                "           {\n" +
                "             \"following\": false,\n" +
                "             \"url\": \"http://vertretung.lornsenschule.de/schueler/f2/subst_001.htm\" \n" +
                "          } \n" +
                "        ],\n" +
                "         \"encoding\": \"ISO-8859-1\",\n" +
                "         \"columns\": [\n" +
                "           \"lesson\",\"type\",\"subject\",\"previousSubject\",\"room\",\"desc\" \n" +
                "        ] \n" +
                "      }"));
        data.getAdditionalInfos().add("winter-sh");
        SubstitutionSchedule schedule = ParserUtil.parseSubstitutionSchedule(data);
        System.out.println(schedule);
    }
}
