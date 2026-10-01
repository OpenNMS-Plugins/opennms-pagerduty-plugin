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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;
import org.opennms.integration.api.v1.config.events.AlarmType;
import org.opennms.integration.api.v1.events.EventForwarder;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Severity;
import org.opennms.integration.api.v1.model.immutables.ImmutableAlarm;
import org.opennms.integration.api.v1.model.immutables.ImmutableDatabaseEvent;
import org.opennms.integration.api.v1.model.immutables.ImmutableEventParameter;
import org.opennms.integration.api.v1.model.immutables.ImmutableIpInterface;
import org.opennms.integration.api.v1.model.immutables.ImmutableNode;
import org.opennms.pagerduty.client.api.PDClient;
import org.opennms.pagerduty.client.api.PDEvent;

public class PagerDutyForwarderTest {

    private static final String REDUCTION_KEY = "uei.opennms.org/nodes/nodeDown::1";

    private final List<PDEvent> sentEvents = new CopyOnWriteArrayList<>();
    private PagerDutyForwarder forwarder;

    @After
    public void tearDown() {
        if (forwarder != null) {
            forwarder.close();
        }
    }

    @Test
    public void resolvesIncidentWhenFilterFailsOnClear() throws Exception {
        // The clear carries no event parameters, so this filter fails to evaluate on it
        forwarder = forwarder("alarm.lastEvent.parameters[0].value == \"timeout\"");

        forwarder.handleNewOrUpdatedAlarm(alarm(Severity.MAJOR, true));
        awaitSentEvents(1);

        forwarder.handleNewOrUpdatedAlarm(alarm(Severity.CLEARED, false));
        forwarder.handleDeletedAlarm(1, REDUCTION_KEY);
        awaitSentEvents(2);

        assertThat(sentActions(), contains("TRIGGER", "RESOLVE"));
    }

    @Test
    public void skipsDeleteForFilteredAlarm() throws Exception {
        forwarder = forwarder("alarm.severity.id >= 7");

        forwarder.handleNewOrUpdatedAlarm(alarm(Severity.MAJOR, true));
        forwarder.handleDeletedAlarm(1, REDUCTION_KEY);
        Thread.sleep(500);

        assertThat(sentEvents, empty());
    }

    private PagerDutyForwarder forwarder(String jexlFilter) {
        PDClient pdClient = mock(PDClient.class);
        when(pdClient.sendEvent(any())).thenAnswer(invocation -> {
            sentEvents.add(invocation.getArgument(0));
            return CompletableFuture.completedFuture(null);
        });
        return new PagerDutyForwarder(mock(EventForwarder.class), () -> pdClient,
                new PagerDutyPluginConfig("OpenNMS", "http://localhost:8980/opennms/alarm/detail.htm?id=%d"),
                new PagerDutyServiceConfig("test", "routing-key", jexlFilter, Duration.ZERO));
    }

    private void awaitSentEvents(int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (sentEvents.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }

    private List<String> sentActions() {
        return sentEvents.stream()
                .map(e -> e.getEventAction().name())
                .collect(Collectors.toList());
    }

    private static Alarm alarm(Severity severity, boolean withParameter) throws UnknownHostException {
        ImmutableDatabaseEvent.Builder lastEvent = ImmutableDatabaseEvent.newBuilder()
                .setId(1L)
                .setUei("uei.opennms.org/nodes/nodeDown");
        if (withParameter) {
            lastEvent.addParameter(ImmutableEventParameter.newBuilder()
                    .setName("reason")
                    .setValue("timeout")
                    .build());
        }
        return ImmutableAlarm.newBuilder()
                .setId(1)
                .setReductionKey(REDUCTION_KEY)
                .setSeverity(severity)
                .setType(AlarmType.PROBLEM)
                .setLogMessage("Node is down")
                .setNode(ImmutableNode.newBuilder()
                        .setId(1)
                        .setForeignSource("Requisition-A")
                        .setForeignId("1")
                        .setLabel("node1")
                        .setLocation("Default")
                        .addIpInterface(ImmutableIpInterface.newBuilder()
                                .setIpAddress(InetAddress.getByName("10.0.0.1"))
                                .build())
                        .build())
                .setLastEvent(lastEvent.build())
                .build();
    }
}
