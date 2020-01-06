/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo.blsschleswig.lsschleswig;

import me.vertretungsplan.additionalinfo.BaseIcalParser;

public class BlsSchleswigIcalParser extends BaseIcalParser {
    @Override
    protected String getIcalUrl() {
        return "https://schulintern.sh.schulcommsy.de/ical/1402918?hid=96bc9059d0db95ef6619a7a770fd2f15&calendar_id" +
                "=784";
    }
}
