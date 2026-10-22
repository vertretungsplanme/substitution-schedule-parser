/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.utils;

import java.time.ZoneId;

/**
 * Time zone used for all date and time calculations of this library.
 *
 * <p>The substitution schedules parsed here always belong to German schools and the library
 * already formats everything with {@link java.util.Locale#GERMAN}. Interpreting instants (WebDAV
 * modification times, JWT timestamps, HTTP <code>Last-Modified</code> headers) and the notion of
 * "today" in one fixed zone keeps the results independent of the time zone the JVM happens to run
 * in, so a schedule parsed in a container with TZ=UTC yields the same dates as on a machine
 * running in Berlin.</p>
 */
public final class TimeZones {
    /**
     * The zone all parsers work in and the one that defines "today".
     */
    public static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private TimeZones() {
    }
}
