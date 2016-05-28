/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
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
