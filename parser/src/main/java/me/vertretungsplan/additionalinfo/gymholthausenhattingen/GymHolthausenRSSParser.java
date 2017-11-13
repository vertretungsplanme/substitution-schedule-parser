/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo.gymholthausenhattingen;

import me.vertretungsplan.additionalinfo.BaseRSSFeedParser;

public class GymHolthausenRSSParser extends BaseRSSFeedParser {
    @Override protected String getRSSUrl() {
        return "http://www.gyho.de/index.php/neuigkeiten?option=com_content&format=feed&type=rss&filter_order=a" +
                ".publish_up&filter_order_Dir=desc";
    }
}
