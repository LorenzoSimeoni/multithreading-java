package com.example.parallelisme.benchmark;

import java.util.List;

/**
 * Résultat d'un benchmark pour une stratégie donnée.
 * Record immuable : idéal pour des données de résultats sans mutation.
 *
 * threadNames contient :
 *  - Pour les platform threads : le nom du thread (ex: "ForkJoinPool-1-worker-3")
 *  - Pour les virtual threads  : le toString() complet (ex: "VirtualThread[#42]/runnable@ForkJoinPool-1-worker-3")
 *    → toString() encode l'ID du VT ET le nom du carrier thread — getName() retourne "" pour les VT.
 */
public record BenchmarkResult(
        String strategy,
        long durationMs,
        int taskCount,
        List<String> threadNames
) {
    /** Nombre total d'entrées distinctes — pour les VT chaque entrée est unique (ID différent). */
    public long uniqueThreadCount() {
        return threadNames.stream().distinct().count();
    }

    /**
     * Nombre de virtual threads distincts utilisés.
     * Un virtual thread est identifié par "VirtualThread[#N]..." dans toString().
     */
    public long virtualThreadCount() {
        return threadNames.stream()
                .filter(n -> n.startsWith("VirtualThread"))
                .distinct()
                .count();
    }

    /**
     * Nombre de platform threads distincts utilisés (threads OS réels).
     * Pour les stratégies sans VT : c'est simplement le nombre de threads du pool.
     * Pour les stratégies avec VT : c'est le nombre de carrier threads distincts.
     */
    public long platformThreadCount() {
        return threadNames.stream()
                .map(this::extractPlatformThreadName)
                .distinct()
                .count();
    }

    /**
     * Extrait le nom du thread platform depuis une entrée threadNames.
     * - VT  : "VirtualThread[#42]/runnable@ForkJoinPool-1-worker-3" → "ForkJoinPool-1-worker-3"
     * - PT  : "ForkJoinPool-1-worker-3" → "ForkJoinPool-1-worker-3" (inchangé)
     */
    private String extractPlatformThreadName(String threadInfo) {
        int atIdx = threadInfo.indexOf('@');
        return atIdx >= 0 ? threadInfo.substring(atIdx + 1) : threadInfo;
    }

    @Override
    public String toString() {
        long vt = virtualThreadCount();
        long pt = platformThreadCount();
        String threadDetail = vt > 0
                ? "%d VT / %d carrier threads".formatted(vt, pt)
                : "%d platform threads".formatted(pt);
        return "[%s] %d ms | %d tâches | %s"
                .formatted(strategy, durationMs, taskCount, threadDetail);
    }
}