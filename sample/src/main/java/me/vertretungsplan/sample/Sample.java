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
import me.vertretungsplan.objects.authentication.PasswordAuthenticationData;
import me.vertretungsplan.objects.credential.PasswordCredential;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class Sample {
    public static void main(String[] args) throws JSONException, IOException, CredentialInvalidException {
        SubstitutionScheduleData data = new SubstitutionScheduleData();

        PasswordCredential passwordCredential = new PasswordCredential("FWS_LEIPZIG");

        data.setType(SubstitutionSchedule.Type.STUDENT);
        data.setApi("vpo");
        data.setAuthenticationData(new PasswordAuthenticationData());
        data.setData(new JSONObject("{\n" +
                "         \"url\": \"demo.vpo.de\"" +
                "      }"));
        SubstitutionSchedule schedule = ParserUtil.parseSubstitutionSchedule(data, passwordCredential);
        System.out.println(schedule);
    }
}
