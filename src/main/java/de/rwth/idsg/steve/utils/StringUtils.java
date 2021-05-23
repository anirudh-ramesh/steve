/*
 * SteVe - SteckdosenVerwaltung - https://github.com/RWTH-i5-IDSG/steve
 * Copyright (C) 2013-2021 RWTH Aachen University - Information Systems - Intelligent Distributed Systems Group (IDSG).
 * All Rights Reserved.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.utils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import de.rwth.idsg.steve.ocpp.CommunicationTask;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * @author Sevket Goekay <goekay@dbis.rwth-aachen.de>
 * @since 12.01.2015
 */
public final class StringUtils {

    private static final Splitter SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();
    private static final Joiner JOINER = Joiner.on(",").skipNulls();

    private StringUtils() { }

    /**
     * We don't want to hard-code operation names,
     * but derive them from the actual request object.
     *
     * Example for "ChangeAvailabilityTask":
     * - Remove "Task" at the end -> "ChangeAvailability"
     * - Insert space -> "Change Availability"
     */
    public static String getOperationName(CommunicationTask task) {
        String s = task.getClass().getSimpleName();

        if (s.endsWith("Task")) {
            s = s.substring(0, s.length() - 4);
        }

        // http://stackoverflow.com/a/4886141
        s = s.replaceAll("(\\p{Ll})(\\p{Lu})", "$1 $2");

        return s;
    }

    @Nullable
    public static String joinByComma(Collection<?> col) {
        if (col == null || col.isEmpty()) {
            return null;
        } else {
            // Use HashSet to trim duplicates
            return JOINER.join(new HashSet<>(col));
        }
    }

    public static List<String> splitByComma(String str) {
        if (str == null || str.isEmpty()) {
            return Collections.emptyList();
        } else {
            return SPLITTER.splitToList(str);
        }
    }
}
