/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */

package me.vertretungsplan.parser;

import me.vertretungsplan.objects.Substitution;
import me.vertretungsplan.objects.SubstitutionSchedule;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import me.vertretungsplan.objects.SubstitutionScheduleDay;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enthält gemeinsam genutzte Funktionen für die Parser für
 * Untis-Vertretungspläne
 *
 */
public abstract class UntisCommonParser extends BaseParser {

    private static final String[] EXCLUDED_CLASS_NAMES = new String[]{"-----"};

	public UntisCommonParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
		super(scheduleData, cookieProvider);
	}

	static String findLastChange(Element doc, SubstitutionScheduleData scheduleData) {
		String lastChange = null;
		if (doc.select("table.mon_head").size() > 0) {
			Element monHead = doc.select("table.mon_head").first();
			lastChange = findLastChangeFromMonHeadTable(monHead);
		} else if (scheduleData != null && scheduleData.getData().optBoolean("stand_links", false)) {
			lastChange = doc.select("body").html().substring(0, doc.select("body").html().indexOf("<p>") - 1);
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
	 * Parst eine Vertretungstabelle eines Untis-Vertretungsplans
	 *
	 * @param table
	 *            das <code>table</code>-Element des HTML-Dokuments, das geparst
	 *            werden soll
	 * @param data
	 *            Daten von der Schule (aus <code>Schule.getData()</code>)
	 * @param day
	 *            der {@link SubstitutionScheduleDay} in dem die Vertretungen
	 *            gespeichert werden sollen
	 * @throws JSONException
	 */
	protected void parseVertretungsplanTable(Element table, JSONObject data,
											 SubstitutionScheduleDay day) throws JSONException {
		if (data.optBoolean("class_in_extra_line")) {
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
                                skipLines --;
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

								String type = data.getJSONArray("columns")
                                        .getString(i);

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
										handleTeacher(v, spalte);
										break;
									case "previousTeacher":
										v.setPreviousTeacher(text);
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
								}
								i++;
							}

							if (v.getType() == null) {
								v.setType("Vertretung");
								v.setColor(colorProvider.getColor("Vertretung"));
							}

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
			for (int i = 0; i < data.getJSONArray("columns").length(); i++) {
				if (data.getJSONArray("columns").getString(i).equals("type"))
					hasType = true;
			}
			Substitution previousSubstitution = null;
			int skipLines = 0;
            for (Element zeile : table
					.select("tr.list.odd:not(:has(td.inline_header)), "
							+ "tr.list.even:not(:has(td.inline_header)), "
							+ "tr:has(td[align=center]):gt(0)")) {
				if (skipLines > 0) {
					skipLines --;
					continue;
				}

				Substitution v = new Substitution();
				String klassen = "";
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

					String type = data.getJSONArray("columns").getString(i);
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
							handleTeacher(v, spalte);
							break;
						case "previousTeacher":
							v.setPreviousTeacher(text);
							break;
						case "class":
							klassen = getClassName(text, data);
							break;
					}
					i++;
				}

				if (v.getLesson() == null || v.getLesson().equals("")) {
					continue;
				}

				if (v.getType() == null) {
					if ((zeile.select("strike").size() > 0 && equalsOrNull(v.getSubject(), v.getPreviousSubject()) &&
							equalsOrNull(v.getTeacher(), v.getPreviousTeacher()))
							|| (v.getSubject() == null && v.getRoom() == null && v
							.getTeacher() == null && v.getPreviousSubject() != null)) {
						v.setType("Entfall");
						v.setColor(colorProvider.getColor("Entfall"));
					} else {
						v.setType("Vertretung");
						v.setColor(colorProvider.getColor("Vertretung"));
					}
				}

				List<String> affectedClasses;

				// Detect things like "5-12"
				Pattern pattern = Pattern.compile("(\\d+) ?- ?(\\d+)");
				Matcher matcher = pattern.matcher(klassen);
				if (matcher.find()) {
					affectedClasses = new ArrayList<String>();
					int min = Integer.parseInt(matcher.group(1));
					int max = Integer.parseInt(matcher.group(2));
					try {
						for (String klasse : getAllClasses()) {
							Pattern pattern2 = Pattern.compile("\\d+");
							Matcher matcher2 = pattern2.matcher(klasse);
							if (matcher2.find()) {
								int num = Integer.parseInt(matcher2.group());
								if (min <= num && num <= max)
									affectedClasses.add(klasse);
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					if (data.optBoolean("classes_separated", true)) {
						affectedClasses = Arrays.asList(klassen.split(", "));
					} else {
						affectedClasses = new ArrayList<String>();
						try {
							for (String klasse : getAllClasses()) { // TODO:
																	// Gibt es
																	// eine
																	// bessere
																	// Möglichkeit?
								StringBuilder regex = new StringBuilder();
								for (char character : klasse.toCharArray()) {
									regex.append(character);
									regex.append(".*");
								}
								if (klassen.matches(regex.toString()))
									affectedClasses.add(klasse);
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
				day.addSubstitution(v);
				previousSubstitution = v;
			}
		}
    }

	private void handleTeacher(Substitution subst, Element cell) {
		if (cell.select("s").size() > 0) {
			subst.setPreviousTeacher(cell.select("s").text());
			if (cell.ownText().length() > 0) {
				subst.setTeacher(cell.ownText().replaceFirst("^\\?", ""));
			}
		} else {
			subst.setTeacher(cell.text());
		}
	}

	private void handleRoom(Substitution subst, Element cell) {
		if (cell.select("s").size() > 0) {
			subst.setPreviousRoom(cell.select("s").text());
			if (cell.ownText().length() > 0) {
				subst.setRoom(cell.ownText().replaceFirst("^\\?", ""));
			}
		} else {
			subst.setRoom(cell.text());
		}
	}

	private void handleSubject(Substitution subst, Element cell) {
		if (cell.select("s").size() > 0) {
			subst.setPreviousSubject(cell.select("s").text());
			if (cell.ownText().length() > 0) {
				subst.setSubject(cell.ownText().replaceFirst("^\\?", ""));
			}
		} else {
			subst.setSubject(cell.text());
		}
	}

	private boolean isEmpty(String text) {
		return text.trim().equals("") || text.trim().equals("---");
	}

	/**
	 * Parst eine "Nachrichten zum Tag"-Tabelle aus Untis-Vertretungsplänen
	 *  @param table
	 *            das <code>table</code>-Element des HTML-Dokuments, das geparst
	 *            werden soll
	 * @param day
	 *            der {@link SubstitutionScheduleDay} in dem die Nachrichten
	 */
	protected void parseMessages(Element table, SubstitutionScheduleDay day) {
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

	protected SubstitutionScheduleDay parseMonitorVertretungsplanTag(Element doc, JSONObject data) throws
			JSONException {
		SubstitutionScheduleDay day = new SubstitutionScheduleDay();
		String date = doc.select(".mon_title").first().text().replaceAll(" \\(Seite \\d+ / \\d+\\)", "");
		day.setDateString(date);
		day.setDate(ParserUtils.parseDate(date));

		if (!scheduleData.getData().has("lastChangeSelector")) {
			String lastChange = findLastChange(doc, scheduleData);
			day.setLastChangeString(lastChange);
			day.setLastChange(ParserUtils.parseDateTime(lastChange));
		}

		// NACHRICHTEN
		if (doc.select("table.info").size() > 0) {
			parseMessages(doc.select("table.info").first(), day);
		}

		// VERTRETUNGSPLAN
        if (doc.select("table:has(tr.list)").size() > 0)
			parseVertretungsplanTable(doc.select("table:has(tr.list)").first(), data, day);

		return day;
	}

	private boolean isValidClass(String klasse) throws JSONException {
		return klasse != null && !Arrays.asList(EXCLUDED_CLASS_NAMES).contains(klasse) &&
				!(scheduleData.getData().has("exclude_classes") &&
						contains(scheduleData.getData().getJSONArray("exclude_classes"), klasse));
	}

	@Override
	public List<String> getAllClasses() throws IOException, JSONException {
		return getClassesFromJson();
	}

	protected void parseDay(SubstitutionScheduleDay day, Element next, SubstitutionSchedule v) throws JSONException {
		if (next.className().equals("subst")) {
			//Vertretungstabelle
			if (next.text().contains("Vertretungen sind nicht freigegeben")) {
				return;
			}
			parseVertretungsplanTable(next, scheduleData.getData(), day);
		} else {
			//Nachrichten
			parseMessages(next, day);
			next = next.nextElementSibling().nextElementSibling();
			parseVertretungsplanTable(next, scheduleData.getData(), day);
		}
		v.addDay(day);
	}
}
