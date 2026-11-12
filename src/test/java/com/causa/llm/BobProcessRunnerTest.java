package com.causa.llm;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BobProcessRunnerTest {

    @Test
    void rejectsProcessesAboveTheConfiguredLimit() throws Exception {
        assertEquals(3, BobProcessRunner.MAX_CONCURRENT_PROCESSES);

        CountDownLatch started = new CountDownLatch(BobProcessRunner.MAX_CONCURRENT_PROCESSES);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(BobProcessRunner.MAX_CONCURRENT_PROCESSES);
        List<Future<String>> calls = new ArrayList<>();

        try {
            for (int i = 0; i < BobProcessRunner.MAX_CONCURRENT_PROCESSES; i++) {
                calls.add(executor.submit(() -> BobProcessRunner.run(processBuilder(), builder -> {
                    starts.incrementAndGet();
                    return new FakeProcess();
                }, process -> {
                    started.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release the Bob process");
                    }
                    return "finished";
                })));
            }

            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(BobProcessRunner.SaturatedException.class,
                    () -> BobProcessRunner.run(processBuilder(), builder -> {
                        starts.incrementAndGet();
                        return new FakeProcess();
                    }, process -> "unexpected"));
            assertEquals(BobProcessRunner.MAX_CONCURRENT_PROCESSES, starts.get());

            release.countDown();
            for (Future<String> call : calls) {
                assertEquals("finished", call.get(5, TimeUnit.SECONDS));
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void releasesCapacityAndStopsProcessAfterOperationFailure() throws Exception {
        FakeProcess process = new FakeProcess();
        assertThrows(IOException.class, () -> BobProcessRunner.run(processBuilder(), ignored -> process, ignored -> {
            throw new IOException("simulated output failure");
        }));

        assertFalse(process.isAlive());
        assertCanStartConfiguredNumberOfProcesses();
    }

    @Test
    void terminatesRealProcessBeforeReturningCapacity() throws Exception {
        AtomicReference<Process> startedProcess = new AtomicReference<>();

        String result = BobProcessRunner.run(longRunningProcessBuilder(), process -> {
            assertTrue(process.isAlive());
            startedProcess.set(process);
            return "finished";
        });

        assertEquals("finished", result);
        assertFalse(startedProcess.get().isAlive());
        assertCanStartConfiguredNumberOfProcesses();
    }

    @Test
    void terminatesTimedOutRealProcessBeforeReturningCapacity() throws Exception {
        AtomicReference<Process> startedProcess = new AtomicReference<>();

        assertThrows(IllegalStateException.class, () -> BobProcessRunner.run(longRunningProcessBuilder(), process -> {
            startedProcess.set(process);
            assertFalse(process.waitFor(100, TimeUnit.MILLISECONDS));
            throw new IllegalStateException("simulated Bob timeout");
        }));

        assertFalse(startedProcess.get().isAlive());
        assertCanStartConfiguredNumberOfProcesses();
    }

    @Test
    void releasesCapacityWhenProcessStartupFails() throws Exception {
        assertThrows(IOException.class, () -> BobProcessRunner.run(processBuilder(), ignored -> {
            throw new IOException("simulated process startup failure");
        }, ignored -> "unexpected"));
        assertCanStartConfiguredNumberOfProcesses();
    }

    @Test
    void stopsProcessAndReleasesCapacityWhenOperationIsInterrupted() throws Exception {
        FakeProcess process = new FakeProcess();
        assertThrows(InterruptedException.class, () -> BobProcessRunner.run(processBuilder(), ignored -> process, ignored -> {
            throw new InterruptedException("simulated cancellation");
        }));

        assertFalse(process.isAlive());
        assertCanStartConfiguredNumberOfProcesses();
    }

    private static void assertCanStartConfiguredNumberOfProcesses() throws Exception {
        for (int i = 0; i < BobProcessRunner.MAX_CONCURRENT_PROCESSES; i++) {
            assertEquals("finished", BobProcessRunner.run(processBuilder(), ignored -> new FakeProcess(), ignored -> "finished"));
        }
    }

    private static ProcessBuilder processBuilder() {
        return new ProcessBuilder("bob");
    }

    private static ProcessBuilder longRunningProcessBuilder() throws Exception {
        String javaExecutable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + javaExecutable;
        String testClassesPath = new File(LongRunningProcess.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getPath();
        return new ProcessBuilder(
                javaPath,
                "-cp",
                testClassesPath,
                LongRunningProcess.class.getName());
    }

    public static final class LongRunningProcess {

        private LongRunningProcess() {
        }

        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(TimeUnit.SECONDS.toMillis(30));
        }
    }

    private static final class FakeProcess extends Process {
        private final CountDownLatch stopped = new CountDownLatch(1);

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() throws InterruptedException {
            stopped.await();
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            return stopped.await(timeout, unit);
        }

        @Override
        public int exitValue() {
            if (isAlive()) {
                throw new IllegalThreadStateException("Process is still running");
            }
            return 0;
        }

        @Override
        public void destroy() {
            stopped.countDown();
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return stopped.getCount() != 0;
        }
    }
}
