/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package me.vertretungsplan.additionalinfo;

import me.vertretungsplan.additionalinfo.amgrottweil.AmgRottweilIcalParser;
import me.vertretungsplan.additionalinfo.amgrottweil.AmgRottweilRSSParser;
import me.vertretungsplan.additionalinfo.amgrottweil.AmgRottweilStudentMessagesParser;
import me.vertretungsplan.additionalinfo.amgrottweil.AmgRottweilTeacherMessagesParser;
import me.vertretungsplan.additionalinfo.eichendorffgymkoblenz.EichendorffGymKoblenzIcalParser;
import me.vertretungsplan.additionalinfo.evsschwalmstadt.EvsSchwalmstadtIcalParser;
import me.vertretungsplan.additionalinfo.gymholthausenhattingen.GymHolthausenIcalParser;
import me.vertretungsplan.additionalinfo.gymholthausenhattingen.GymHolthausenRSSParser;
import me.vertretungsplan.additionalinfo.kantschulefalkensee.KantschuleFalkenseeRSSParser;
import me.vertretungsplan.additionalinfo.lsschleswig.LsSchleswigIcalParser;
import me.vertretungsplan.additionalinfo.lsschleswig.LsSchleswigRSSParser;
import me.vertretungsplan.objects.AdditionalInfo;
import org.apache.http.client.fluent.Request;

import java.io.IOException;

/**
 * Parser that creates {@link AdditionalInfo}s.
 */
public abstract class BaseAdditionalInfoParser {
    protected BaseAdditionalInfoParser() {
    }

    /**
     * Create an additional info parser. Uses the supplied {@code type} string to create an appropriate subclass.
     *
     * @param type the type of additional info
     * @return A {@link BaseAdditionalInfoParser} subclass
     */
    public static BaseAdditionalInfoParser getInstance(String type) {
        BaseAdditionalInfoParser parser = null;
        switch (type) {
            case "winter-sh":
                parser = new WinterShParser();
                break;
            case "amgrottweil-rss":
                parser = new AmgRottweilRSSParser();
                break;
            case "amgrottweil-messages-student":
                parser = new AmgRottweilStudentMessagesParser();
                break;
            case "amgrottweil-messages-teacher":
                parser = new AmgRottweilTeacherMessagesParser();
                break;
            case "amgrottweil-ical":
                parser = new AmgRottweilIcalParser();
                break;
            case "lsschleswig-rss":
                parser = new LsSchleswigRSSParser();
                break;
            case "lsschleswig-ical":
                parser = new LsSchleswigIcalParser();
                break;
            case "gymholthausenhattingen-rss":
                parser = new GymHolthausenRSSParser();
                break;
            case "gymholthausenhattingen-ical":
                parser = new GymHolthausenIcalParser();
                break;
            case "eichendorffgymkoblenz-ical":
                parser = new EichendorffGymKoblenzIcalParser();
                break;
            case "evsschwalmstadt-ical":
                parser = new EvsSchwalmstadtIcalParser();
                break;
            case "kantschulefalksensee-rss":
                parser = new KantschuleFalkenseeRSSParser();
                break;
        }
        return parser;
    }

    public abstract AdditionalInfo getAdditionalInfo() throws IOException;

    @SuppressWarnings("SameParameterValue")
    protected String httpGet(String url, String encoding) throws IOException {
        return new String(Request.Get(url).execute().returnContent().asBytes(), encoding);
    }
}
