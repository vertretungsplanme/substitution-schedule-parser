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
