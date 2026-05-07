package com.example.parallelisme.benchmark;

/**
 * Type de tâche simulée.
 *
 * IO_BOUND  : thread bloqué en attente (réseau, BDD, fichier).
 *             Les threads n'utilisent pas le CPU pendant l'attente.
 *             → On veut beaucoup de threads légers pour couvrir l'attente.
 *
 * CPU_BOUND : thread qui calcule activement et consomme du CPU.
 *             Ajouter plus de threads que de CPUs ne fait qu'augmenter le context-switching.
 *             → On veut exactement N threads (N = nombre de CPUs).
 */
public enum TaskType {
    IO_BOUND,
    CPU_BOUND
}
