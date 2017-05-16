/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contains common code used by {@link DSBLightParser}, {@link DSBMobileParser}, {@link UntisInfoParser},
 * {@link UntisInfoHeadlessParser}, {@link UntisMonitorParser} and {@link UntisSubstitutionParser}.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} in addition to the
 * parameters specified in the documentation of the parser itself.
 *
 * <dl>
 * <dt><code>columns</code> (Array of Strings, required)</dt>
 * <dd>The order of columns used in the substitutions table. Entries can be: <code>"lesson", "subject",
 * "previousSubject", "type", "type-entfall", "room", "previousRoom", "teacher", "previousTeacher", desc",
 * "desc-type", "substitutionFrom", "teacherTo", "class", "ignore"</code> (<code>"class"</code> only works when
 * <code>classInExtraLine</code> is <code>false</code>.
 * </dd>
 *
 * <dt><code>lastChangeLeft</code> (Boolean, optional)</dt>
 * <dd>Whether the date of last change is in the top left corner instead of in the <code>.mon_head</code> table.
 * Default: <code>false</code></dd>
 *
 * <dt><code>classInExtraLine</code> (Boolean, optional)</dt>
 * <dd>Whether the changes in the table are grouped using headers containing the class name(s). Default:
 * <code>false</code></dd>
 *
 * <dt><code>classesSeparated</code> (Boolean, optional)</dt>
 * <dd>Whether the class names are separated by commas. If this is set to <code>false</code>, combinations like "5abcde"
 * are attempted to be accounted for using an ugly algorithm based on RegExes generated from {@link #getAllClasses()}.
 * Default: <code>true</code></dd>
 *
 * <dt><code>excludeClasses</code> (Array of Strings, optional)</dt>
 * <dd>Substitutions for classes from this Array are ignored when reading the schedule. By default, only the class
 * <code>"-----"</code> is ignored.</dd>
 *
 * <dt><code>classRegex</code> (String, optional)</dt>
 * <dd>RegEx to modify the classes set on the schedule (in {@link #getSubstitutionSchedule()}, not
 * {@link #getAllClasses()}. The RegEx is matched against the class using {@link Matcher#find()}. If the RegEx
 * contains a group, the content of the first group {@link Matcher#group(int)} is used as the resulting class.
 * Otherwise, {@link Matcher#group()} is used. If the RegEx cannot be matched ({@link Matcher#find()} returns
 * <code>false</code>), the class is set to an empty string.
 * </dd>
 *
 * <dt><code>typeAutoDetection</code> (Boolean, optional)</dt>
 * <dd>If there is no type column and the detection using desc-type did not work, this sets whether the type may be
 * automatically set to "Entfall" (cancellation) depending on the values of other columns.
 * Default: <code>true</code></dd>
 *
 * </dl>
 */
public abstract class UntisCommonParser extends BaseParser {

    private static final String[] EXCLUDED_CLASS_NAMES = new String[]{"-----"};
    private static final String PARAM_LAST_CHANGE_LEFT = "lastChangeLeft";
    private static final String PARAM_LAST_CHANGE_SELECTOR = "lastChangeSelector"; // only used in UntisMonitorParser
    private static final String PARAM_CLASS_IN_EXTRA_LINE = "classInExtraLine";
    private static final String PARAM_COLUMNS = "columns";
    private static final String PARAM_CLASSES_SEPARATED = "classesSeparated";
    private static final String PARAM_EXCLUDE_CLASSES = "excludeClasses";
    private static final String PARAM_TYPE_AUTO_DETECTION = "typeAutoDetection";
    private static final String PARAM_MERGE_WITH_DIFFERENT_TYPE = "mergeWithDifferentType";

    private static ColumnTypeDetector detector;

    private static ColumnTypeDetector getDetector() throws IOException, JSONException {
        if (detector == null) {
            detector = new ColumnTypeDetector();
        }
        return detector;
    }

    UntisCommonParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
    }

    static String findLastChange(Element doc, SubstitutionScheduleData scheduleData) {
        String lastChange = null;

        boolean lastChangeLeft = false;
        if (scheduleData != null) {
            if (scheduleData.getData().has("stand_links")) {
                // backwards compatibility
                lastChangeLeft = scheduleData.getData().optBoolean("stand_links", false);
            } else {
                lastChangeLeft = scheduleData.getData().optBoolean(PARAM_LAST_CHANGE_LEFT, false);
            }
        }

        if (doc.select("table.mon_head").size() > 0) {
            Element monHead = doc.select("table.mon_head").first();
            lastChange = findLastChangeFromMonHeadTable(monHead);
        } else if (lastChangeLeft) {
            final String bodyHtml = doc.select("body").size() > 0 ? doc.select("body").html() : doc.html();
            lastChange = bodyHtml.substring(0, bodyHtml.indexOf("<p>") - 1);
        } else {
            List<Node> childNodes;
            if (doc instanceof Document) {
                childNodes = ((Document) doc).body().childNodes();
            } else {
                childNodes = doc.childNodes();
            }
            for (Node node : childNodes) {
                if (node instanceof Comment) {
                    Comment comment = (Comment) node;
                    if (comment.getData().contains("<table class=\"mon_head\">")) {
                        Document commentedDoc = Jsoup.parse(comment.getData());
                        Element monHead = commentedDoc.select("table.mon_head").first();
                        lastChange = findLastChangeFromMonHeadTable(monHead);
                        break;
                    }
                }
            }
        }
        return lastChange;
    }

    private static String findLastChangeFromMonHeadTable(Element monHead) {
        if (monHead.select("td[align=right]").size() == 0) return null;

        String lastChange = null;
        Pattern pattern = Pattern.compile("\\d\\d\\.\\d\\d\\.\\d\\d\\d\\d \\d\\d:\\d\\d");
        Matcher matcher = pattern.matcher(monHead.select("td[align=right]").first().text());
        if (matcher.find()) {
            lastChange = matcher.group();
        } else if (monHead.text().contains("Stand: ")) {
            lastChange = monHead.text().substring(monHead.text().indexOf("Stand:") + "Stand:".length()).trim();
        }
        return lastChange;
    }

    private static boolean equalsOrNull(String a, String b) {
        return a == null || b == null || a.equals(b);
    }

    /**
     * Parses an Untis substitution schedule table
     *
     * @param table the <code>table</code> Element from the HTML document
     * @param data  {@link SubstitutionScheduleData#getData()}
     * @param day   the {@link SubstitutionScheduleDay} where the substitutions will be stored
     */
    void parseSubstitutionScheduleTable(Element table, JSONObject data,
                                        SubstitutionScheduleDay day)
            throws JSONException, CredentialInvalidException, IOException {
        parseSubstitutionScheduleTable(table, data, day, null);
    }

    /**
     * Parses an Untis substitution schedule table
     *
     * @param table        the <code>table</code> Element from the HTML document
     * @param data         {@link SubstitutionScheduleData#getData()}
     * @param day          the {@link SubstitutionScheduleDay} where the substitutions will be stored
     * @param defaultClass the class that should be set if there is no class column in the table
     */
    private void parseSubstitutionScheduleTable(Element table, JSONObject data,
                                                SubstitutionScheduleDay day, String defaultClass)
            throws JSONException, CredentialInvalidException, IOException {
        Elements headerRows = table.select("tr:has(th)");

        List<String> columnTitles = new ArrayList<>();
        if (headerRows.size() > 0) {
            Elements headers = headerRows.get(0).select("th");
            for (int i = 0; i < headers.size(); i++) {
                StringBuilder builder = new StringBuilder();
                boolean first = true;
                for (Element headerRow : headerRows) {
                    final String text = headerRow.select("th").get(i).text().replace("\u00a0", " ").trim();
                    if (first) {
                        if (!text.equals("")) first = false;
                    } else {
                        builder.append(" ");
                    }
                    builder.append(text);
                }
                columnTitles.add(builder.toString());
            }
        }

        debuggingDataHandler.columnTitles(columnTitles);

        final JSONArray columnsJson = data.optJSONArray(PARAM_COLUMNS);
        List<String> columns = new ArrayList<>();
        if (columnTitles.size() == 0) {
            for (int i = 0; i < columnsJson.length(); i++) columns.add(columnsJson.getString(i));
        } else {
            for (String title : columnTitles) {
                String type = getDetector().getColumnType(title);
                if (type != null) {
                    columns.add(type);
                } else {
                    if (columnsJson != null && columnsJson.length() == columnTitles.size()) {
                        columns.clear();
                        for (int i = 0; i < columnsJson.length(); i++) columns.add(columnsJson.getString(i));
                    } else {
                        throw new IOException("unknown column title: " + title);
                    }
                    break;
                }
            }
        }

        if (data.optBoolean(PARAM_CLASS_IN_EXTRA_LINE)
                || data.optBoolean("class_in_extra_line")) { // backwards compatibility
            for (Element element : table.select("td.inline_header")) {
                String className = getClassName(element.text(), data);
                if (isValidClass(className)) {
                    Element zeile = null;
                    try {
                        zeile = element.parent().nextElementSibling();
                        if (zeile.select("td") == null) {
                            zeile = zeile.nextElementSibling();
                        }
                        int skipLines = 0;
                        while (zeile != null
                                && !zeile.select("td").attr("class")
                                .equals("list inline_header")) {
                            if (skipLines > 0) {
                                skipLines--;
                                zeile = zeile.nextElementSibling();
                                continue;
                            }

                            Substitution v = new Substitution();

                            int i = 0;
                            for (Element spalte : zeile.select("td")) {
                                String text = spalte.text();
                                if (isEmpty(text)) {
                                    i++;
                                    continue;
                                }

                                int skipLinesForThisColumn = 0;
                                Element nextLine = zeile.nextElementSibling();
                                boolean continueSkippingLines = true;
                                while (continueSkippingLines) {
                                    if (nextLine != null && nextLine.children().size() == zeile.children().size()) {
                                        Element columnInNextLine = nextLine.child(spalte
                                                .elementSiblingIndex());
                                        if (columnInNextLine.text().replaceAll("\u00A0", "").trim().equals(
                                                nextLine.text().replaceAll("\u00A0", "").trim())) {
                                            // Continued in the next line
                                            text += " " + columnInNextLine.text();
                                            skipLinesForThisColumn++;
                                            nextLine = nextLine.nextElementSibling();
                                        } else {
                                            continueSkippingLines = false;
                                        }
                                    } else {
                                        continueSkippingLines = false;
                                    }
                                }
                                if (skipLinesForThisColumn > skipLines) skipLines = skipLinesForThisColumn;

                                String type = columns.get(i);

                                switch (type) {
                                    case "lesson":
                                        v.setLesson(text);
                                        break;
                                    case "subject":
                                        handleSubject(v, spalte);
                                        break;
                                    case "previousSubject":
                                        v.setPreviousSubject(text);
                                        break;
                                    case "type":
                                        v.setType(text);
                                        v.setColor(colorProvider.getColor(text));
                                        break;
                                    case "type-entfall":
                                        if (text.equals("x")) {
                                            v.setType("Entfall");
                                            v.setColor(colorProvider.getColor("Entfall"));
                                        } else {
                                            v.setType("Vertretung");
                                            v.setColor(colorProvider.getColor("Vertretung"));
                                        }
                                        break;
                                    case "room":
                                        handleRoom(v, spalte);
                                        break;
                                    case "teacher":
                                        handleTeacher(v, spalte, data);
                                        break;
                                    case "previousTeacher":
                                        v.setPreviousTeachers(splitTeachers(text, data));
                                        break;
                                    case "desc":
                                        v.setDesc(text);
                                        break;
                                    case "desc-type":
                                        v.setDesc(text);
                                        String recognizedType = recognizeType(text);
                                        v.setType(recognizedType);
                                        v.setColor(colorProvider.getColor(recognizedType));
                                        break;
                                    case "previousRoom":
                                        v.setPreviousRoom(text);
                                        break;
                                    case "substitutionFrom":
                                        v.setSubstitutionFrom(text);
                                        break;
                                    case "teacherTo":
                                        v.setTeacherTo(text);
                                        break;
                                    case "ignore":
                                        break;
                                    case "date": // used by UntisSubstitutionParser
                                        break;
                                    case "class":
                                        break; // ignore - class is in extra line
                                    default:
                                        throw new IllegalArgumentException("Unknown column type: " + type);
                                }
                                i++;
                            }

                            autoDetectType(data, zeile, v);

                            v.getClasses().add(className);

                            if (v.getLesson() != null && !v.getLesson().equals("")) {
                                day.addSubstitution(v);
                            }

                            zeile = zeile.nextElementSibling();

                        }
                    } catch (Throwable e) {

                        e.printStackTrace();
                    }
                }
            }
        } else {
            boolean hasType = false;
            for (String column : columns) {
                if (column.equals("type")) {
                    hasType = true;
                }
            }
            int skipLines = 0;
            for (Element zeile : table
                    .select("tr.list.odd:not(:has(td.inline_header)), "
                            + "tr.list.even:not(:has(td.inline_header)), "
                            + "tr:has(td[align=center]):gt(0)")) {
                if (skipLines > 0) {
                    skipLines--;
                    continue;
                }

                Substitution v = new Substitution();
                String klassen = defaultClass != null ? defaultClass : "";
                int i = 0;
                for (Element spalte : zeile.select("td")) {
                    String text = spalte.text();

                    String type = columns.get(i);
                    if (isEmpty(text) && !type.equals("type-entfall")) {
                        i++;
                        continue;
                    }

                    int skipLinesForThisColumn = 0;
                    Element nextLine = zeile.nextElementSibling();
                    boolean continueSkippingLines = true;
                    while (continueSkippingLines) {
                        if (nextLine != null && nextLine.children().size() == zeile.children().size()) {
                            Element columnInNextLine = nextLine.child(spalte
                                    .elementSiblingIndex());
                            if (columnInNextLine.text().replaceAll("\u00A0", "").trim().equals(
                                    nextLine.text().replaceAll("\u00A0", "").trim())) {
                                // Continued in the next line
                                text += " " + columnInNextLine.text();
                                skipLinesForThisColumn++;
                                nextLine = nextLine.nextElementSibling();
                            } else {
                                continueSkippingLines = false;
                            }
                        } else {
                            continueSkippingLines = false;
                        }
                    }
                    if (skipLinesForThisColumn > skipLines) skipLines = skipLinesForThisColumn;

                    switch (type) {
                        case "lesson":
                            v.setLesson(text);
                            break;
                        case "subject":
                            handleSubject(v, spalte);
                            break;
                        case "previousSubject":
                            v.setPreviousSubject(text);
                            break;
                        case "type":
                            v.setType(text);
                            v.setColor(colorProvider.getColor(text));
                            break;
                        case "type-entfall":
                            if (text.equals("x")) {
                                v.setType("Entfall");
                                v.setColor(colorProvider.getColor("Entfall"));
                            } else if (!hasType) {
                                v.setType("Vertretung");
                                v.setColor(colorProvider.getColor("Vertretung"));
                            }
                            break;
                        case "room":
                            handleRoom(v, spalte);
                            break;
                        case "previousRoom":
                            v.setPreviousRoom(text);
                            break;
                        case "desc":
                            v.setDesc(text);
                            break;
                        case "desc-type":
                            v.setDesc(text);
                            String recognizedType = recognizeType(text);
                            v.setType(recognizedType);
                            v.setColor(colorProvider.getColor(recognizedType));
                            break;
                        case "teacher":
                            handleTeacher(v, spalte, data);
                            break;
                        case "previousTeacher":
                            v.setPreviousTeachers(splitTeachers(text, data));
                            break;
                        case "substitutionFrom":
                            v.setSubstitutionFrom(text);
                            break;
                        case "teacherTo":
                            v.setTeacherTo(text);
                            break;
                        case "class":
                            klassen = getClassName(text, data);
                            break;
                        case "ignore":
                            break;
                        case "date": // used by UntisSubstitutionParser
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown column type: " + type);
                    }
                    i++;
                }

                if (v.getLesson() == null || v.getLesson().equals("")) {
                    continue;
                }

                autoDetectType(data, zeile, v);

                List<String> affectedClasses;

                // Detect things like "7"
                Pattern singlePattern = Pattern.compile("(\\d+)");
                Matcher singleMatcher = singlePattern.matcher(klassen);

                // Detect things like "5-12"
                Pattern rangePattern = Pattern.compile("(\\d+) ?- ?(\\d+)");
                Matcher rangeMatcher = rangePattern.matcher(klassen);

                Pattern pattern2 = Pattern.compile("^(\\d+).*");

                if (rangeMatcher.matches()) {
                    affectedClasses = new ArrayList<>();
                    int min = Integer.parseInt(rangeMatcher.group(1));
                    int max = Integer.parseInt(rangeMatcher.group(2));
                    try {
                        for (String klasse : getAllClasses()) {
                            Matcher matcher2 = pattern2.matcher(klasse);
                            if (matcher2.matches()) {
                                int num = Integer.parseInt(matcher2.group(1));
                                if (min <= num && num <= max) affectedClasses.add(klasse);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (singleMatcher.matches()) {
                    affectedClasses = new ArrayList<>();
                    int grade = Integer.parseInt(singleMatcher.group(1));
                    try {
                        for (String klasse : getAllClasses()) {
                            Matcher matcher2 = pattern2.matcher(klasse);
                            if (matcher2.matches() && grade == Integer.parseInt(matcher2.group(1))) {
                                affectedClasses.add(klasse);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    if (data.optBoolean(PARAM_CLASSES_SEPARATED, true)
                            && data.optBoolean("classes_separated", true)) { // backwards compatibility
                        affectedClasses = Arrays.asList(klassen.split(", "));
                    } else {
                        affectedClasses = new ArrayList<>();
                        try {
                            for (String klasse : getAllClasses()) { // TODO: is there a better way?
                                StringBuilder regex = new StringBuilder();
                                for (char character : klasse.toCharArray()) {
                                    if (character == '?') {
                                        regex.append("\\?");
                                    } else {
                                        regex.append(character);
                                    }
                                    regex.append(".*");
                                }
                                if (klassen.matches(regex.toString())) {
                                    affectedClasses.add(klasse);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                for (String klasse : affectedClasses) {
                    if (isValidClass(klasse)) {
                        v.getClasses().add(klasse);
                    }
                }

                if (data.optBoolean(PARAM_MERGE_WITH_DIFFERENT_TYPE, false)) {
                    boolean found = false;
                    for (Substitution subst : day.getSubstitutions()) {
                        if (subst.equalsExcludingType(v)) {
                            found = true;

                            if (v.getType().equals("Vertretung")) {
                                subst.setType("Vertretung");
                                subst.setColor(colorProvider.getColor("Vertretung"));
                            }

                            break;
                        }
                    }
                    if (!found) {
                        day.addSubstitution(v);
                    }
                } else {
                    day.addSubstitution(v);
                }
            }
        }
    }

    private void autoDetectType(JSONObject data, Element zeile, Substitution v) {
        if (v.getType() == null) {
            if (data.optBoolean(PARAM_TYPE_AUTO_DETECTION, true)) {
                if ((zeile.select("strike").size() > 0 &&
                        equalsOrNull(v.getSubject(), v.getPreviousSubject()) &&
                        equalsOrNull(v.getTeacher(), v.getPreviousTeacher()))
                        || (v.getSubject() == null && v.getRoom() == null && v
                        .getTeacher() == null && v.getPreviousSubject() != null)) {
                    v.setType("Entfall");
                    v.setColor(colorProvider.getColor("Entfall"));
                } else {
                    v.setType("Vertretung");
                    v.setColor(colorProvider.getColor("Vertretung"));
                }
            } else {
                v.setType("Vertretung");
                v.setColor(colorProvider.getColor("Vertretung"));
            }
        }
    }

    static void handleTeacher(Substitution subst, Element cell, JSONObject data) {
        cell = getContentElement(cell);
        if (cell.select("s").size() > 0) {
            subst.setPreviousTeachers(splitTeachers(cell.select("s").text(), data));
            if (cell.ownText().length() > 0) {
                subst.setTeachers(splitTeachers(cell.ownText().replaceFirst("^\\?", "").replaceFirst("→", ""), data));
            }
        } else {
            subst.setTeachers(splitTeachers(cell.text(), data));
        }
    }

    private static Set<String> splitTeachers(String s, JSONObject data) {
        Set<String> teachers = new HashSet<>();
        if (data.optBoolean("splitTeachers", true)) {
            teachers.addAll(Arrays.asList(s.split(", ")));
        } else {
            teachers.add(s);
        }
        return teachers;
    }

    static void handleRoom(Substitution subst, Element cell) {
        cell = getContentElement(cell);
        if (cell.select("s").size() > 0) {
            subst.setPreviousRoom(cell.select("s").text());
            if (cell.ownText().length() > 0) {
                subst.setRoom(cell.ownText().replaceFirst("^\\?", "").replaceFirst("→", ""));
            }
        } else {
            subst.setRoom(cell.text());
        }
    }

    private static Element getContentElement(Element cell) {
        if (cell.ownText().isEmpty() && cell.select("> span").size() == 1) {
            cell = cell.select("> span").first();
        }
        return cell;
    }

    static void handleSubject(Substitution subst, Element cell) {
        cell = getContentElement(cell);
        if (cell.select("s").size() > 0) {
            subst.setPreviousSubject(cell.select("s").text());
            if (cell.ownText().length() > 0) {
                subst.setSubject(cell.ownText().replaceFirst("^\\?", "").replaceFirst("→", ""));
            }
        } else {
            subst.setSubject(cell.text());
        }
    }

    private boolean isEmpty(String text) {
        return text.replaceAll("\u00A0", "").trim().equals("") || text.replaceAll("\u00A0", "").trim().equals("---");
    }

    /**
     * Parses a "Nachrichten zum Tag" ("daily news") table from an Untis schedule
     *
     * @param table the <code>table</code>-Element to be parsed
     * @param day   the {@link SubstitutionScheduleDay} where the messages should be stored
     */
    private void parseMessages(Element table, SubstitutionScheduleDay day) {
        Elements zeilen = table
                .select("tr:not(:contains(Nachrichten zum Tag))");
        for (Element i : zeilen) {
            Elements spalten = i.select("td");
            String info = "";
            for (Element b : spalten) {
                info += "\n"
                        + TextNode.createFromEncoded(b.html(), null)
                        .getWholeText();
            }
            info = info.substring(1); // remove first \n
            day.addMessage(info);
        }
    }

    SubstitutionScheduleDay parseMonitorDay(Element doc, JSONObject data) throws
            JSONException, CredentialInvalidException, IOException {
        SubstitutionScheduleDay day = new SubstitutionScheduleDay();
        String date = doc.select(".mon_title").first().text().replaceAll(" \\(Seite \\d+ / \\d+\\)", "");
        day.setDateString(date);
        day.setDate(ParserUtils.parseDate(date));

        if (!scheduleData.getData().has(PARAM_LAST_CHANGE_SELECTOR)) {
            String lastChange = findLastChange(doc, scheduleData);
            day.setLastChangeString(lastChange);
            day.setLastChange(ParserUtils.parseDateTime(lastChange));
        }

        // NACHRICHTEN
        if (doc.select("table.info").size() > 0) {
            parseMessages(doc.select("table.info").first(), day);
        }

        // VERTRETUNGSPLAN
        if (doc.select("table:has(tr.list)").size() > 0) {
            parseSubstitutionScheduleTable(doc.select("table:has(tr.list)").first(), data, day);
        }

        return day;
    }

    private boolean isValidClass(String klasse) throws JSONException {
        return klasse != null && !Arrays.asList(EXCLUDED_CLASS_NAMES).contains(klasse) &&
                !(scheduleData.getData().has(PARAM_EXCLUDE_CLASSES) &&
                        contains(scheduleData.getData().getJSONArray(PARAM_EXCLUDE_CLASSES), klasse)) &&
                !(scheduleData.getData().has("exclude_classes") && // backwards compatibility
                        contains(scheduleData.getData().getJSONArray("exclude_classes"), klasse));
    }

    @Override
    public List<String> getAllClasses() throws IOException, JSONException, CredentialInvalidException {
        return getClassesFromJson();
    }

    void parseDay(SubstitutionScheduleDay day, Element next, SubstitutionSchedule v, String klasse) throws
            JSONException, CredentialInvalidException, IOException {
        if (next.className().equals("subst") || next.select(".list").size() > 0
                || next.text().contains("Vertretungen sind nicht freigegeben")
                || next.text().contains("Keine Vertretungen")) {
            //Vertretungstabelle
            if (next.text().contains("Vertretungen sind nicht freigegeben")) {
                return;
            }
            parseSubstitutionScheduleTable(next, scheduleData.getData(), day, klasse);
        } else {
            //Nachrichten
            parseMessages(next, day);
            next = next.nextElementSibling().nextElementSibling();
            parseSubstitutionScheduleTable(next, scheduleData.getData(), day, klasse);
        }
        v.addDay(day);
    }

    void parseMultipleMonitorDays(SubstitutionSchedule v, Document doc, JSONObject data)
            throws JSONException, CredentialInvalidException, IOException {
        if (doc.select(".mon_head").size() > 1) {
            for (int j = 0; j < doc.select(".mon_head").size(); j++) {
                Document doc2 = Document.createShell(doc.baseUri());
                doc2.body().appendChild(doc.select(".mon_head").get(j).clone());
                Element next = doc.select(".mon_head").get(j).nextElementSibling();
                if (next != null && next.tagName().equals("center")) {
                    doc2.body().appendChild(next.select(".mon_title").first().clone());
                    if (next.select("table:has(tr.list)").size() > 0) {
                        doc2.body().appendChild(next.select("table:has(tr.list)").first());
                    }
                    if (next.select("table.info").size() > 0) {
                        doc2.body().appendChild(next.select("table.info").first());
                    }
                } else if (doc.select(".mon_title").size() - 1 >= j) {
                    doc2.body().appendChild(doc.select(".mon_title").get(j).clone());
                    doc2.body().appendChild(doc.select("table:has(tr.list)").get(j).clone());
                } else {
                    continue;
                }
                SubstitutionScheduleDay day = parseMonitorDay(doc2, data);
                v.addDay(day);
            }
        } else if (doc.select(".mon_title").size() > 1) {
            for (int j = 0; j < doc.select(".mon_title").size(); j++) {
                Document doc2 = Document.createShell(doc.baseUri());
                doc2.body().appendChild(doc.select(".mon_title").get(j).clone());
                Element next = doc.select(".mon_title").get(j).nextElementSibling();
                while (next != null && !next.tagName().equals("center")) {
                    doc2.body().appendChild(next);
                    next = doc.select(".mon_title").get(j).nextElementSibling();
                }
                SubstitutionScheduleDay day = parseMonitorDay(doc2, data);
                v.addDay(day);
            }
        } else {
            SubstitutionScheduleDay day = parseMonitorDay(doc, data);
            v.addDay(day);
        }
    }

    /**
     * Parses an Untis substitution table ({@link UntisSubstitutionParser}).
     *
     * @param v
     * @param lastChange
     * @param doc
     * @throws JSONException
     * @throws CredentialInvalidException
     */
    protected void parseSubstitutionTable(SubstitutionSchedule v, String lastChange, Document doc)
            throws JSONException, CredentialInvalidException, IOException {
        JSONObject data = scheduleData.getData();

        LocalDateTime lastChangeDate = ParserUtils.parseDateTime(lastChange);
        Pattern dayPattern = Pattern.compile("\\d\\d?.\\d\\d?. / \\w+");

        int dateColumn = -1;
        JSONArray columns = data.getJSONArray("columns");
        for (int i = 0; i < columns.length(); i++) {
            if (columns.getString(i).equals("date")) {
                dateColumn = i;
                break;
            }
        }

        Element table = doc.select("table[rules=all], table:has(tr:has(td[align=center]))").first();
        if (table.text().replace("\u00a0", "").trim().equals("Keine Vertretungen")) return;

        if (dateColumn == -1) {
            SubstitutionScheduleDay day = new SubstitutionScheduleDay();
            day.setLastChangeString(lastChange);
            day.setLastChange(lastChangeDate);
            String title = doc.select("font[size=5], font[size=4], font[size=3] b").text();
            Matcher matcher = dayPattern.matcher(title);
            if (matcher.find()) {
                String date = matcher.group();
                day.setDateString(date);
                day.setDate(ParserUtils.parseDate(date));
            }
            parseSubstitutionScheduleTable(table, data, day);
            v.addDay(day);
        } else {
            for (Element line : table
                    .select("tr.list.odd:not(:has(td.inline_header)), "
                            + "tr.list.even:not(:has(td.inline_header)), "
                            + "tr:has(td[align=center]):gt(0)")) {
                SubstitutionScheduleDay day = null;
                String date = line.select("td").get(dateColumn).text().trim();

                if (date.indexOf("-") > 0) {
                    date = date.substring(0, date.indexOf("-") - 1).trim();
                }

                LocalDate parsedDate = ParserUtils.parseDate(date);
                for (SubstitutionScheduleDay search : v.getDays()) {
                    if (Objects.equals(search.getDate(), parsedDate)
                            || Objects.equals(search.getDateString(), date)) {
                        day = search;
                        break;
                    }
                }
                if (day == null) {
                    day = new SubstitutionScheduleDay();
                    day.setDateString(date);
                    day.setDate(parsedDate);
                    day.setLastChangeString(lastChange);
                    day.setLastChange(lastChangeDate);
                    v.addDay(day);
                }
                parseSubstitutionScheduleTable(line, data, day);
            }
        }
    }
}
