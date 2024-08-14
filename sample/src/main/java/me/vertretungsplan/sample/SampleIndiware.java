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
import me.vertretungsplan.objects.authentication.PasswordAuthenticationData;
import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.objects.credential.PasswordCredential;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class SampleIndiware {
    public static void main(String[] args) throws JSONException, IOException, CredentialInvalidException {
        SubstitutionScheduleData data = new SubstitutionScheduleData();
        data.setType(SubstitutionSchedule.Type.STUDENT);
        data.setApi("untis-subst");
        data.setAuthenticationData(new PasswordAuthenticationData());
        data.setData(new JSONObject("{\n" +
                "            \"classes\": \"(0[5-9]|10)[A-D]|11|12|AEG|ÖPR\",\n" +
                "            \"columns\": [\"class\", \"lesson\", \"teacher\", \"subject\", \"room\", \"desc-type\"],\n" +
                "            \"encoding\": \"ISO-8859-1\",\n" +
                "            \"baseurl\": \"https://www.aeg-boeblingen.de/vertretungsplan/HTML/2_Ver_Kla_AEG.htm\",\n" +
                "            \"login\": {\n" +
                "                \"type\": \"post\",\n" +
                "                \"url\": \"https://aeg-boeblingen.de/index.php/component/jlexblock/?task=password\",\n" +
                "                \"preUrl\": \"https://aeg-boeblingen.de/index.php/plaene/vertretungsplan\",\n" +
                "                \"data\": {\n" +
                "                    \"_hiddeninputs\": \"#jlex-login-form\",\n" +
                "                    \"password\": \"_password\"\n" +
                "                },\n" +
                "                \"checkUrl\": \"https://aeg-boeblingen.de/index.php/plaene/vertretungsplan\",\n" +
                "                \"checkText\": \"Passworteingabe\"\n" +
                "            }\n" +
                "        }"));
        Credential cred = new PasswordCredential("einstein");
        SubstitutionSchedule schedule = ParserUtil.parseSubstitutionSchedule(data, cred);
        System.out.println(schedule);
    }
}
