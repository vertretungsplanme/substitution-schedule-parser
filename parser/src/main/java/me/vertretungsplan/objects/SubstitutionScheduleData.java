/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import me.vertretungsplan.objects.authentication.AuthenticationData;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class SubstitutionScheduleData {
    private SubstitutionSchedule.Type type;
    private String api;
    private Set<String> additionalInfos;
    private JSONObject data;
    private AuthenticationData authenticationData;

    public SubstitutionScheduleData() {
        additionalInfos = new HashSet<>();
    }

    public SubstitutionSchedule.Type getType() {
        return type;
    }

    @SuppressWarnings("SameParameterValue")
    public void setType(SubstitutionSchedule.Type type) {
        this.type = type;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public Set<String> getAdditionalInfos() {
        return additionalInfos;
    }

    public void setAdditionalInfos(Set<String> additionalInfos) {
        this.additionalInfos = additionalInfos;
    }

    public JSONObject getData() {
        return data;
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    public AuthenticationData getAuthenticationData() {
        return authenticationData;
    }

    public void setAuthenticationData(AuthenticationData authenticationData) {
        this.authenticationData = authenticationData;
    }
}
