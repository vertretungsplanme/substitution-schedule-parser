/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

public class ColumnTypeDetector {
    private HashMap<String, String> columns;

    public ColumnTypeDetector() throws IOException, JSONException {
        columns = new HashMap<>();
        InputStream is = getClass().getClassLoader().getResourceAsStream("column_headers.json");
        String content = IOUtils.toString(is, "UTF-8");
        JSONObject json = new JSONObject(content);
        for (Iterator it = json.keys(); it.hasNext(); ) {
            String type = (String) it.next();
            final JSONArray titles = json.getJSONArray(type);
            for (int i = 0; i < titles.length(); i++) {
                columns.put(titles.getString(i), type);
            }
        }
    }

    public String getColumnType(String title) {
        return columns.get(title);
    }
}
