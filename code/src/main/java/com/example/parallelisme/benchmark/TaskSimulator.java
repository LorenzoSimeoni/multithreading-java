package com.example.parallelisme.benchmark;

/**
 * Simule deux types de tâches : IO-bound et CPU-bound.
 *
 * IO-bound  : le thread se bloque en attente (réseau, BDD, fichier).
 *             → Idéal pour Virtual Threads, CompletableFuture, Reactor.
 *             → Parallel Streams / ForkJoinPool n'apportent rien : ils occupent
 *               un slot de thread de pool pour... attendre.
 *
 * CPU-bound : le thread calcule activement et consomme du CPU.
 *             → Idéal pour Parallel Streams, ForkJoinPool.
 *             → Virtual Threads n'aident PAS : ils sont montés sur des platform
 *               threads, et le scheduler OS reste le goulot d'étranglement.
 */
public class TaskSimulator {

    private TaskSimulator() {}

    /**
     * Simule une tâche IO-bound : 100 ms d'attente (ex: appel HTTP, requête BDD).
     */
    public static void simulateIoBound() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simule une tâche CPU-bound : calcule les N premiers nombres premiers.
     * Retourne un résultat pour éviter que le JIT ne l'élimine (dead code elimination).
     */
    public static long simulateCpuBound() {
        long count = 0;
        long candidate = 2;
        // Cherche les 50 000 premiers nombres premiers (~300-500ms par tâche selon le CPU)
        while (count < 20_000) {
            if (isPrime(candidate)) {
                count++;
            }
            candidate++;
        }
        return candidate; // valeur retournée pour forcer l'évaluation
    }

    private static boolean isPrime(long n) {
        if (n < 2) return false;
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (long i = 3; i * i <= n; i += 2) {
            if (n % i == 0) return false;
        }
        return true;
    }
}
