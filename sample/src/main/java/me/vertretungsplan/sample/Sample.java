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
        data.setType(SubstitutionSchedule.Type.TEACHER);
        data.setApi("untis-monitor");
        data.setAuthenticationData(new NoAuthenticationData());
        data.setData(new JSONObject("{\n" +
                "         \"lastChangeLeft\": true,\n" +
                "         \"classes\": \"[5-9][a-e]|EF|Q1|Q2\",\n" +
                "         \"classInExtraLine\": true,\n" +
                "         \"classRegex\": \"([^\\\\s]*)\",\n" +
                "         \"urls\": [\n" +
                "           {\n" +
                "             \"following\": true,\n" +
                "             \"url\": \"https://owncloud.pkg-overath.de/index" +
                ".php/s/YbqgsrhBh2S75nZ/download?path=%2Ff1&files=subst_001.htm\"\n" +
                "          },\n" +
                "           {\n" +
                "             \"following\": true,\n" +
                "             \"url\": \"https://owncloud.pkg-overath.de/index" +
                ".php/s/YbqgsrhBh2S75nZ/download?path=%2Ff2&files=subst_001.htm\"\n" +
                "          }\n" +
                "        ],\n" +
                "         \"columns\": [\n" +
                "           \"lesson\",\n" +
                "           \"ignore\",\n" +
                "           \"subject\",\n" +
                "           \"previousTeacher\",\n" +
                "           \"subject\",\n" +
                "           \"teacher\",\n" +
                "           \"room\",\n" +
                "           \"type\",\n" +
                "           \"desc\"\n" +
                "        ],\n" +
                "         \"excludeTeachers\": true\n" +
                "      }"));
        SubstitutionSchedule schedule = ParserUtil.parseSubstitutionSchedule(data);
        System.out.println(schedule);
    }
}
