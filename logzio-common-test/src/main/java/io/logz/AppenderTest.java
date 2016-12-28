package io.logz;

import org.junit.Test;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Created by MarinaRazumovsky on 28/12/2016.
 */
public abstract class AppenderTest extends SenderTest {

    @Test
    public void validateAdditionalFields() throws Exception {
        String token = "validatingAdditionalFields";
        String type = "willTryWithOrWithoutEnvironmentVariables";
        String loggerName = "additionalLogger";
        int drainTimeout = 1;

        String message1 = "Just a log - " + random(5);
        Map<String,String> additionalFields = new HashMap<>();

        String additionalFieldsString = "java_home=$JAVA_HOME;testing=yes;message=override";
        additionalFields.put("java_home", System.getenv("JAVA_HOME"));
        additionalFields.put("testing", "yes");

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,false, additionalFieldsString,30 , port);


        Optional level1 = testWrapper.info(loggerName, message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, level1);
        assertAdditionalFields(logRequest, additionalFields);
    }

    @Test
    public void existingHostname() throws Exception {
        String token = "checkingHostname";
        String type = "withOrWithoutHostnamr";
        String loggerName = "runningOutOfIdeasHere";
        int drainTimeout = 1;

        String message1 = "Hostname log - " +  random(5);

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,true, "",30 , port);

        Optional level1 = testWrapper.info(loggerName, message1);

        // Sleep double time the drain timeout
        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, level1);

        String hostname = InetAddress.getLocalHost().getHostName();
        assertThat(logRequest.getHost()).isEqualTo(hostname);
    }

    @Test
    public void testMDC() throws Exception {
        String token = "mdcTokensAreTheBest";
        String type = "mdcType";
        String loggerName = "mdcTesting";
        int drainTimeout = 1;

        String message1 = "Simple log line - "+random(5);

        String mdcKey = "mdc-key";
        String mdcValue = "mdc-value";

        Map<String, String> mdcKv = new HashMap<>();
        mdcKv.put(mdcKey, mdcValue);
        mdcKv.put("logger", "Doesn't matter");

        MDC.put(mdcKey, mdcValue);

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,false, "",30 , port);

        Optional level1 = testWrapper.info(loggerName, message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, level1);

        assertThat(logRequest.getStringFieldOrNull(mdcKey)).isEqualTo(mdcValue);
    }

    @Test
    public void testMarker() throws Exception {
        String token = "markerToken";
        String type = "markerType";
        String loggerName = "markerTesting";

        String markerKey = "marker";
        String markerTestValue = "MyMarker";

        int drainTimeout = 1;

        String message1 = "Simple log line - "+random(5);

        Marker marker = MarkerFactory.getMarker(markerTestValue);

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,false, "",30 , port);

        Optional level1 = testWrapper.info(loggerName, marker, message1);

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName, level1);

        assertThat(logRequest.getStringFieldOrNull(markerKey)).isEqualTo(markerTestValue);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void sendException() throws Exception {
        String token = "checkingExceptions";
        String type = "badType";
        String loggerName = "exceptionProducer";
        int drainTimeout = 1;
        Throwable exception = null;

        String message1 = "This is not an int..";

        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,false, "",30 , port);

        Optional level1 = Optional.empty();

        try {
            Integer.parseInt(message1);
        } catch (Exception e) {
            exception = e;
            level1 = testWrapper.info(loggerName, message1, e);
        }
        assertThat(exception).isNotNull();

        sleepSeconds(2 * drainTimeout);

        assertNumberOfReceivedMsgs(1);
        MockLogzioBulkListener.LogRequest logRequest = assertLogReceivedByMessage(message1);
        assertLogReceivedIs(logRequest, token, type, loggerName,level1);

        String exceptionField = logRequest.getStringFieldOrNull("exception");
        if (exceptionField == null) fail("Exception field does not exists");

        assertThat(exceptionField.replace("\\", "")).contains(exception.getMessage());
    }




}
