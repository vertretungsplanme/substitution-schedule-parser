/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.credential.Credential;
import org.joda.time.LocalDateTime;
import org.json.JSONException;

import java.io.IOException;

/**
 * Parser for a substitution schedule. Uses information from a {@link SubstitutionScheduleData} object to load and
 * parse the current substitution schedule and return the result as a {@link SubstitutionSchedule} object.
 */
public interface SubstitutionScheduleParser {
    /**
     * Downloads and parses the substitution schedule
     *
     * @return the parsed {@link SubstitutionSchedule}
     * @throws IOException                Connection or parsing error
     * @throws JSONException              Error with the JSON configuration
     * @throws CredentialInvalidException the supplied credential ({@link BaseParser#setCredential(Credential)} is
     *                                    not correct
     */
    SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException, CredentialInvalidException;

    /**
     * Returns the time when the substitution schedule was last changed. This can be used to use a previously cached
     * schedule if nothing was changed. Should return null if this information is not available or should not be
     * relied upon. The default implementation in {@link BaseParser} returns null.
     *
     * @return the time when the substitution schedule was last changed
     * @throws IOException                Connection or parsing error
     * @throws JSONException              Error with the JSON configuration
     * @throws CredentialInvalidException the supplied credential ({@link BaseParser#setCredential(Credential)} is
     *                                    not correct
     */
    LocalDateTime getLastChange() throws IOException, JSONException, CredentialInvalidException;

    void setCredential(Credential credential);

    Credential getCredential();

    /**
     * Some substitution schedule systems allow the user to only see his "own" substitution schedule depending on the
     * credentials he enters. If this is the case - i.e. the parser can return different schedules depending on the
     * credentials - this function should be overridden to return <code>true</code>.
     *
     * @return whether this parser returns personal schedules
     */
    boolean isPersonal();

    void setDebuggingDataHandler(DebuggingDataHandler handler);
}
