/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan;

import me.vertretungsplan.additionalinfo.BaseAdditionalInfoParser;
import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.parser.BaseParser;
import me.vertretungsplan.parser.CookieProvider;
import me.vertretungsplan.parser.SubstitutionScheduleParser;
import org.json.JSONException;

import java.io.IOException;

public class ParserUtil {
    public static SubstitutionSchedule parseSubstitutionSchedule(SubstitutionScheduleData data)
            throws CredentialInvalidException, IOException, JSONException {
        return parseSubstitutionSchedule(data, null, null);
    }

    public static SubstitutionSchedule parseSubstitutionSchedule(SubstitutionScheduleData data, CookieProvider cp)
            throws CredentialInvalidException, IOException, JSONException {
        return parseSubstitutionSchedule(data, null, cp);
    }

    public static SubstitutionSchedule parseSubstitutionSchedule(SubstitutionScheduleData data, Credential credential)
            throws CredentialInvalidException, IOException, JSONException {
        return parseSubstitutionSchedule(data, credential, null);
    }

    public static SubstitutionSchedule parseSubstitutionSchedule(SubstitutionScheduleData data, Credential credential, CookieProvider cp)
            throws JSONException, CredentialInvalidException, IOException {
        SubstitutionScheduleParser parser = BaseParser.getInstance(data, cp);
        if (credential != null) parser.setCredential(credential);
        SubstitutionSchedule schedule = parser.getSubstitutionSchedule();
        for (String a:data.getAdditionalInfos()) {
            BaseAdditionalInfoParser aParser = BaseAdditionalInfoParser.getInstance(a);
            schedule.addAdditionalInfo(aParser.getAdditionalInfo());
        }
        return schedule;
    }
}
