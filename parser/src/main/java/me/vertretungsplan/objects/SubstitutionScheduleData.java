/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import me.vertretungsplan.objects.authentication.AuthenticationData;
import me.vertretungsplan.parser.CookieProvider;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Contains data about a school's substitution schedule. This has to be provided to a
 * {@link me.vertretungsplan.parser.SubstitutionScheduleParser} instance so that it can parse the schedule.
 */
public class SubstitutionScheduleData {
    private SubstitutionSchedule.Type type;
    private String api;
    private Set<String> additionalInfos;
    private JSONObject data;
    private AuthenticationData authenticationData;

    public SubstitutionScheduleData() {
        additionalInfos = new HashSet<>();
    }

    /**
     * Get the {@link SubstitutionSchedule.Type} of the substitution schedule this data represents
     * @return the type of this schedule
     */
    public SubstitutionSchedule.Type getType() {
        return type;
    }

    /**
     * Set the {@link SubstitutionSchedule.Type} of the substitution schedule this data represents
     * @param type the type of this schedule
     */
    @SuppressWarnings("SameParameterValue")
    public void setType(SubstitutionSchedule.Type type) {
        this.type = type;
    }

    /**
     * Get the type of parser to use for this schedule, as a string representation. This is used by
     * {@link me.vertretungsplan.parser.BaseParser#getInstance(SubstitutionScheduleData, CookieProvider)} to create a
     * suitable parser instance.
     *
     * @return the type of parser to use
     */
    public String getApi() {
        return api;
    }

    /**
     * Set the type of parser to use for this schedule, as a string representation. This information is used by
     * {@link me.vertretungsplan.parser.BaseParser#getInstance(SubstitutionScheduleData, CookieProvider)} to create a
     * suitable parser instance. Currently supported values are:
     * <ul>
     *     <li>{@code "untis-monitor"}</li>
     *     <li>{@code "untis-info"}</li>
     *     <li>{@code "untis-info-headless"}</li>
     *     <li>{@code "untis-subst"}</li>
     *     <li>{@code "dsbmobile"}</li>
     *     <li>{@code "svplan"}</li>
     *     <li>{@code "davinci"}</li>
     *     <li>{@code "turbovertretung"}</li>
     *     <li>{@code "csv"}</li>
     * </ul>
     *
     * @param api the type of parser to use
     */
    public void setApi(String api) {
        this.api = api;
    }

    /**
     * Get the types of {@link Addition}
     * @return the set of additional infos
     */
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
