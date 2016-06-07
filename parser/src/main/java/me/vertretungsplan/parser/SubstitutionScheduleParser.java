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
import org.json.JSONException;

import java.io.IOException;

/**
 * Parser for a substitution schedule. Uses information from a {@link SubstitutionScheduleData} object to load and
 * parse the current substitution schedule and return the result as a {@link SubstitutionSchedule} object.
 */
public interface SubstitutionScheduleParser {
    SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException, CredentialInvalidException;
    void setCredential(Credential credential);
    Credential getCredential();
}
