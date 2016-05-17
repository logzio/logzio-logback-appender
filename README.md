# Logzio logback appender
This appender send logs to your [Logz.io](http://logz.io) account, using non blocking threading, bulks, and HTTPs encryption.

### Technical
This appender uses [BigQueue](https://github.com/bulldog2011/bigqueue) implementation of persistent queue, so all logs are backed up to local FS before being sent. Once you send a log, it will be enqueued in the buffer so it will be 100% non blocking. There is a background task that will handle the log shipment for you.

### Installation from maven
```xml
<dependency>
    <groupId>io.logz.logbackAppender</groupId>
    <artifactId>logzio-logback-appender</artifactId>
    <version>1.0</version>
</dependency>
```

### Logback Example Configuration
```xml
<configuration debug="true" scan="true" scanPeriod="30 seconds">
    <appender name="LogzioLogbackAppender" class="io.logz.logback.LogzioLogbackAppender">
        <encoder>
            <pattern>%d{yy/MM/dd HH:mm:ss} {%t} %p %c{2}: %m</pattern>
        </encoder>
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>DEBUG</level>
        </filter>
        <token>yourlogziopersonaltokenfromsettings</token>
        <type>myAwesomeType</type>
    </appender>
</configuration>
```

### Parameters
| Parameter          | Default                              | Explained  |
| ------------------ | ------------------------------------ | ----- |
| **token**              | *None*                                 | Your logz.io token, can be found under "settings" in your account |
| **type**               | *java*                                 | What is the [log type](http://support.logz.io/support/solutions/articles/6000103063-what-is-type-) for that appender |
| **drainTimeout**       | *5*                                    | Once in how long we should drain the buffer (in seconds) |
| **fsPercentThreshold** | *98*                                   | The precent of used FS space, to stop buffering. When we will reach that mark, on the FS that the buffer is in we will drop all new logs until the FS is dopping below that threhsold. set to -1 to never stop processing new logs |
| **bufferDir**          | *System.getProperty("java.io.tmpdir")* | Where we should store the buffer |
