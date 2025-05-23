[![Build Status](https://travis-ci.org/logzio/logzio-logback-appender.svg?branch=master)](https://travis-ci.org/logzio/logzio-logback-appender)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.logz.logback/logzio-logback-appender/badge.svg)](http://mvnrepository.com/artifact/io.logz.logback/logzio-logback-appender)

# Logzio logback appender
This appender sends logs to your [Logz.io](http://logz.io) account, using non-blocking threading, bulks, and HTTPS encryption. Please note that this appender requires logback version 1.1.7 and above, and from version 2.0.0, java 11 and up.

### Technical Information
This appender uses [LogzioSender](https://github.com/logzio/logzio-java-sender) implementation. All logs are backed up to a local file system before being sent. Once you send a log, it will be enqueued in the queue and 100% non-blocking. There is a background task that will handle the log shipment for you. This jar is an "Uber-Jar" that shades both BigQueue, Gson and Guava to avoid "dependency hell".

### Installation from maven

JDK 11 and above:
```
<dependency>
    <groupId>io.logz.logback</groupId>
    <artifactId>logzio-logback-appender</artifactId>
    <version>2.4.0</version>
</dependency>
```


JDK 8 and above:
```
<dependency>
    <groupId>io.logz.logback</groupId>
    <artifactId>logzio-logback-appender</artifactId>
    <version>1.0.29</version>
</dependency>
```

Logback appender also requires logback classic:
```
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.18</version>
</dependency>
```

### logback.xml Example Configuration
```xml
<!-- Use debug=true here if you want to see output from the appender itself -->
<!-- Use line=true here if you want to see the line of code that generated this log -->
<configuration>
    <!-- Use shutdownHook so that we can close gracefully and finish the log drain -->
    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>
    <appender name="LogzioLogbackAppender" class="io.logz.logback.LogzioLogbackAppender">
        <token>yourlogziopersonaltokenfromsettings</token>
        <logzioType>myAwesomeType</logzioType>
        <logzioUrl>https://listener.logz.io:8071</logzioUrl>
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>INFO</level>
        </filter>
    </appender>
    <root level="debug">
        <!-- IMPORTANT: This line is required -->
        <appender-ref ref="LogzioLogbackAppender"/>
    </root>
</configuration>
```

### Advanced Configuration: Custom Executor Example

For advanced control over the background threads used for sending logs, you can provide your own `ScheduledExecutorService`. This allows tuning thread pool size, thread factory, etc.

```xml
<configuration>
    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>

    <appender name="LogzioLogbackAppender" class="io.logz.logback.LogzioLogbackAppender">
        <token>yourlogziopersonaltokenfromsettings</token>
        <logzioType>myAdvancedType</logzioType>
        <logzioUrl>https://listener.logz.io:8071</logzioUrl>

        <executor class="java.util.concurrent.ScheduledThreadPoolExecutor">
            <corePoolSize>3</corePoolSize> <threadFactory class="com.google.common.util.concurrent.ThreadFactoryBuilder">
                <daemon>true</daemon>
                <nameFormat>my-logzio-sender-%d</nameFormat>
            </threadFactory>
        </executor>

        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>INFO</level>
        </filter>
    </appender>

    <root level="debug">
        <appender-ref ref="LogzioLogbackAppender"/>
    </root>
</configuration>

Note: When providing a custom <executor>, you are responsible for ensuring its configuration is valid. If configured via XML as shown, Logback typically handles the executor's shutdown. The appender itself will not shut down an executor provided to it via this configuration method.

### Parameters
| Parameter                   | Default                         | Explained                                                                                                                                                                                                                                                                                                                                                                                                                 |
|-----------------------------|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **token**                   | *None*                          | Your Logz.io token, which can be found under "settings" in your account, If the value begins with `$` then the appender looks for an environment variable or system property with the name specified. For example: `$LOGZIO_TOKEN` will look for environment variable named `LOGZIO_TOKEN`                                                                                                                                |
| **logzioType**              | *java*                          | The [log type](https://docs.logz.io/user-guide/log-shipping/built-in-log-types.html) for that appender, it must not contain spaces                                                                                                                                                                                                                                                                                        |
| **logzioUrl**               | *https://listener.logz.io:8071* | The url that the appender sends to.  If your account is in the EU you must use https://listener-eu.logz.io:8071                                                                                                                                                                                                                                                                                                           |
| **drainTimeoutSec**         | *5*                             | How often the appender should drain the queue (in seconds)                                                                                                                                                                                                                                                                                                                                                                |
| **socketTimeout**           | *10 * 1000*                     | The socket timeout during log shipment                                                                                                                                                                                                                                                                                                                                                                                    |
| **connectTimeout**          | *10 * 1000*                     | The connection timeout during log shipment                                                                                                                                                                                                                                                                                                                                                                                |
| **addHostname**             | *false*                         | Optional. If true, then a field named 'hostname' will be added holding the host name of the machine. If from some reason there's no defined hostname, this field won't be added                                                                                                                                                                                                                                           |
| **additionalFields**        | *None*                          | Optional. Allows to add additional fields to the JSON message sent. The format is "fieldName1=fieldValue1;fieldName2=fieldValue2". You can optionally inject an environment variable value using the following format: "fieldName1=fieldValue1;fieldName2=$ENV_VAR_NAME". In that case, the environment variable should be the only value. In case the environment variable can't be resolved, the field will be omitted. |
| **addOpentelemetryContext** | *true*                          | Optional. Add `trace_id`, `span_id`, `service_name` fields to logs when opentelemetry context is available.                                                                                                                                                                                                                                                                                                               |
| **debug**                   | *false*                         | Print some debug messages to stdout to help to diagnose issues                                                                                                                                                                                                                                                                                                                                                            |
| **`<executor>` (tag)** | *None* (Uses Logback default)   | Optional. Allows specifying a custom `java.util.concurrent.ScheduledExecutorService` implementation (e.g., `ScheduledThreadPoolExecutor`) via nested XML tags for background log sending tasks. See advanced configuration example above. |
| **line**                    | *false*                         | Print the line of code that generated this log                                                                                                                                                                                                                                                                                                                                                                            |
| **compressRequests**        | *false*                         | Boolean. `true` if logs are compressed in gzip format before sending. `false` if logs are sent uncompressed.                                                                                                                                                                                                                                                                                                              |
| **format**                  | *text*                          | Optional. `json` if the logged message is to be parsed as a JSON (in such a way that each JSON node will be a field in logz.io) or `text` if the logged message is to be treated as plain text.                                                                                                                                                                                                                           |
| **exceedMaxSizeAction**     | *"cut"*                         | String. cut to truncate the message field or drop to drop log that exceed the allowed maximum size for logzio. If the log size exceeding the maximum size allowed after truncating the message field, the log will be dropped.                                                                                                                                                                                            |
#### Parameters for in-memory queue
| Parameter                      | Default             | Explained                                                                                                                                         |
|--------------------------------|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| **inMemoryQueueCapacityBytes** | *1024 * 1024 * 100* | The amount of memory(bytes) we are allowed to use for the memory queue. If the value is -1 the sender will not limit the queue size.              |
| **inMemoryLogsCountCapacity**  | *-1*                | Number of logs we are allowed to have in the queue before dropping logs. If the value is -1 the sender will not limit the number of logs allowed. |
| **inMemoryQueue**              | *false*             | Set to true if the appender uses in memory queue. By default the appender uses disk queue                                                         |


#### Parameters for disk queue
| Parameter                                | Default                                | Explained                                                                                                                                                                                                                                                                                        |
|------------------------------------------|----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **fileSystemFullPercentThreshold**       | *98*                                   | The percent of used file system space at which the sender will stop queueing. When we will reach that percentage, the file system in which the queue is stored will drop all new logs until the percentage of used space drops below that threshold. Set to -1 to never stop processing new logs |
| **gcPersistedQueueFilesIntervalSeconds** | *30*                                   | How often the disk queue should clean sent logs from disk                                                                                                                                                                                                                                        |
| **bufferDir**(deprecated, use queueDir)  | *System.getProperty("java.io.tmpdir")* | Where the appender should store the queue                                                                                                                                                                                                                                                        |
| **queueDir**                             | *System.getProperty("java.io.tmpdir")* | Where the appender should store the queue                                                                                                                                                                                                                                                        |


### Code Example
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogzioLogbackExample {

    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(LogzioLogbackExample.class);
        
        logger.info("Testing logz.io!");
        logger.warn("Winter is coming");
    }
}
```

### MDC
Each key value you will add to MDC will be added to each log line as long as the thread alive. No further configuration needed.
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class LogzioLogbackExample {

    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(LogzioLogbackExample.class);

        MDC.put("Key", "Value");
        logger.info("This log will hold the MDC data as well");
    }
}
```

Will send a log to Logz.io that looks like this:
```
{
    "message": "This log will hold the MDC data as well",
    "Key": "Value",
    ... (all other fields you used to get)
}
```

### Marker
Markers are named objects used to enrich log statements, so each log line will be enriched with its own. No further configuration needed.
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

public class LogzioLogbackExample {

    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(LogzioLogbackExample.class);

        Marker marker = MarkerFactory.getMarker("Fatal");
        logger.error(marker, "This line has a fatal error");
    }
}
```

Will send a log to Logz.io that looks like this:
```
{
    "message": "This line has a fatal error",
    "Marker": "Fatal",
    ... (all other fields you used to get)
}
```

## Add opentelemetry context
If you're sending traces with OpenTelemetry instrumentation (auto or manual), you can correlate your logs with the trace context. That way, your logs will have traces data in it, such as service name, span id and trace id (version >= `2.2.0`). This feature is enabled by default, To disable it, set the `addOpentelemetryContext` option in your configuration to `false`, like in this example:

```xml
<configuration>
   <appender name="LogzioLogbackAppender" class="io.logz.logback.LogzioLogbackAppender">
      <token>yourlogziopersonaltokenfromsettings</token>
      <logzioType>myAwesomeType</logzioType>
      <logzioUrl>https://listener.logz.io:8071</logzioUrl>
      <addOpentelemetryContext>false</addOpentelemetryContext>
   </appender>
   <root level="debug">
      <!-- IMPORTANT: This line is required -->
      <appender-ref ref="LogzioLogbackAppender"/>
   </root>
</configuration>
```

## Build and test locally
1. Clone the repository:
  ```bash
  git clone https://github.com/logzio/logzio-logback-appender.git
  cd logzio-logback-appender
  ```
2. Build and run tests:
  ```bash
  mvn clean compile
  mvn test
  ```

### Release notes
- 2.4.0
  - Updated LogzioSender version to `2.3.0`
    - Upgrade dependencies
  - Upgrade dependencies
 - 2.3.0
   - Added `<executor>` configuration option to allow specifying a custom `ScheduledExecutorService` for background log sending tasks.

 - 2.2.0
    -  Updated LogzioSender version to `2.2.0`
        - Add `addOpentelemetryContext` option, to add `trace_id`, `span_id`, `service_name` fields to logs when opentelemetry context is available.
- 2.1.0
  - Updated LogzioSender version to 2.1.0
    - Upgrade packages version
  - Upgrade packages version
- 2.0.1
   -  Updated LogzioSender version to `2.0.1`
      - Add `User-Agent` header with logz.io information
 - 2.0.0 - **THIS IS A SNAPSHOT RELEASE - SUPPORTED WITH JDK 11 AND ABOVE**
   - Updated LogzioSender version to `2.0.0`:
     - Fixes an issue where DiskQueue was not clearing disk space when using JDK 11 and above.
 - 1.0.29
   - Updated LogzioSender version to `1.1.8`:
      - Fix an issue where log is not being truncated properly between size of 32.7k to 500k.
   
 <details>
  <summary markdown="span"> Expand to check old versions </summary>

 - 1.0.28
   - Added exceedMaxSizeAction parameter for handling oversized logs
   - Updated LogzioSender version, fixing IndexOutOfBounds error with bigqueue 
 - 1.0.27
   - Dependency version bump
   - Reverted invalid maven shade configuration
 - 1.0.25
   - added ability to flush sender manually
 - 1.0.24
   - shade some dependencies
 - 1.0.22 - 1.0.23
   - update logzio-sender version
 - 1.0.21
   - added in memory queue support
 - 1.0.19: 
   - added json message format support
 - 1.0.18
   - added `compressRequests` parameter to enable gzip compression of the logs before they are sent.
   - added option to inject system property value into additionalFields, logzioUrl and token.
 - 1.0.16 - 1.0.17
   - added `line` parameter to enable printing the line of code that generated this log
 - 1.0.15 - 1.0.16
   - add error message about reason of 400(BAD REQUEST)
 - 1.0.1 - 1.0.14
   - Separate LogzioSender to independent project, add dependency on logzio-sender 
 - 1.0.12
   - Add Marker support
 - 1.0.11
   - Add environment variables support to `token` and `logzioUrl` parameters
 - 1.0.10
   - Replace task executor in case of old executor termination
 - 1.0.9
   - Fixed an issue preventing the appender to restart if asked. Also, prevented hot-loading of logback config.xml
 - 1.0.8
   - Fixed filesystem percentage wrong calculation (#12)
 - 1.0.7
   - Create different buffers for different types
   - Moved LogzioSender class to be a factory, by log type - meaning no different configurations for the same type (which does not make sense anyway)
 - 1.0.6
   - Fix: Appender can get into dead-lock thus causing all threads logging to bloc
   - Refactored all Unit tests 
   - Switched to maven wrapper for build consistency
 - 1.0.5
   - Add MDC support
   - Replace exception handling to use Logback own instead of implementing alone
   - Periodically calls BigQueue GC function so we can release files on local disk
 - 1.0.4
   - If you logged a throwable as well, we will put it inside a field named "exception"
 - 1.0.3
   - Addional fields support
   - Add hostname to logs support
 - 1.0.0 - 1.0.2
   - Initial releases
      </details>

### Contribution
 - Fork
 - Code
 - ```mvn test```
 - Issue a PR :)
