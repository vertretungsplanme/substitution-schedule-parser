/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.additionalinfo.karlsruheparzivalzentrum;

import me.vertretungsplan.additionalinfo.BaseRSSFeedParser;

public class KarlsruheParzivalzentrumRSSParser extends BaseRSSFeedParser {
	@Override protected String getRSSUrl() {
		return "https://www.parzival-zentrum.de/feed/";
	}
}
