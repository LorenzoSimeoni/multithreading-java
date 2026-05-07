package com.example.parallelisme.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * Service regroupant 7 stratégies de parallélisation Java + 1 cas témoin séquentiel.
 *
 * Chaque méthode accepte un TaskType (IO_BOUND ou CPU_BOUND) pour permettre
 * la comparaison directe : même stratégie, tâche différente → résultats très différents.
 */
@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    /**
     * Capture l'identité du thread courant.
     *
     * Platform thread : getName() → "ForkJoinPool-1-worker-3"
     * Virtual thread  : getName() → "" (vide, inutile)
     *                   toString() → "VirtualThread[#42]/runnable@ForkJoinPool-1-worker-3"
     *                   → encode l'ID du VT et le carrier thread, les deux en un.
     *
     * On utilise toString() pour les VT afin que BenchmarkResult puisse distinguer
     * le nombre de VT créés et le nombre de carrier threads (threads OS) réellement utilisés.
     */
    private static String threadInfo() {
        Thread t = Thread.currentThread();
        return t.isVirtual() ? t.toString() : t.getName();
    }

    private void runTask(TaskType taskType) {
        if (taskType == TaskType.IO_BOUND) {
            TaskSimulator.simulateIoBound();
        } else {
            TaskSimulator.simulateCpuBound();
        }
    }

    private String label(String strategy, TaskType taskType) {
        return strategy + " [" + taskType.name() + "]";
    }

    // =========================================================================
    // 0. Cas témoin : exécution séquentielle (aucun parallélisme)
    // =========================================================================
    /**
     * POURQUOI UN CAS TÉMOIN ?
     *  Sans référence séquentielle, impossible de quantifier le gain apporté par chaque stratégie.
     *  Le temps séquentiel = taskCount × durée_unitaire_tâche.
     *  Le speedup d'une stratégie = tempsSequentiel / tempsStratégie.
     *
     * AVEC IO_BOUND : temps = taskCount × 100ms → dramatiquement plus long que les autres.
     *   Ex : 50 tâches → 50 × 100ms = 5 000ms. Virtual Threads : ~100ms. Speedup : ×50.
     *
     * AVEC CPU_BOUND : temps = taskCount × durée_calcul.
     *   Speedup possible ≈ N_CPU. Au-delà, on est limité par le hardware.
     *
     * Thread utilisé : le thread appelant uniquement (1 seul thread).
     */
    public BenchmarkResult withSequential(int taskCount, TaskType taskType) {
        log.info("=== Séquentiel [{}] : démarrage de {} tâches ===", taskType, taskCount);
        List<String> threadNames = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 0; i < taskCount; i++) {
            String info = threadInfo();
            threadNames.add(info);
            log.debug("SEQ tâche #{} → thread [{}]", i, info);
            runTask(taskType);
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Séquentiel [{}] terminé en {} ms ===", taskType, duration);
        return new BenchmarkResult(label("Sequential", taskType), duration, taskCount, threadNames);
    }

    // =========================================================================
    // 1. CompletableFuture
    // =========================================================================
    /**
     * QUAND L'UTILISER :
     *  - Tâches IO-bound asynchrones avec composition (thenApply, thenCombine...).
     *  - Quand tu veux chaîner des étapes sans bloquer un thread entre chacune.
     *  - Ex : appel HTTP → transformer la réponse → sauvegarder en BDD, tout en async.
     *
     * AVEC IO_BOUND : performant. runAsync soumet chaque tâche au ForkJoinPool.commonPool(),
     *   qui peut avoir plusieurs tâches en attente simultanément → bonne concurrence.
     *
     * AVEC CPU_BOUND : ForkJoinPool.commonPool() a N threads (N = CPUs).
     *   Si taskCount > N, les tâches font la queue → pas de gain au-delà de N threads.
     *   Utiliser un ExecutorService dédié ou Parallel Streams est plus adapté.
     *
     * ATTENTION : le .join() final bloque le thread appelant.
     */
    public BenchmarkResult withCompletableFuture(int taskCount, TaskType taskType) {
        log.info("=== CompletableFuture [{}] : démarrage de {} tâches ===", taskType, taskCount);
        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        long start = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = IntStream.range(0, taskCount)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    String info = threadInfo();
                    threadNames.add(info);
                    log.debug("CF tâche #{} → thread [{}]", i, info);
                    runTask(taskType);
                }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long duration = System.currentTimeMillis() - start;
        log.info("=== CompletableFuture [{}] terminé en {} ms ===", taskType, duration);
        return new BenchmarkResult(label("CompletableFuture", taskType), duration, taskCount, threadNames);
    }

    // =========================================================================
    // 2. Parallel Streams
    // =========================================================================
    /**
     * QUAND L'UTILISER :
     *  - Tâches CPU-bound sur des collections en mémoire (tri, transformation, agrégation).
     *  - Code simple, lisible, bonne performance sur des données larges.
     *  - Ex : transformer une liste de 100 000 objets, calculs statistiques.
     *
     * AVEC CPU_BOUND : excellent. N threads occupent N CPUs en parallèle réel.
     *   platformThreadCount() ≈ N_CPU. Speedup ≈ N_CPU vs séquentiel.
     *
     * AVEC IO_BOUND : mauvais. ForkJoinPool.commonPool() a seulement N_CPU threads.
     *   Chaque thread bloque pendant toute la durée IO → les autres tâches ATTENDENT.
     *   Temps ≈ (taskCount / N_CPU) × durée_IO au lieu de durée_IO avec Virtual Threads.
     *
     * ATTENTION : BLOQUE le thread appelant. À éviter en contexte serveur réactif.
     */
    public BenchmarkResult withParallelStreams(int taskCount, TaskType taskType) {
        log.info("=== Parallel Streams [{}] : démarrage de {} tâches ===", taskType, taskCount);
        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        long start = System.currentTimeMillis();

        IntStream.range(0, taskCount)
                .parallel()
                .forEach(i -> {
                    String info = threadInfo();
                    threadNames.add(info);
                    log.debug("PS tâche #{} → thread [{}]", i, info);
                    runTask(taskType);
                });

        long duration = System.currentTimeMillis() - start;
        log.info("=== Parallel Streams [{}] terminé en {} ms ===", taskType, duration);
        return new BenchmarkResult(label("ParallelStreams", taskType), duration, taskCount, threadNames);
    }

    // =========================================================================
    // 3. Virtual Threads (Java 21+)
    // =========================================================================
    /**
     * QUAND L'UTILISER :
     *  - Tâches IO-bound en très grande quantité (des milliers, voire millions).
     *  - Migration d'une application synchrone bloquante vers la concurrence sans refactoring.
     *  - Ex : serveur qui gère 10 000 connexions simultanées, chacune faisant une requête BDD.
     *
     * AVEC IO_BOUND : excellent. Chaque tâche a son propre Virtual Thread (~200 octets).
     *   Quand le VT bloque sur Thread.sleep/IO, le carrier thread est libéré pour un autre VT.
     *   Résultat : toutes les tâches progressent en "parallèle" → temps ≈ durée_IO seule.
     *   virtualThreadCount() ≈ taskCount, platformThreadCount() ≈ N_CPU (les carrier threads).
     *
     * AVEC CPU_BOUND : pas de gain réel. Les VT sont montés sur N_CPU carrier threads.
     *   Créer 1000 VT CPU-bound revient à 1000 threads qui se battent pour N CPUs.
     *
     * ATTENTION : éviter les synchronized blocks avec IO à l'intérieur (pinning).
     */
    public BenchmarkResult withVirtualThreads(int taskCount, TaskType taskType) {
        log.info("=== Virtual Threads [{}] : démarrage de {} tâches ===", taskType, taskCount);
        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = IntStream.range(0, taskCount)
                    .<Future<?>>mapToObj(i -> executor.submit(() -> {
                        String info = threadInfo(); // toString() pour les VT → "VirtualThread[#N]@carrier"
                        threadNames.add(info);
                        log.debug("VT tâche #{} → [{}]", i, info);
                        runTask(taskType);
                    }))
                    .toList();

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    log.error("Erreur dans Virtual Thread tâche", e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Virtual Threads [{}] terminé en {} ms ===", taskType, duration);
        return new BenchmarkResult(label("VirtualThreads", taskType), duration, taskCount, threadNames);
    }

    // =========================================================================
    // 4. Project Reactor — Flux parallel()
    // =========================================================================
    /**
     * QUAND L'UTILISER :
     *  - Pipelines réactifs avec backpressure (le consommateur contrôle le débit).
     *  - Composition complexe d'opérations async : merge, zip, retry, timeout...
     *
     * PATTERN : flatMap + subscribeOn (et NON parallel().runOn())
     *  - parallel().runOn() est conçu pour CPU-bound (N_CPU rails fixes).
     *    Pour IO-bound avec parallel(), même en passant parallel(taskCount),
     *    les Workers du scheduler sont partagés entre rails → pas de vraie concurrence.
     *  - flatMap(mapper, maxConcurrency) souscrit jusqu'à maxConcurrency Mono en parallèle.
     *    subscribeOn(scheduler) pousse chaque Mono sur un thread du scheduler.
     *    boundedElastic spawne autant de threads que nécessaire (cap = 10 × N_CPU).
     *
     * AVEC IO_BOUND → boundedElastic + concurrence = taskCount : tous les threads bloquent
     *   en parallèle → temps ≈ durée_IO seule, comme Virtual Threads.
     * AVEC CPU_BOUND → parallel() + concurrence = N_CPU : exactement N_CPU threads calculent,
     *   pas de context-switching inutile.
     *
     * NOTE sur le terminal operator :
     *  CountDownLatch + subscribe() au lieu de blockLast() : même sémantique bloquante
     *  mais sans le check Reactor isInNonBlockingThread() qui lève IllegalStateException
     *  quand appelé depuis le thread NIO de WebFlux.
     */
    public BenchmarkResult withProjectReactor(int taskCount, TaskType taskType) {
        log.info("=== Project Reactor [{}] : démarrage de {} tâches ===", taskType, taskCount);
        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        long start = System.currentTimeMillis();

        Scheduler scheduler = (taskType == TaskType.IO_BOUND)
                ? Schedulers.boundedElastic()
                : Schedulers.parallel();

        // IO_BOUND : toutes les tâches en parallèle (limitées par le cap boundedElastic)
        // CPU_BOUND : concurrence = N_CPU pour ne pas créer plus de threads que de cœurs
        int concurrency = (taskType == TaskType.IO_BOUND)
                ? taskCount
                : Runtime.getRuntime().availableProcessors();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

//        Flux.range(0, taskCount)
//                .flatMap(i -> Mono.fromCallable(() -> {
//                    String info = threadInfo();
//                    threadNames.add(info);
//                    log.debug("Reactor tâche #{} → thread [{}]", i, info);
//                    runTask(taskType);
//                    return info;
//                }).subscribeOn(scheduler), concurrency)
//                .subscribe(
//                        v -> {},
//                        err -> { errorRef.set(err); latch.countDown(); },
//                        latch::countDown
//                );

        Flux.range(0, taskCount)
                .parallel(taskCount)
                .runOn(scheduler)
                .doOnNext(i -> {
                    String info = threadInfo();
                    threadNames.add(info);
                    log.debug("Reactor tâche #{} → thread [{}]", i, info);
                    runTask(taskType);
                })
                .sequential()
                .subscribe(
                        v -> {},
                        err -> { errorRef.set(err); latch.countDown(); },
                        latch::countDown
                );

        try {
            latch.await(); // bloque le thread appelant — intentionnel dans ce benchmark
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (errorRef.get() != null) {
            throw new RuntimeException(errorRef.get());
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Project Reactor [{}] terminé en {} ms ===", taskType, duration);
        return new BenchmarkResult(label("ProjectReactor", taskType), duration, taskCount, threadNames);
    }

    // =========================================================================
    // 5. ForkJoinPool avec pool personnalisé
    // =========================================================================
    /**
     * QUAND L'UTILISER :
     *  - Tâches CPU-bound avec isolation du pool (ne pas impacter ForkJoinPool.commonPool()).
     *  - Ex : traitements batch lourds qui ne doivent pas ralentir le reste de l'appli.
     *
     * AVEC CPU_BOUND : excellent. Pool dédié avec N_CPU threads, work-stealing activé.
     * AVEC IO_BOUND : mauvais. N_CPU threads bloqués → file d'attente.
     *
     * PRINCIPE : work-stealing = un thread idle vole des tâches à un thread occupé.
     */
    public BenchmarkResult withForkJoinPool(int taskCount, TaskType taskType) {
        log.info("=== ForkJoinPool [{}] : démarrage de {} tâches ===", taskType, taskCount);
        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        int parallelism = Runtime.getRuntime().availableProcessors();
        long start = System.currentTimeMillis();

        ForkJoinPool customPool = new ForkJoinPool(parallelism);
        try {
            customPool.submit(() ->
                    IntStream.range(0, taskCount)
                            .parallel()
                            .forEach(i -> {
                                String info = threadInfo();
                                threadNames.add(info);
                                log.debug("FJP tâche #{} → thread [{}]", i, info);
                                runTask(taskType);
                            })
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.error("Erreur dans ForkJoinPool", e);
        } finally {
            customPool.shutdown();
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== ForkJoinPool [{}] terminé en {} ms (parallélisme: {}) ===", taskType, duration, parallelism);
        return new BenchmarkResult(label("ForkJoinPool", taskType), duration, taskCount, threadNames);
    }

    // =========================================================================
    // 6. Structured Concurrency (Java 21+ — preview feature)
    // =========================================================================
    /**
     * QUAND L'UTILISER :
     *  - Sous-tâches concurrentes formant une unité logique avec cycle de vie strict.
     *  - Ex : charger user + commandes en parallèle, annulation si l'une échoue.
     *
     * AVEC IO_BOUND : excellent (VT sous-jacents). virtualThreadCount() ≈ taskCount.
     * AVEC CPU_BOUND : même limite que Virtual Threads (N_CPU carrier threads).
     *
     * NOTE : Requiert --enable-preview (Java 25, JEP 505).
     */
    @SuppressWarnings("preview")
    public BenchmarkResult withStructuredConcurrency(int taskCount, TaskType taskType) {
        log.info("=== Structured Concurrency [{}] : démarrage de {} tâches ===", taskType, taskCount);
        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        long start = System.currentTimeMillis();

        // Java 25 : l'API a été redessinée (JEP 505).
        // ShutdownOnFailure/ShutdownOnSuccess → supprimés, remplacés par le pattern Joiner.
        // StructuredTaskScope.open() utilise Joiner.awaitAllSuccessfulOrThrow() par défaut :
        //   - attend toutes les sous-tâches
        //   - lève FailedException (RuntimeException) si l'une d'elles échoue
        // join() retourne le résultat du Joiner (ici Void) sans appel à .throwIfFailed().
        try (var scope = StructuredTaskScope.open()) {
            IntStream.range(0, taskCount)
                    .mapToObj(i -> scope.fork(() -> {
                        String info = threadInfo();
                        threadNames.add(info);
                        log.debug("SC tâche #{} → thread [{}]", i, info);
                        runTask(taskType);
                        return info;
                    }))
                    .toList(); // force l'évaluation — fork() est lazy sur un stream
            scope.join(); // lève FailedException (RuntimeException) si une tâche échoue
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (StructuredTaskScope.FailedException e) {
            log.error("Erreur dans Structured Concurrency", e.getCause());
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Structured Concurrency [{}] terminé en {} ms ===", taskType, duration);
        return new BenchmarkResult(label("StructuredConcurrency", taskType), duration, taskCount, threadNames);
    }

    // =========================================================================
    // 7. Legacy Threads (Java 1.0) — Thread + Runnable
    // =========================================================================
    /**
     * LA FAÇON ORIGINALE (Java 1.0, 1996).
     * Avant ExecutorService (Java 5), ForkJoinPool (Java 7), CompletableFuture (Java 8),
     * Virtual Threads (Java 21)... on faisait comme ça.
     *
     * PRINCIPE : un Thread OS par tâche, créé à la main, démarré à la main, joint à la main.
     *
     *   // Ancêtre anonyme (pré-Java 8) :
     *   Thread t = new Thread(new Runnable() {
     *       public void run() { faireLaTache(); }
     *   });
     *   t.start();
     *   t.join();
     *
     * AVEC IO_BOUND : perfs similaires aux Virtual Threads (tous les threads bloquent en
     *   parallèle). MAIS chaque thread coûte ~1 Mo de stack. 200 tâches = ~200 Mo juste en stack.
     *   Avec 10 000 tâches → OutOfMemoryError. C'est exactement le problème que Virtual Threads
     *   (Java 21) résolvent avec le même modèle de programmation mais ~200 octets par thread.
     *
     * AVEC CPU_BOUND : perfs identiques à ForkJoinPool/ParallelStreams. Limité par N_CPU.
     *   Sans pool → overhead de création/destruction de thread pour chaque exécution.
     *
     * PROBLÈMES vs approches modernes :
     *  - Pas de pool : chaque appel à withLegacyThreads() recrée tous les threads from scratch.
     *  - Pas de backpressure : 10 000 tâches = 10 000 threads = crash.
     *  - Gestion d'erreur primitive : si un thread plante, les autres continuent.
     *  - Pas de valeur de retour native : il faut passer par des structures partagées (synchronized).
     */
    public BenchmarkResult withLegacyThreads(int taskCount, TaskType taskType) {
        log.info("=== Legacy Threads [{}] : démarrage de {} tâches ===", taskType, taskCount);
        List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        long start = System.currentTimeMillis();

        // Création explicite d'un Thread par tâche — aucun pool, aucune réutilisation
        List<Thread> threads = IntStream.range(0, taskCount)
                .mapToObj(i -> new Thread(() -> {
                    String info = threadInfo();
                    threadNames.add(info);
                    log.debug("Thread tâche #{} → [{}]", i, info);
                    runTask(taskType);
                }))
                .toList();

        // Démarrage manuel de chaque thread
        threads.forEach(Thread::start);

        // Attente manuelle de chaque thread — l'équivalent primitif de CompletableFuture.allOf()
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Legacy Threads [{}] terminé en {} ms ===", taskType, duration);
        return new BenchmarkResult(label("LegacyThreads", taskType), duration, taskCount, threadNames);
    }

    // =========================================================================
    // Runners globaux
    // =========================================================================

    public List<BenchmarkResult> runAll(int taskCount, TaskType taskType) {
        log.info("========== BENCHMARK COMPLET [{}] : {} tâches ==========", taskType, taskCount);
        List<BenchmarkResult> results = new ArrayList<>();

        results.add(withSequential(taskCount, taskType));
        results.add(withLegacyThreads(taskCount, taskType));
        results.add(withCompletableFuture(taskCount, taskType));
        results.add(withParallelStreams(taskCount, taskType));
        results.add(withVirtualThreads(taskCount, taskType));
        results.add(withStructuredConcurrency(taskCount, taskType));
        results.add(withProjectReactor(taskCount, taskType));
        results.add(withForkJoinPool(taskCount, taskType));

        log.info("========== RÉSULTATS [{}] ==========", taskType);
        results.forEach(r -> log.info("{}", r));
        return results;
    }

    public List<BenchmarkResult> runAllTaskTypes(int taskCount) {
        log.info("========== BENCHMARK COMPLET IO + CPU : {} tâches ==========", taskCount);
        List<BenchmarkResult> results = new ArrayList<>();
        results.addAll(runAll(taskCount, TaskType.IO_BOUND));
        results.addAll(runAll(taskCount, TaskType.CPU_BOUND));
        return results;
    }
}