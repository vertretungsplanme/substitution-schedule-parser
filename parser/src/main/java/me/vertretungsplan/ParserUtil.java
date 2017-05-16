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
import me.vertretungsplan.parser.DebuggingDataHandler;
import me.vertretungsplan.parser.SubstitutionScheduleParser;
import org.json.JSONException;

import java.io.IOException;

/**
 * Utility class for parsing substitution schedules.
 */
public class ParserUtil {
    /**
     * @see #parseSubstitutionSchedule(SubstitutionScheduleData, Credential, CookieProvider, DebuggingDataHandler)
     */
    public static SubstitutionSchedule parseSubstitutionSchedule(SubstitutionScheduleData data)
            throws CredentialInvalidException, IOException, JSONException {
        return parseSubstitutionSchedule(data, null, null, null);
    }

    /**
     * @see #parseSubstitutionSchedule(SubstitutionScheduleData, Credential, CookieProvider, DebuggingDataHandler)
     */
    public static SubstitutionSchedule parseSubstitutionSchedule(SubstitutionScheduleData data, CookieProvider cp)
            throws CredentialInvalidException, IOException, JSONException {
        return parseSubstitutionSchedule(data, null, cp, null);
    }

    /**
     * @see #parseSubstitutionSchedule(SubstitutionScheduleData, Credential, CookieProvider, DebuggingDataHandler)
     */
    public static SubstitutionSchedule parseSubstitutionSchedule(SubstitutionScheduleData data, Credential credential)
            throws CredentialInvalidException, IOException, JSONException {
        return parseSubstitutionSchedule(data, credential, null, null);
    }

    /**
     * Parses a substitution schedule.
     *
     * @param data       A <code>SubstitutionScheduleData</code> instance containing information about the schedule you
     *                   want to parse.
     * @param credential A <code>Credential</code> subclass for authentication. If the schedule requires no
     *                   authentication, use <code>null</code>.
     * @param cp         An optional <code>CookieProvider</code> implementation. This can be used if you want to reuse
     *                   session cookies the next time you load the schedule. If you don't need it, pass
     *                   <code>null</code>.
     * @param handler    An optional <code>DebuggingDataHandler</code> implementation. If you don't need it, pass
     *                   <code>null</code>.
     * @return The parsed substitution schedule.
     * @throws JSONException              When there's an error with your JSON configuration
     * @throws CredentialInvalidException When the <code>Credential</code> you supplied was invalid
     * @throws IOException                When there was another error while loading or parsing the schedule
     */
    public static SubstitutionSchedule parseSubstitutionSchedule(SubstitutionScheduleData data, Credential
            credential, CookieProvider cp, DebuggingDataHandler handler)
            throws JSONException, CredentialInvalidException, IOException {
        SubstitutionScheduleParser parser = BaseParser.getInstance(data, cp);
        if (credential != null) parser.setCredential(credential);
        if (handler != null) ((BaseParser) parser).setDebuggingDataHandler(handler);
        SubstitutionSchedule schedule = parser.getSubstitutionSchedule();
        for (String a:data.getAdditionalInfos()) {
            BaseAdditionalInfoParser aParser = BaseAdditionalInfoParser.getInstance(a);
            schedule.addAdditionalInfo(aParser.getAdditionalInfo());
        }
        return schedule;
    }
}
