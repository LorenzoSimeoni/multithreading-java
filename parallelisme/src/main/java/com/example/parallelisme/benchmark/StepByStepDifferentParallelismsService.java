package com.example.parallelisme.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.*;

@Service
public class StepByStepDifferentParallelismsService {

    private static final Logger logger = LoggerFactory.getLogger(StepByStepDifferentParallelismsService.class);

    public void useCaseJava1() {

        ThreadFactory factory = Thread.ofPlatform()
                .name("Thread Lorenzo-", 0)
                .daemon(false)
                .priority(Thread.NORM_PRIORITY)
                .factory();

        for (int i = 1; i <= 10; i++) {
            factory.newThread(() -> logger.info("Hello from thread: {}", Thread.currentThread().getName())).start();
        }
    }

    public void useCaseJava1WithVT() {
        ThreadFactory factory = Thread.ofVirtual().name("VT Thread Lorenzo-", 0).factory();

        for (int i = 1; i <= 10; i++) {
            factory.newThread(() -> logger.info("Hello from VT thread: {}", Thread.currentThread().getName())).start();
        }
    }

    public void useCaseJava5Runnable() {
        try (ExecutorService executorService = Executors.newFixedThreadPool(5)) {
            for (int i = 1; i <= 10; i++) {
                executorService.execute(() -> logger.info("Hello from thread: {}", Thread.currentThread().getName()));
            }
        }
    }

    public void useCaseJava5Callable() {
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            Future<String> future = executorService.submit(() -> {
                // Simulate a long-running task
                Thread.sleep(Duration.ofSeconds(1));
                return "Result of the task";
            });

            // Check if the task is done
            boolean isDone = future.isDone();
            logger.info("Task is done: " + isDone);

            String result = null;
            try {
                result = future.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Error while getting the result", e);
            }
            logger.info("Result: " + result);
        }
    }
}
