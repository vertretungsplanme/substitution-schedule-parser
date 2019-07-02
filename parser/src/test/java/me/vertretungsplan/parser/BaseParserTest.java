/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2018 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.parser;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Feature;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;
import me.vertretungsplan.exception.CredentialInvalidException;
import me.vertretungsplan.objects.SubstitutionScheduleData;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class BaseParserTest {
    @Test
    public void testHandleClassRanges() throws JSONException {
        JSONObject data = new JSONObject();
        JSONObject classRanges = new JSONObject();
        classRanges.put(BaseParser.CLASS_RANGES_RANGE_FORMAT, "gc-c");
        classRanges.put(BaseParser.CLASS_RANGES_SINGLE_FORMAT, "gc");
        classRanges.put(BaseParser.CLASS_RANGES_CLASS_REGEX, "\\w");
        classRanges.put(BaseParser.CLASS_RANGES_GRADE_REGEX, "\\d+");
        data.put(BaseParser.PARAM_CLASS_RANGES, classRanges);

        assertEquals(new HashSet<>(Arrays.asList("10a", "10b", "10c", "10d")),
                BaseParser.handleClassRanges("10a-d", data));
        assertEquals(new HashSet<>(Collections.singletonList("10a")),
                BaseParser.handleClassRanges("10a", data));

        classRanges.put(BaseParser.CLASS_RANGES_RANGE_FORMAT, "gc-gc");
        assertEquals(new HashSet<>(Arrays.asList("10a", "10b", "10c", "10d")),
                BaseParser.handleClassRanges("10a-10d", data));

        classRanges.put(BaseParser.CLASS_RANGES_CLASS_REGEX, "\\d");
        classRanges.put(BaseParser.CLASS_RANGES_RANGE_FORMAT, "g/c-g/c");
        classRanges.put(BaseParser.CLASS_RANGES_SINGLE_FORMAT, "g/c");
        assertEquals(new HashSet<>(Arrays.asList("10/1", "10/2", "10/3", "10/4")),
                BaseParser.handleClassRanges("10/1-10/4", data));
    }

    @Test
    public void testLoadLocalFiles() throws IOException, CredentialInvalidException {
        // create in-memory file system
        FileSystem fs = Jimfs.newFileSystem(Configuration.builder(PathType.unix())
                .setRoots("/")
                .setWorkingDirectory("/")
                .setAttributeViews("basic")
                .setSupportedFeatures(Feature.SECURE_DIRECTORY_STREAM, Feature.FILE_CHANNEL)
                .build());

        Files.write(fs.getPath("test.txt"), "test".getBytes());

        SubstitutionScheduleData scheduleData = new SubstitutionScheduleData();
        scheduleData.setData(new JSONObject());
        BaseParser parser = new UntisMonitorParser(scheduleData, null);
        parser.setLocalSource(fs.getPath("/"));
        assertEquals("test", parser.httpGet("local://test.txt"));
    }
}
