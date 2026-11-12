package com.causa.llm;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Starts Bob processes under one application-wide concurrency limit.
 */
final class BobProcessRunner {

    static final int MAX_CONCURRENT_PROCESSES = 3;
    private static final Semaphore PROCESS_SLOTS = new Semaphore(MAX_CONCURRENT_PROCESSES, true);

    private BobProcessRunner() {
    }

    static <T> T run(ProcessBuilder processBuilder, ProcessOperation<T> operation)
            throws IOException, InterruptedException {
        return run(processBuilder, ProcessBuilder::start, operation);
    }

    static <T> T run(ProcessBuilder processBuilder, ProcessStarter processStarter, ProcessOperation<T> operation)
            throws IOException, InterruptedException {
        if (!PROCESS_SLOTS.tryAcquire(0, TimeUnit.SECONDS)) {
            throw new SaturatedException();
        }

        Process process = null;
        try {
            process = processStarter.start(processBuilder);
            return operation.run(process);
        } finally {
            try {
                terminateIfAlive(process);
            } finally {
                if (process == null || !process.isAlive()) {
                    PROCESS_SLOTS.release();
                }
            }
        }
    }

    private static void terminateIfAlive(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }

        boolean interrupted = Thread.interrupted();
        try {
            process.destroyForcibly();
            while (process.isAlive()) {
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(ProcessBuilder processBuilder) throws IOException;
    }

    @FunctionalInterface
    interface ProcessOperation<T> {
        T run(Process process) throws IOException, InterruptedException;
    }

    static final class SaturatedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private SaturatedException() {
            super("Bob process concurrency limit reached");
        }
    }
}
