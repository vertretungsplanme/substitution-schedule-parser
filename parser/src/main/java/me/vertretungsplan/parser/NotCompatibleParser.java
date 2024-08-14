/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2017 Johan v. Forstner
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
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class NotCompatibleParser extends BaseParser {
    private final SubstitutionScheduleData scheduleData;

    public NotCompatibleParser(SubstitutionScheduleData scheduleData, CookieProvider cookieProvider) {
        super(scheduleData, cookieProvider);
        this.scheduleData = scheduleData;
    }

    @Override
    public SubstitutionSchedule getSubstitutionSchedule() throws JSONException {
        SubstitutionSchedule v = SubstitutionSchedule.fromData(scheduleData);
        v.setLastChange(new LocalDateTime(2000, 1, 1, 0, 0));
        SubstitutionScheduleDay today = new SubstitutionScheduleDay();
        today.setDate(new LocalDate(2999, 1, 1));

        Substitution subst = new Substitution();
        subst.setLesson("0");
        subst.setClasses(new HashSet<>(getAllClasses()));
        subst.setType("siehe Nachrichten");
        subst.setDesc("Der Vertretungsplan kann von dieser Schule nicht mehr abgerufen werden. Genauere Informationen" +
                " findest du unter \"Nachrichten\".");
        subst.setColor("#F44336");
        today.addSubstitution(subst);

        String appName = null;
        switch (scheduleData.getApi()) {
            case "dsbmobile":
                appName = "DSBmobile";
                break;
            case "webuntis":
                appName = "Untis Mobile";
                break;
            case "not-desired":
                appName = "not-desired";
                break;
        }
        if (Objects.equals(appName, "not-desired")) {
            today.addMessage("Auf Wunsch der Schulleitung wurde der Vertretungsplan dieser Schule aus der App " +
                    "entfernt. Bei Fragen wenden Sie sich bitte direkt an die Schulleitung. " +
                    "Lehrkräfte und Schulleiter/-innen können sich unter info@vertretungsplan.me bei uns melden, " +
                    "um den Plan wieder in die App aufzunehmen.");
        } else {
            today.addMessage("Aus technischen Gründen kann der Vertretungsplan dieser Schule mit dieser App nicht mehr " +
                    "abgerufen werden. " +
                    (appName != null ? "Als Alternative kannst du vorerst die offizielle " +
                            "App \"" + appName + "\" nutzen. " : "") +
                    "Falls Sie eine Lehrkraft oder Schulleiter/-in an der Schule sind, melden Sie sich " +
                    "bitte unter info@vertretungsplan.me bei uns, um herauszufinden, wie der Plan wieder in die App " +
                    "aufgenommen werden kann.");
        }


        v.addDay(today);

        v.setClasses(getAllClasses());
        v.setTeachers(new ArrayList<>());

        return v;
    }

    @Override public List<String> getAllClasses() throws JSONException {
        List<String> classes = getClassesFromJson();
        if (classes == null) {
            classes = new ArrayList<>();
            classes.add("");
        }
        return classes;
    }

    @Override public List<String> getAllTeachers() {
        return null;
    }

    @Override public LocalDateTime getLastChange() {
        return new LocalDateTime(2000, 1, 1, 0, 0);
    }

    @Override public void setCredential(Credential credential) {

    }

    @Override public Credential getCredential() {
        return null;
    }

}
