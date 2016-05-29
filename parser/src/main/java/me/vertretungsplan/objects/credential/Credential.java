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
     * @return Hash value for the credential data.
     * For two Credential objects a and b, the following relation should apply:
     * a.getHash().equals(b.getHash()) if and only if a.getLoginData().equals(b.getLoginData())
     */
    String getHash();

    String getSchoolId();

    String getScheduleId();

    boolean isValid();

    void setValid(boolean valid);

    DateTime getLastCheck();

    void setLastCheck(DateTime lastCheck);

    String getId();
}
