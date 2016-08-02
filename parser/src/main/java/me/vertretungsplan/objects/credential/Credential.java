/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects.credential;

import org.joda.time.DateTime;

public interface Credential {
    /**
     * Get a hash value for the credential data.
     * This can be used to differentiate between two credentials or to allow a user to authenticate to an application
     * which uses this parser. <b>It should not be used by {@link me.vertretungsplan.parser.SubstitutionScheduleParser}
     * implementations, especially not if they depend on a specific hashing algorithm.</b>
     *
     * For two Credential objects a and b, the following relation should apply:
     * a.getHash().equals(b.getHash()) if and only if a.getLoginData().equals(b.getLoginData())
     *
     * @return Hash value for the credential data.
     */
    String getHash();

    String getSchoolId();

    String getScheduleId();

    /**
     * @return If this credential is believed to be valid. Not necessary for any
     * {@link me.vertretungsplan.parser.SubstitutionScheduleParser} implementations.
     */
    boolean isValid();

    /**
     * Set if this credential is believed to be valid. Useful if your application saves credentials and checks them
     * periodically.
     *
     * @param valid if this credential is believed to be valid
     */
    void setValid(boolean valid);

    /**
     * @return The last time this credential was checked. Not necessary for any
     * {@link me.vertretungsplan.parser.SubstitutionScheduleParser} implementations.
     */
    DateTime getLastCheck();

    /**
     * Set the last time this credential was checked. Useful if your application saves credentials and checks them
     * periodically.
     *
     * @param lastCheck last time this credential was checked
     */
    void setLastCheck(DateTime lastCheck);

    String getId();
}
