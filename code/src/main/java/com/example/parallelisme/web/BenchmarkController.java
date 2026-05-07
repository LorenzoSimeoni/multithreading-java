package com.example.parallelisme.web;

import com.example.parallelisme.benchmark.BenchmarkResult;
import com.example.parallelisme.benchmark.BenchmarkService;
import com.example.parallelisme.benchmark.TaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints REST pour déclencher les benchmarks.
 *
 * Exemples :
 *   GET /benchmark/all?taskCount=50&taskType=IO_BOUND
 *   GET /benchmark/all?taskCount=50&taskType=CPU_BOUND
 *   GET /benchmark/compare?taskCount=50          ← lance les 2 types d'un coup (12 résultats)
 *   GET /benchmark/virtual-threads?taskCount=100&taskType=IO_BOUND
 */
@RestController
@RequestMapping("/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    /**
     * Lance toutes les stratégies pour UN type de tâche.
     * 6 résultats.
     */
    @GetMapping("/all")
    public List<BenchmarkResult> runAll(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "IO_BOUND") TaskType taskType) {
        return benchmarkService.runAll(taskCount, taskType);
    }

    /**
     * Lance toutes les stratégies pour LES DEUX types.
     * 12 résultats — c'est le plus parlant pour la comparaison IO vs CPU.
     */
    @GetMapping("/compare")
    public List<BenchmarkResult> compare(@RequestParam(defaultValue = "50") int taskCount) {
        return benchmarkService.runAllTaskTypes(taskCount);
    }

    @GetMapping("/sequential")
    public BenchmarkResult sequential(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "IO_BOUND") TaskType taskType) {
        return benchmarkService.withSequential(taskCount, taskType);
    }

    @GetMapping("/legacy-threads")
    public BenchmarkResult legacyThreads(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "IO_BOUND") TaskType taskType) {
        return benchmarkService.withLegacyThreads(taskCount, taskType);
    }

    @GetMapping("/completable-future")
    public BenchmarkResult completableFuture(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "IO_BOUND") TaskType taskType) {
        return benchmarkService.withCompletableFuture(taskCount, taskType);
    }

    @GetMapping("/parallel-streams")
    public BenchmarkResult parallelStreams(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "IO_BOUND") TaskType taskType) {
        return benchmarkService.withParallelStreams(taskCount, taskType);
    }

    @GetMapping("/virtual-threads")
    public BenchmarkResult virtualThreads(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "IO_BOUND") TaskType taskType) {
        return benchmarkService.withVirtualThreads(taskCount, taskType);
    }

    @GetMapping("/reactor")
    public BenchmarkResult reactor(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "IO_BOUND") TaskType taskType) {
        return benchmarkService.withProjectReactor(taskCount, taskType);
    }

    @GetMapping("/fork-join")
    public BenchmarkResult forkJoin(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "IO_BOUND") TaskType taskType) {
        return benchmarkService.withForkJoinPool(taskCount, taskType);
    }

    @GetMapping("/structured-concurrency")
    public BenchmarkResult structuredConcurrency(
            @RequestParam(defaultValue = "50") int taskCount,
            @RequestParam(defaultValue = "IO_BOUND") TaskType taskType) {
        return benchmarkService.withStructuredConcurrency(taskCount, taskType);
    }
}
