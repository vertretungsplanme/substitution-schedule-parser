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
import me.vertretungsplan.objects.authentication.UserPasswordAuthenticationData;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class Sample {
    public static void main(String[] args) throws JSONException, IOException, CredentialInvalidException {
        SubstitutionScheduleData data = new SubstitutionScheduleData();
        data.setType(SubstitutionSchedule.Type.STUDENT);
        data.setApi("indiware-mobile");
        data.setAuthenticationData(new UserPasswordAuthenticationData());
        data.setData(new JSONObject("{\n" +
                "         \"baseurl\": \"http://freies-gymnasium-naunhof.de/plan/klassen\",\n" +
                "         \"login\": {\n" +
                "           \"type\": \"basic\" \n" +
                "        } \n" +
                "      }"));
        data.getAdditionalInfos().add("winter-sh");
        SubstitutionSchedule schedule = ParserUtil.parseSubstitutionSchedule(data, new UserPasswordCredential(
                "schueler", "PwVP2018"));
        System.out.println(schedule);
    }
}
