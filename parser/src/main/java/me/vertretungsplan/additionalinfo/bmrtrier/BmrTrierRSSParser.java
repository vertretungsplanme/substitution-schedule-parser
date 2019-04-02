/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2019 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo.bmrtrier;

import me.vertretungsplan.additionalinfo.BaseRSSFeedParser;

public class BmrTrierRSSParser extends BaseRSSFeedParser {
    @Override protected String getRSSUrl() {
        return "https://bmrtrier.de/index.php/galerie-und-schulleben/aktuelles-aus-dem-schulleben?option=com_content" +
                "&format=feed&type=rss&filter_order=a.publish_up&filter_order_Dir=desc";
    }
}
