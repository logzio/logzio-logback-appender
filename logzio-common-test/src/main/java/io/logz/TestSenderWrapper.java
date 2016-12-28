package io.logz;

import org.slf4j.Marker;

import java.util.Optional;

/**
 * Created by MarinaRazumovsky on 27/12/2016.
 */
public  interface TestSenderWrapper {

    Optional info(String loggerName, String message);
    Optional info(String loggerName, String message, Throwable exc);
    Optional warn(String loggerName, String message);
    Optional error(String loggerName, String message);

    public Optional info(String loggerName, Marker market, String message);

    void stop();


}
