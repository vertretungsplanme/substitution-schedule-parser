/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects.authentication;

import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.objects.credential.SchoolNumberPasswordCredential;
import org.json.JSONException;
import org.json.JSONObject;

public class SchoolNumberPasswordAuthenticationData implements AuthenticationData {
    private String schoolNumber;

    @Override
    public Class<? extends Credential> getCredentialType() {
        return SchoolNumberPasswordCredential.class;
    }

    @Override
    public JSONObject getData() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("schoolNumber", schoolNumber);
        return json;
    }

    public String getSchoolNumber() {
        return schoolNumber;
    }

    public void setSchoolNumber(String schoolNumber) {
        this.schoolNumber = schoolNumber;
    }
}
