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

import java.util.ArrayList;
import java.util.List;

/**
 * Contains data about a school's substitution schedule. This has to be provided to a
 * {@link me.vertretungsplan.parser.SubstitutionScheduleParser} instance so that it can parse the schedule.
 */
public class SubstitutionScheduleData {
    private SubstitutionSchedule.Type type;
    private String api;
    private List<String> additionalInfos;
    private JSONObject data;
    private AuthenticationData authenticationData;

    public SubstitutionScheduleData() {
        additionalInfos = new ArrayList<>();
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
     * Get the types of {@link AdditionalInfo} this schedule should contain. Used by
     * {@link me.vertretungsplan.additionalinfo.BaseAdditionalInfoParser#getInstance(String)} to create a suitable
     * parser instance.
     *
     * @return the list of additional info types
     */
    public List<String> getAdditionalInfos() {
        return additionalInfos;
    }

    /**
     * Set the types of {@link AdditionalInfo} this schedule should contain. Used by
     * {@link me.vertretungsplan.additionalinfo.BaseAdditionalInfoParser#getInstance(String)} to create a suitable
     * parser instance. Currently supported values are:
     * <ul>
     *     <li>{@code "winter-sh"}</li>
     * </ul>
     *
     * @param additionalInfos the additional info types to set
     */
    public void setAdditionalInfos(List<String> additionalInfos) {
        this.additionalInfos = additionalInfos;
    }

    /**
     * Get additional data about this substitution schedule in form of a JSON object. What data is needed here
     * depends on the parser type, see their own documentation in the <code>me.vertretungsplan.parser</code> package.
     *
     * @return additional data about this substitution schedule
     */
    public JSONObject getData() {
        return data;
    }

    /**
     * Set additional data about this substitution schedule in form of a JSON object. What data is needed here
     * depends on the parser type, see their own documentation in the <code>me.vertretungsplan.parser</code> package.
     *
     * @param data additional data about this substitution schedule
     */
    public void setData(JSONObject data) {
        this.data = data;
    }

    /**
     * Get information about what kind of {@link me.vertretungsplan.objects.credential.Credential} is needed to parse
     * this schedule and if there are additional parameters for authentication (such as a pre-set school number with
     * only the password needing to be filled in). If no credential is needed, this should return a
     * {@link me.vertretungsplan.objects.authentication.NoAuthenticationData} instance.
     *
     * @return the authentication data
     */
    public AuthenticationData getAuthenticationData() {
        return authenticationData;
    }

    /**
     * Set information about what kind of {@link me.vertretungsplan.objects.credential.Credential} is needed to parse
     * this schedule and if there are additional parameters for authentication (such as a pre-set school number with
     * only the password needing to be filled in). If no credential is needed, set this to a
     * {@link me.vertretungsplan.objects.authentication.NoAuthenticationData} instance.
     *
     * @param authenticationData the authentication data to set
     */
    public void setAuthenticationData(AuthenticationData authenticationData) {
        this.authenticationData = authenticationData;
    }
}
