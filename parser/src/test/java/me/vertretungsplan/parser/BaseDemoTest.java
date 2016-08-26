/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import java.io.*;

public class BaseDemoTest {
    /**
     * Reads content from an InputStream into a string
     *
     * @param is InputStream to read from
     * @return String content of the InputStream
     */
    private static String convertStreamToString(InputStream is) throws IOException {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        } catch (UnsupportedEncodingException e1) {
            reader = new BufferedReader(new InputStreamReader(is));
        }
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    protected String readResource(String filename) {
        InputStream is = getClass().getResourceAsStream(filename);
        if (is == null) return null;
        try {
            return convertStreamToString(is);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
