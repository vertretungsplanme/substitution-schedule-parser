/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 * Copyright (c) 2016 Nico Alt
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
import me.vertretungsplan.objects.credential.Credential;
import me.vertretungsplan.objects.credential.UserPasswordCredential;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.HttpResponseException;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Parser for LegionBoard, an open source changes management system for schools.
 *
 * More information on the <a href="https://legionboard.github.io">official website</a>
 * and on its <a href="https://gitlab.com/groups/legionboard">project page on GitLab</a>.
 *
 * <h4>Configuration parameters</h4>
 * These parameters can be supplied in {@link SubstitutionScheduleData#setData(JSONObject)} to configure the parser:
 *
 * <dl>
 * <dt><code>api</code> (String, required)</dt>
 * <dd>The URL where the LegionBoard Heart API can be found.</dd>
 *
 * <dt><code>website</code> (String, recommended)</dt>
 * <dd>The URL of a website where the substitution schedule can be seen online. Normally, this would be the URL of the
 * LegionBoard Eye instance.</dd>
 *
 * You have to use a {@link me.vertretungsplan.objects.authentication.UserPasswordAuthenticationData} because all
 * schedules on LegionBoard are protected by a login.
 */
public class LegionBoardParser extends BaseParser {

    private static final String PARAM_API = "api";
    private static final String PARAM_WEBSITE = "website";

	/**
	 * URL of given LegionBoard Heart instance
	 */
	private String api;

	/**
	 * URL of given LegionBoard Eye instance
	 */
	private String website;

	public LegionBoardParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
		super(scheduleData, cookieProvider);
        JSONObject data = scheduleData.getData();
        try {
            api = data.getString(PARAM_API);
            website = data.getString(PARAM_WEBSITE);
        } catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public SubstitutionSchedule getSubstitutionSchedule() throws IOException, JSONException, CredentialInvalidException {
		final SubstitutionSchedule substitutionSchedule = SubstitutionSchedule.fromData(scheduleData);
		substitutionSchedule.setClasses(getAllClasses());
		substitutionSchedule.setTeachers(getAllTeachers());
		substitutionSchedule.setWebsite(website);
		final JSONArray changes = getChanges();
		final JSONArray courses = getCourses();
		final JSONArray teachers = getTeachers();

		parseLegionBoard(substitutionSchedule, changes, courses, teachers);
		return substitutionSchedule;
	}

	/**
	 * Returns authentication key as shown
	 * <a href="https://gitlab.com/legionboard/heart/blob/master/doc/README.md">in the documentation</a>.
	 */
	private String getAuthenticationKey(Credential credential) {
		final UserPasswordCredential userPasswordCredential = (UserPasswordCredential) credential;
		final String username = userPasswordCredential.getUsername();
		final String password = userPasswordCredential.getPassword();
		return DigestUtils.sha256Hex(username.toLowerCase() + "//" + password);
	}

	/**
	 * Returns a JSONArray with all changes from now to in one week.
	 * More information: <a href="https://gitlab.com/legionboard/heart/blob/master/doc/changes/list.md">List changes</a>
	 */
	private JSONArray getChanges() throws IOException, JSONException {
		// Date (or alias of date) when the changes start
		final String startBy = "now";
		// Date (or alias of date) when the changes end
		final String endBy = "i1w";

		final String url = api + "/changes?startBy=" + startBy + "&endBy=" + endBy + "&k=" + getAuthenticationKey(getCredential());
		return getJSONArray(url);
	}

	/**
	 * Returns a JSONArray with all courses.
	 * More information: <a href="https://gitlab.com/legionboard/heart/blob/master/doc/courses/list.md">List courses</a>
	 */
	private JSONArray getCourses() throws IOException, JSONException {
		final String url = api + "/courses?k=" + getAuthenticationKey(getCredential());
		return getJSONArray(url);
	}

	/**
	 * Returns a JSONArray with all teachers.
	 * More information: <a href="https://gitlab.com/legionboard/heart/blob/master/doc/teachers/list.md">List teachers</a>
	 */
	private JSONArray getTeachers() throws IOException, JSONException {
		final String url = api + "/teachers?k=" + getAuthenticationKey(getCredential());
		return getJSONArray(url);
	}

	private JSONArray getJSONArray(String url) throws IOException, JSONException {
		try {
			return new JSONArray(httpGet(url, "UTF-8"));
		} catch (HttpResponseException httpResponseException) {
			if (httpResponseException.getStatusCode() == 404) {
				return null;
			}
			throw httpResponseException;
		}
	}

    void parseLegionBoard(SubstitutionSchedule substitutionSchedule, JSONArray changes, JSONArray courses,
                          JSONArray teachers) throws IOException, JSONException {
        if (changes == null) {
			return;
		}
		// Link course IDs to their names
		HashMap<String, String> coursesHashMap = null;
		if (courses != null) {
			coursesHashMap = new HashMap<>();
			for (int i = 0; i < courses.length(); i++) {
				JSONObject course = courses.getJSONObject(i);
				coursesHashMap.put(course.getString("id"), course.getString("name"));
			}
		}
		// Link teacher IDs to their names
		HashMap<String, String> teachersHashMap = null;
		if (teachers != null) {
			teachersHashMap = new HashMap<>();
			for (int i = 0; i < teachers.length(); i++) {
				JSONObject teacher = teachers.getJSONObject(i);
				teachersHashMap.put(teacher.getString("id"), teacher.getString("name"));
			}
		}
		// Add changes to SubstitutionSchedule
		LocalDate currentDate = LocalDate.now();
		SubstitutionScheduleDay substitutionScheduleDay = new SubstitutionScheduleDay();
		substitutionScheduleDay.setDate(currentDate);
		for (int i = 0; i < changes.length(); i++) {
			final JSONObject change = changes.getJSONObject(i);
			final Substitution substitution = getSubstitution(change, coursesHashMap, teachersHashMap);
			final LocalDate startingDate = new LocalDate(change.getString("startingDate"));
			final LocalDate endingDate = new LocalDate(change.getString("endingDate"));
			// Handle multi-day changes
			if (!startingDate.isEqual(endingDate)) {
				if (!substitutionScheduleDay.getSubstitutions().isEmpty()) {
					substitutionSchedule.addDay(substitutionScheduleDay);
				}
				for (int k = 0; k < 7; k++) {
					final LocalDate date = LocalDate.now().plusDays(k);
					if ((date.isAfter(startingDate) || date.isEqual(startingDate)) &&
						(date.isBefore(endingDate) || date.isEqual(endingDate))) {
						substitutionScheduleDay = new SubstitutionScheduleDay();
						substitutionScheduleDay.setDate(date);
						substitutionScheduleDay.addSubstitution(substitution);
						substitutionSchedule.addDay(substitutionScheduleDay);
                        currentDate = date;
                    }
				}
				continue;
			}
			// If starting date of change does not equal date of SubstitutionScheduleDay
			if (!startingDate.isEqual(currentDate)) {
				if (!substitutionScheduleDay.getSubstitutions().isEmpty()) {
					substitutionSchedule.addDay(substitutionScheduleDay);
				}
				substitutionScheduleDay = new SubstitutionScheduleDay();
				substitutionScheduleDay.setDate(startingDate);
                currentDate = startingDate;
            }
			substitutionScheduleDay.addSubstitution(substitution);
		}
		substitutionSchedule.addDay(substitutionScheduleDay);
	}
	
	private Substitution getSubstitution(JSONObject change, HashMap<String, String> coursesHashMap, HashMap<String, String> teachersHashMap) throws IOException, JSONException {
		final Substitution substitution = new Substitution();
		// Set class
		final String classId = change.getString("course");
		if (!classId.equals("0")) {
			if (coursesHashMap == null) {
				throw new IOException("Change references a course but courses are empty.");
			}
			final String singleClass = coursesHashMap.get(classId);
			final HashSet<String> classes = new HashSet<>();
			classes.add(singleClass);
			substitution.setClasses(classes);
		}
		// Set type
		String type = "Unknown";
		switch (change.getString("type")) {
			case "0":
				type = "Entfall";
				break;
			case "1":
				type = "Vertretung";
				break;
			case "2":
				type = "Information";
				break;
		}
		substitution.setType(type);
		// Set color
		substitution.setColor(colorProvider.getColor(type));
		// Set covering teacher
		final String coveringTeacherId = change.getString("coveringTeacher");
		if (!coveringTeacherId.equals("0")) {
			if (teachersHashMap == null) {
				throw new IOException("Change references a covering teacher but teachers are empty.");
			}
			substitution.setTeacher(teachersHashMap.get(coveringTeacherId));
		}
		// Set teacher
		final String teacherId = change.getString("teacher");
		if (!teacherId.equals("0")) {
			if (teachersHashMap == null) {
				throw new IOException("Change references a teacher but teachers are empty.");
			}
			if (type.equals("Vertretung") || !coveringTeacherId.equals("0")) {
				substitution.setPreviousTeacher(teachersHashMap.get(teacherId));
			} else {
				substitution.setTeacher(teachersHashMap.get(teacherId));
			}
				
		}
		// Set description
		substitution.setDesc(change.getString("text"));
		// Set lesson
		final String startingHour = change.getString("startingHour");
		final String endingHour = change.getString("endingHour");
		if (!startingHour.equals("") || !endingHour.equals("")) {
			String lesson = "";
			if (!startingHour.equals("") && endingHour.equals("")) {
				lesson = "Ab " + startingHour;
			}
			if (startingHour.equals("") && !endingHour.equals("")) {
				lesson = "Bis " + endingHour;
			}
			if (!startingHour.equals("") && !endingHour.equals("")) {
				lesson = startingHour + " - " + endingHour;
			}
			substitution.setLesson(lesson);
		}
		return substitution;
	}

	@Override
	public List<String> getAllClasses() throws IOException, JSONException {
		final List<String> classes = new ArrayList<>();
		final JSONArray courses = getCourses();
		if (courses == null) {
			return null;
		}
		for (int i = 0; i < courses.length(); i++) {
			final JSONObject course = courses.getJSONObject(i);
			if (!course.getBoolean("archived")) {
				classes.add(course.getString("name"));
			}
		}
		Collections.sort(classes);
		return classes;
	}

	@Override
	public List<String> getAllTeachers() throws IOException, JSONException {
		final List<String> teachers = new ArrayList<>();
		final JSONArray jsonTeachers = getTeachers();
		if (jsonTeachers == null) {
			return null;
		}
		for (int i = 0; i < jsonTeachers.length(); i++) {
			final JSONObject teacher = jsonTeachers.getJSONObject(i);
			if (!teacher.getBoolean("archived")) {
				teachers.add(teacher.getString("name"));
			}
		}
		Collections.sort(teachers);
		return teachers;
	}
}
