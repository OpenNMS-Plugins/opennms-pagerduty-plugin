# OpenNMS PagerDuty Plugin [![CircleCI](https://circleci.com/gh/OpenNMS-Plugins/opennms-pagerduty-plugin.svg?style=svg)](https://circleci.com/gh/OpenNMS-Plugins/opennms-pagerduty-plugin)


![arch](assets/pd-alerts.png "PagerDuty Integration")

## 🗺️ Overview

This plugin allows you to create PagerDuty incidents that correspond to alarms in OpenNMS by integrating with the [Events API v2](https://developer.pagerduty.com/docs/events-api-v2/overview/).

OpenNMS alarms map naturally to incidents in PagerDuty:
* Events create incidents
* Events are deduplicated by the `dedup_key` (or `reduction key` in OpenNMS)
* Incidents can be acknowledged while someone is working on the problem
* Incidents are cleared when there is no longer a problem

This plugin is compatible with OpenNMS Horizon 26.1.3 or higher.

## 🕹️ Installation

Download the latest release with
```shell
mkdir pager-duty-plugin && cd pager-duty-plugin
wget https://github.com/OpenNMS/opennms-pagerduty-plugin/releases/latest/download/opennms-pagerduty-plugin.tar.gz
```

Extract the archive and verify the checksum
```shell
tar xzf opennms-pagerduty-plugin.tar.gz
shasum -c shasum256.txt
```

Copy the kar file to your deploy folder
```shell
cp opennms-pagerduty-plugin-*.kar ${OPENNMS_HOME}/deploy
```

Configure the plugin to be installed when OpenNMS starts:
```shell
echo 'opennms-plugins-pagerduty wait-for-kar=opennms-pagerduty-plugin' | sudo tee ${OPENNMS_HOME}/etc/featuresBoot.d/pagerduty.boot
```

Access the [Karaf shell](https://opennms.discourse.group/t/karaf-cli-cheat-sheet/149) and install the feature manually to avoid having to restart:
```shell
feature:install opennms-plugins-pagerduty
``` 

## 👩‍🔧 Configuration

Configure global options (affects all services for this instance):
```shell
config:edit org.opennms.plugins.pagerduty
property-set client OpenNMS
property-set alarmDetailsUrlPattern 'http://"YOUR-OPENNMS-IP-ADDRESS"/opennms/alarm/detail.htm?id=%d'
config:update
```

> [!NOTE]
> Use the IP address of your OpenNMS server (e.g., 127.0.0.1:8980).

Configure services:
```shell
config:edit --alias core --factory org.opennms.plugins.pagerduty.services
property-set routingKey "YOUR-INTEGRATION-KEY-HERE"
config:update
```

> [!NOTE]
> Use the value of the "Integration Key" as the `routingKey` in the service integrations. Use a JEXL expression to filter the types of notifcations you receive. For example,`property-set jexlFilter 'alarm.reductionKey =~ ".*trigger.*"'` will forward only alarms with the label "trigger" to PagerDuty. No alarms will forward to PagerDuty until a JEXL expression is configured.   

The plugin supports handling multiple services simultaneously - use a different `alias` for each of these when configuring.

## 🎚️ Alarm filtering

We currently only support JEXL expressions for controlling which alarms get forwarded to a given service.

You can use the `opennms-pagerduty:eval-jexl` command to help test expressions before committing them to configuration i.e.:
```shell
admin@opennms> opennms-pagerduty:eval-jexl 'alarm.reductionKey =~ ".*trigger.*"'
No alarms matched (out of 1 alarms.)
admin@opennms> opennms-pagerduty:eval-jexl 'alarm.reductionKey =~ ".*"'
MATCHED: ImmutableAlarm{reductionKey='uei.opennms.org/devjam/2020/minecraft/playerEnteredZone:mousebar:x_intercept', id=109, node=null, managedObjectInstance='null', managedObjectType='zone', type=null, severity=WARNING, attributes={}, relatedAlarms=[], logMessage='x_intercept has entered mousebar.', description='x_intercept has entered mousebar.', lastEventTime=2020-07-30 16:31:26.904, firstEventTime=2020-07-30 16:31:26.904, lastEvent=ImmutableDatabaseEvent{uei='uei.opennms.org/devjam/2020/minecraft/playerEnteredZone', id=195, parameters=[ImmutableEventParameter{name='zone', value='mousebar'}, ImmutableEventParameter{name='player', value='x_intercept'}]}, acknowledged=false}
```

### JEXL Expression Examples

The OpenNMS PagerDuty integration leverages [Apache Commons JEXL Syntax](https://commons.apache.org/proper/commons-jexl/reference/syntax.html) to allow filtering the alarms that get passed to PagerDuty.

Each expression will have a single `alarm` variable set, which is an [Alarm](https://github.com/OpenNMS/opennms-integration-api/blob/master/api/src/main/java/org/opennms/integration/api/v1/model/Alarm.java) object with details about the alarm. If the expression evaluates to `true`, then a PagerDuty event is created for this alarm.

#### All alarms for a nodes with "Test" and "Servers" categories assigned

```shell
admin@opennms> property-set jexlFilter '"Servers" =~ alarm.node.categories and "Test" =~ alarm.node.categories'
```

This leverages the `=~` operator to mean "'Servers' is in the alarm's node's categories" and "'Test' is in the alarm's node's categories".

#### Excluding some alarms from the above

```shell
admin@opennms> property-set jexlFilter '"Servers" =~ alarm.node.categories and "Test" =~ alarm.node.categories and alarm.reductionKey !~ "^uei\.opennms\.org/generic/traps/SNMP_Authen_Failure:.*"'
```

This leverages the `!~` operator to mean "the alarm reduction key does not match the given regex", in addition to the "in" sense of `=~` as shown above.

#### Only Alarms That Can Auto-Resolve

```shell
admin@opennms> property-set jexlFilter '"Servers" =~ alarm.node.categories and "Test" =~ alarm.node.categories and alarm.type.name == "PROBLEM"'
```

This limits to only alarms for certain categories of nodes that have a resolution. Some alarms have no "clearing" event, so they would stay present in PagerDuty forever unless manual action is taken, or certain special configuration is used within PagerDuty to expire the events.

### Hold-Down Timer (Delayed Notifications)

Some alarms may quickly resolve themselves, especially occasional brief outages from the OpenNMS service pollers.

To be able to get the full benefits of the OpenNMS [Downtime Model](https://docs.opennms.org/opennms/releases/latest/guide-admin/guide-admin.html#ga-service-assurance-downtime-model),
you can specify a hold-down timer of at least a few minutes, to trade off instant notification of issues for
reduced false positives (issues that resolved themselves before you were able to look at them).

Similar to configuring the `jexlFilter` above, you can edit a specific service's configuration. To find
the specific configuration to edit, use `config:list` as shown below, then use `config:edit` to edit the Pid of that specific
service's configuration:

```shell
admin@opennms> config:list '(service.factoryPid=org.opennms.plugins.pagerduty.services)'
----------------------------------------------------------------
Pid:            org.opennms.plugins.pagerduty.services.bbc99bb4-bc56-4d56-b35c-14066b6e2dcf
FactoryPid:     org.opennms.plugins.pagerduty.services
BundleLocation: ?
Properties:
   felix.fileinstall.filename = file:/opt/opennms/etc/org.opennms.plugins.pagerduty.services-test-servers.cfg
   jexlFilter = "Servers" =~ alarm.node.categories and "Test" =~ alarm.node.categories and alarm.type.name == "PROBLEM"
   routingKey = YOUR-INTEGRATION-KEY-HERE
   service.factoryPid = org.opennms.plugins.pagerduty.services
   service.pid = org.opennms.plugins.pagerduty.services.bbc99bb4-bc56-4d56-b35c-14066b6e2dcf
admin@opennms> config:edit org.opennms.plugins.pagerduty.services.bbc99bb4-bc56-4d56-b35c-14066b6e2dcf
admin@opennms> property-set holdDownDelay "PT5M"
admin@opennms> config:update
```

The `holdDownDelay` property should be a string that follows ISO-8601 duration format, as supported
by [`java.time.Duration.parse()`](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-).

### Handling notification failures

In cases where forwarding an alarm to PagerDuty fails, the plugin will generate a `uei.opennms.org/pagerduty/sendEventFailed` locally that will trigger an alarm.
The event contains details on the error that occurred. You could use it to trigger alternate notification mechanisms.

## 👩‍🔬 Developing

Build and install the plugin into your local Maven repository using:
```shell
make
```
> [!TIP]
> OpenNMS normally runs as root, so make sure the artifacts are installed in `/root/.m2` or try making `/root/.m2` symlink to your user's repository.
> The kar file is created in the `assembly/kar/target` directory.

From the OpenNMS Karaf shell:
```shell
feature:repo-add mvn:org.opennms.plugins/pagerduty-karaf-features/<MAVEN-POM-VERSION>/xml
feature:install opennms-plugins-pagerduty
```
> [!NOTE]
> Replace <MAVEN-POM-VERSION> with the version number from the pom.xml in your current working branch.

Update automatically:
```shell
bundle:watch *
```

### Debugging

Add the following lines to `$OPENNMS_HOME/etc/org.ops4j.pax.logging.cfg`:
```shell
# PagerDuty plugin
log4j2.logger.pd-plugin.name = org.opennms.integrations.pagerduty
log4j2.logger.pd-plugin.level = DEBUG
log4j2.logger.pd-client.name = org.opennms.pagerduty
log4j2.logger.pd-client.level = DEBUG
```
