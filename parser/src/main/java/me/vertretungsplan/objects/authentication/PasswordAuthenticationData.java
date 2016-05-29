/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects.authentication;

import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.objects.credential.PasswordCredential;
import org.json.JSONException;
import org.json.JSONObject;

public class PasswordAuthenticationData implements AuthenticationData {
    @Override
    public Class<? extends Credential> getCredentialType() {
        return PasswordCredential.class;
    }

    @Override
    public JSONObject getData() throws JSONException {
        return null;
    }
}
