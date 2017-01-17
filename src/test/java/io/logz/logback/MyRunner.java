package io.logz.logback;

import java.util.concurrent.CountDownLatch;

/**
 * This is for exception stack trace. Don't change any line here otherwise the test will fail
 */
public class MyRunner implements Runnable {
    final CountDownLatch countDownLatch;
    final ExceptionGenerator exceptionGenerator;

    public MyRunner(CountDownLatch countDownLatch, ExceptionGenerator exceptionGenerator) {
        this.countDownLatch = countDownLatch;
        this.exceptionGenerator = exceptionGenerator;
    }

    public void run() {
        exceptionGenerator.generateNPE();
        countDownLatch.countDown();
    }

    public static class ExceptionGenerator {
        Exception e;


        public void generateNPE() {
            String v = null;
            try {

                try {
                    if (1 == 1) v.toString();
                } catch (NullPointerException e) {
                    throw new RuntimeException("Got NPE!", e);
                }
            } catch (Exception e) {
                this.e = e;
            }

        }

        public Exception getE() {
            return e;
        }
    }
}
