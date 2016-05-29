/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects.authentication;

import me.vertretungsplan.objects.credential.Credential;
import org.json.JSONException;
import org.json.JSONObject;

public interface AuthenticationData {
    Class<? extends Credential> getCredentialType();
    JSONObject getData() throws JSONException;
}
