/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.integrations.pagerduty;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.Severity;
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm;
import org.opennms.integration.api.v1.model.immutables.ImmutableNode;

/**
 * Verifies that filter expressions can inspect the Integration API model objects.
 * JEXL 3.3+ hides them by default, which fails with "undefined property".
 */
public class JexlFilterTest {

    private static final String LOW = "alarm.node.foreignSource == \"Requisition-A\" and (alarm.severity.id == 6 or alarm.severity.id == 2)";
    private static final String HIGH = "alarm.node.foreignSource == \"Requisition-A\" and (alarm.severity.id == 7 or alarm.severity.id == 2)";

    @Test
    public void canAccessAlarmProperties() {
        Alarm alarm = alarm("Requisition-A", Severity.MAJOR);
        assertTrue(matches("alarm.reductionKey =~ \"^uei\\.opennms\\.org/nodes/nodeDown:.*\"", alarm));
        assertTrue(matches("alarm.severity == \"MAJOR\"", alarm));
        assertTrue(matches("alarm.getSeverity() != null", alarm));
        assertTrue(matches("alarm.severity.id >= 6", alarm));
        assertTrue(matches("alarm.node.foreignSource == \"Requisition-A\"", alarm));
        assertTrue(matches("alarm.node.foreignSource =~ [\"Requisition-A\", \"Requisition-B\"]", alarm));
        assertTrue(matches("\"Servers\" =~ alarm.node.categories", alarm));
        assertFalse(matches("alarm.node.foreignSource == \"Requisition-B\"", alarm));
    }

    @Test
    public void canRouteBySeverity() {
        assertFalse(matches(LOW, alarm("Requisition-A", Severity.WARNING)));
        assertFalse(matches(HIGH, alarm("Requisition-A", Severity.WARNING)));

        assertTrue(matches(LOW, alarm("Requisition-A", Severity.MAJOR)));
        assertFalse(matches(HIGH, alarm("Requisition-A", Severity.MAJOR)));

        assertFalse(matches(LOW, alarm("Requisition-A", Severity.CRITICAL)));
        assertTrue(matches(HIGH, alarm("Requisition-A", Severity.CRITICAL)));

        // Clears must pass so the incident gets resolved
        assertTrue(matches(LOW, alarm("Requisition-A", Severity.CLEARED)));
        assertTrue(matches(HIGH, alarm("Requisition-A", Severity.CLEARED)));

        assertFalse(matches(HIGH, alarm("Requisition-B", Severity.CRITICAL)));
    }

    @Test
    public void doesNotMatchOnErrors() {
        Alarm alarmWithoutNode = ImmutableAlarm.newBuilder()
                .setId(2)
                .setReductionKey("uei.opennms.org/generic/traps/SNMP_Cold_Start")
                .setSeverity(Severity.CRITICAL)
                .build();
        assertFalse(matches("alarm.node.foreignSource == \"Requisition-A\"", alarmWithoutNode));
        // Non-boolean result
        assertFalse(matches("alarm.reductionKey", alarmWithoutNode));
        // Unknown property
        assertFalse(matches("alarm.noSuchProperty == \"x\"", alarmWithoutNode));
    }

    private static boolean matches(String expression, Alarm alarm) {
        return PagerDutyForwarder.testAlarmAgainstExpression(PagerDutyForwarder.createExpression(expression), alarm);
    }

    private static Alarm alarm(String foreignSource, Severity severity) {
        Node node = ImmutableNode.newBuilder()
                .setId(1)
                .setForeignSource(foreignSource)
                .setForeignId("1")
                .setLabel("node1")
                .setLocation("Default")
                .setCategories(Arrays.asList("Servers", "Production"))
                .build();
        return ImmutableAlarm.newBuilder()
                .setId(1)
                .setReductionKey("uei.opennms.org/nodes/nodeDown::1")
                .setSeverity(severity)
                .setNode(node)
                .build();
    }
}
