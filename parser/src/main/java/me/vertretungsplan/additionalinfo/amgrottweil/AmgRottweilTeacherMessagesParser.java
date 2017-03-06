/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo.amgrottweil;

import org.jetbrains.annotations.NotNull;

public class AmgRottweilTeacherMessagesParser extends AmgRottweilMessagesParser {
    @NotNull @Override protected String getUrl() {
        return "https://www.amgrw.de/AMGaktuell/NachrichtenLehrer.php";
    }

    @NotNull @Override protected String getTitle() {
        return "Nachrichten für Lehrer";
    }
}
