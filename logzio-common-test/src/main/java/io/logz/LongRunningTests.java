package io.logz;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class LongRunningTests extends BaseTest {

    private final static Logger logger = LoggerFactory.getLogger(LongRunningTests.class);

    @Test
    public void testDeadLock() throws InterruptedException {
        String token = "aBcDeFgHiJkLmNoPqRsU";
        String type = "awesomeType";
        String loggerName = "deadlockLogger";
        int drainTimeout = 1;
        Integer gcInterval = 1;
        TestSenderWrapper testWrapper = getTestSenderWrapper(token, type, loggerName, drainTimeout, 98,
                null, 10*1000,false, "",30 , port);



        List<Thread> threads = new ArrayList<>();
        try {
            int threadCount = 10;
            CountDownLatch countDownLatch = new CountDownLatch(threadCount);
            final int msgCount = 100000000;
            for (int j = 1; j < threadCount; j++) {
                Thread thread = new Thread(() -> {
                    for (int i = 1; i <= msgCount; i++) {
                        testWrapper.warn(loggerName,"Hello i");
                        if (Thread.interrupted()) {
                            logger.info("Stopping thread - interrupted");
                            break;
                        }
                    }
                    countDownLatch.countDown();
                });
                thread.start();
                threads.add(thread);
            }

            countDownLatch.await(100, TimeUnit.SECONDS);

            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            long[] threadIds = bean.findDeadlockedThreads(); // Returns null if no threads are deadlocked.

            if (threadIds != null) {
                ThreadInfo[] infos = bean.getThreadInfo(threadIds);

                for (ThreadInfo info : infos) {
                    System.out.println("Locked thread: "+ info);
                }
            } else {
                logger.info("No deadlocked threads");
            }

            assertThat(threadIds).isNull();

        } finally {
            threads.forEach(Thread::interrupt);
            testWrapper.stop();
        }

    }
}
