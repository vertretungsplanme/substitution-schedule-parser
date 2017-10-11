/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * Parser for substitution schedules created by the <a href="http://davinci.stueber.de/">DaVinci</a> software and
 * hosted on their <a href="https://davinci.stueber.de/davinci-infoserver.php">InfoServer</a>.
 * <p>
 * This parser can be accessed using <code>"davinci-infoserver"</code> for
 * {@link SubstitutionScheduleData#setApi(String)}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>url</code> (String, required)</dt>
 * <dd>The URL of the home page of the DaVinci HTML export can be found. This can either be a schedule for a single
 * day or an overview page with a selection of classes or days (in both calendar and list views)</dd>
 * </dl>
 */
public class DaVinciInfoserverParser extends BaseParser {
    DaVinciInfoserverParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
    }

    @Override public SubstitutionSchedule getSubstitutionSchedule()
            throws IOException, JSONException, CredentialInvalidException {
        return null;
    }

    @Override public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        return null;
    }

    @Override public List<String> getAllTeachers() throws IOException, JSONException, CredentialInvalidException {
        return null;
    }
}
