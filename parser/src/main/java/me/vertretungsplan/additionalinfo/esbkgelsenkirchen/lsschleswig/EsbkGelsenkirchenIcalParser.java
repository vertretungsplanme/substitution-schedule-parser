/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2018 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo.esbkgelsenkirchen.lsschleswig;

import me.vertretungsplan.additionalinfo.BaseIcalParser;

public class EsbkGelsenkirchenIcalParser extends BaseIcalParser {
    @Override
    protected String getIcalUrl() {
        return "https://www.eduard-spranger-bk.de//index.php?option=com_jevents&task=icals" +
                ".export&format=ical&catids=0&years=0&icf=1&k=38f31bbc7bff3bce9137ac0e5a56adc2";
    }

    @Override
    protected boolean shouldStripTimezoneInfo() {
        return true;
    }
}
