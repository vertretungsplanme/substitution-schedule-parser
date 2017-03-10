/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Utility class used by the {@link SubstitutionScheduleParser} implementations to set suitable colors depending on
 * the type of substitution.
 * <ul>
 * <li>Red: Lesson is cancelled or the students are supposed to work without a teacher ("EVA" =
 * Eigenverantwortliches Arbeiten)</li>
 * <li>Blue: Lesson will be given by a different teacher and/or with a different subject</li>
 * <li>Yellow: Lesson will take place at another time or swapped with another lesson</li>
 * <li>Green: Lesson will take place in another room</li>
 * <li>Brown: A special event will replace the lesson</li>
 * <li>Orange: Exam will be taken in this lesson</li>
 * <li>Gray: Break supervision (teacher)</li>
 * <li>Purple: Unknown</li>
 * </ul>
 * Colors from the <a href="https://material.google.com/style/color.html#color-color-palette">Material design</a>
 * color palette are used.
 */
public class ColorProvider {

    private static final HashMap<String, String> colorNames = new HashMap<>();
    // *** Default color values ***
    private static final String[] RED_VALUES = {"Entfall", "EVA", "Entf.", "Entf", "Fällt aus!", "Fällt aus",
            "entfällt", "Freistunde", "Klasse frei", "Selbstlernen", "HA", "selb.Arb.", "Aufgaben", "selbst.", "Frei",
            "Ausfall", "Stillarbeit", "Absenz", "-> Entfall", "Freisetzung"};
    private static final String[] BLUE_VALUES = {"Vertretung", "Sondereins.", "Statt-Vertretung",
            "Betreuung", "V", "VTR", "Vertr."};
    private static final String[] YELLOW_VALUES = {"Tausch", "Verlegung", "Zusammenlegung",
            "Unterricht geändert", "Unterrichtstausch", "geändert", "statt", "Stundentausch"};
    private static final String[] GREEN_VALUES =
            {"Raum", "KLA", "Raum-Vtr.", "Raumtausch", "Raumverlegung", "Raumänderung", "R. Änd.", "Raum beachten",
                    "Raum-Vertr."};
    private static final String[] BROWN_VALUES = {"Veranst.", "Veranstaltung", "Frei/Veranstaltung", "Hochschultag"};
    private static final String[] ORANGE_VALUES = {"Klausur"};
    private static final String[] GRAY_VALUES = {"Pausenaufsicht"};
    private static final HashMap<String, String> defaultColorMap = new HashMap<>();

    // Material Design colors
    static {
        // These colors are used for the substitutions recognized by default and should also be used for other
        // substitutions if possible. For description of their meanings, see below
        colorNames.put("red", "#F44336");
        colorNames.put("blue", "#2196F3");
        colorNames.put("yellow", "#FFA000"); // this is darker for readable white text, should be #FFEB3B
        colorNames.put("green", "#4CAF50");
        colorNames.put("brown", "#795548");
        colorNames.put("orange", "#FF9800");
        colorNames.put("gray", "#9E9E9E");
        // This color is used for substitutions that could not be recognized and should not be used otherwise
        // (if not explicitly requested by the school)
        colorNames.put("purple", "#9C27B0");
        // These are additional colors from the Material Design spec that can be used for substitutions that do not fit
        // into the scheme below
        colorNames.put("pink", "#E91E63");
        colorNames.put("deep_purple", "#673AB7");
        colorNames.put("indigo", "#3F51B5");
        colorNames.put("light_blue", "#03A9F4");
        colorNames.put("cyan", "#00BCD4");
        colorNames.put("teal", "#009688");
        colorNames.put("light_green", "#8BC34A");
        colorNames.put("lime", "#CDDC39");
        colorNames.put("amber", "#FFC107");
        colorNames.put("deep_orange", "#FF5722");
        colorNames.put("blue_gray", "#607D8B");
        colorNames.put("black", "#000000");
        colorNames.put("white", "#FFFFFF");
    }

    static {
        for (String string : RED_VALUES) defaultColorMap.put(string.toLowerCase(), colorNames.get("red"));
        for (String string : BLUE_VALUES) defaultColorMap.put(string.toLowerCase(), colorNames.get("blue"));
        for (String string : YELLOW_VALUES) defaultColorMap.put(string.toLowerCase(), colorNames.get("yellow"));
        for (String string : GREEN_VALUES) defaultColorMap.put(string.toLowerCase(), colorNames.get("green"));
        for (String string : BROWN_VALUES) defaultColorMap.put(string.toLowerCase(), colorNames.get("brown"));
        for (String string : ORANGE_VALUES) defaultColorMap.put(string.toLowerCase(), colorNames.get("orange"));
        for (String string : GRAY_VALUES) defaultColorMap.put(string.toLowerCase(), colorNames.get("gray"));
    }

    private HashMap<String, String> colorMap = new HashMap<>();

    public ColorProvider(SubstitutionScheduleData data) {
        try {
            if (data.getData().has("colors")) {
                JSONObject colors = data.getData().getJSONObject("colors");
                Iterator<?> keys = colors.keys();
                while (keys.hasNext()) {
                    String color = (String) keys.next();
                    JSONArray values = colors.getJSONArray(color);
                    if (colorNames.containsKey(color)) {
                        color = colorNames.get(color);
                    }
                    for (int i = 0; i < values.length(); i++) {
                        colorMap.put(values.getString(i).toLowerCase(), color);
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get an appropriate color for a substitution based on its type
     *
     * @param type the type of the substitution ({@link Substitution#getType()})
     * @return an appropriate color in hexadecimal format
     */
    public String getColor(String type) {
        if (type == null) {
            return null;
        } else if (colorMap.containsKey(type.toLowerCase())) {
            return colorMap.get(type.toLowerCase());
        } else if (defaultColorMap.containsKey(type.toLowerCase())) {
            return defaultColorMap.get(type.toLowerCase());
        } else {
            return colorNames.get("purple");
        }
    }
}
