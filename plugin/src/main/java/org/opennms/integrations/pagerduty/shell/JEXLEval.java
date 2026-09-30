/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2020 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2020 The OpenNMS Group, Inc.
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

package org.opennms.integrations.pagerduty.shell;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.integration.api.v1.dao.AlarmDao;
import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integrations.pagerduty.PagerDutyForwarder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.opennms.pagerduty.client.api.PDEvent;

@Command(scope = "opennms-pagerduty", name = "eval-jexl", description = "Evaluate a JEXL expression for the PagerDuty plugin")
@Service
public class JEXLEval implements Action {

    private static final int SUMMARY_MAX_LENGTH = 1024;
    @Reference
    private AlarmDao alarmDao;

    @Argument(required = true)
    private String expression;

    @Option(name = "-p", aliases = "--topayload", description = "Convert matching alarms to JSON PagerDuty event payloads", required = false, multiValued = false)
    Boolean toPayload = false;

    @Option(name = "-c", aliases = "--count", description = "Only show the number of matching alarms, without alarm data", required = false, multiValued = false)
    Boolean onlyCount = false;

    @Option(name = "-a", aliases = "--alarm-id", description = "Lookup an alarm by its id and evaluate the given expression against it.")
    private Integer alarmId;

    @Override
    public Object execute() throws JsonProcessingException {
        JexlExpression e = PagerDutyForwarder.createExpression(expression);

        boolean alarmIdMatched = false;
        int numAlarmsProcessed = 0;
        boolean didMatchAtLeastOneAlarm = false;
        int matchedAlarmCount = 0;
        int failedAlarmCount = 0;
        String firstFailure = null;

        if (toPayload && onlyCount) {
            System.out.println("Options '-p' and '-c' are mutually exclusive, ignoring '-p' ");
        }

        for (Alarm alarm : alarmDao.getAlarms()) {
            numAlarmsProcessed++;
            boolean didMatch = false;
            String failure = null;
            try {
                didMatch = PagerDutyForwarder.evaluateExpression(e, alarm);
            } catch (PagerDutyForwarder.FilterEvaluationException ex) {
                failure = ex.getMessage();
                failedAlarmCount++;
                if (firstFailure == null) {
                    firstFailure = String.format("alarm ID '%d': %s", alarm.getId(), failure);
                }
            }

            if (alarmId != null && alarm.getId().equals(alarmId)) {
                System.out.printf("Alarm with ID '%d' has reduction key: '%s'\n", alarmId, alarm.getReductionKey());
                alarmIdMatched = true;
                if (failure != null) {
                    System.out.printf("Expression failed to evaluate: %s\n", failure);
                } else {
                    System.out.printf("Expression evaluates: %s\n", didMatch);
                }
            }
            if (didMatch) {
                if (!onlyCount && alarmId == null) {
                    System.out.println("MATCHED: " + alarm);
                }
                if (toPayload && !onlyCount && (alarmId == null || alarm.getId().equals(alarmId))) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    PDEvent pdevent = new PDEvent();
                    Object json = mapper.convertValue(PagerDutyForwarder.createPayload(alarm, pdevent), JsonNode.class);
                    System.out.println("PagerDuty JSON Payload:\n " + mapper.writeValueAsString(json));
                    System.out.println();
                }
                matchedAlarmCount++;
                didMatchAtLeastOneAlarm = true;
            }
        }

        if (failedAlarmCount > 0) {
            System.out.printf("\nExpression failed to evaluate for %d alarms (out of %d alarms.) First failure, %s\n",
                    failedAlarmCount, numAlarmsProcessed, firstFailure);
        }
        if (numAlarmsProcessed < 1) {
            System.out.println("\nNo alarms present.\n");
        } else if (!didMatchAtLeastOneAlarm) {
            System.out.printf("\nNo alarms matched (out of %d alarms.)\n", numAlarmsProcessed);
        } else if (alarmId != null && !alarmIdMatched) {
            System.out.printf("\nNo alarm with ID '%d' was found!\n", alarmId);
        } else if (didMatchAtLeastOneAlarm && matchedAlarmCount > 0) {
            System.out.printf("\nExpression matched %d alarms (out of %d alarms.)\n", matchedAlarmCount, numAlarmsProcessed);
        }
        return null;
    }
}
