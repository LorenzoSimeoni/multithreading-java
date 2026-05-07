# Concurrence et Programmation Asynchrone en Java
### De Java 1 à Java 25

---

> **À propos de ce cours**
> Ce document couvre l'évolution complète de la concurrence en Java, depuis les threads primitifs de Java 1.0 jusqu'aux Virtual Threads de Java 21+.
> Chaque section indique clairement si le contenu concerne un workload **CPU-bound** (calcul intensif) ou **I/O-bound** (attente de ressources externes).

---

## Table des Matières

1. [Vue d'ensemble — L'évolution de la concurrence Java](#1-vue-densemble--lévolution-de-la-concurrence-java)
2. [Java 1.0 — Thread, Runnable, synchronized](#2-java-10--thread-runnable-synchronized)
3. [Java 5 — Concurrency API](#3-java-5--concurrency-api)
4. [Java 7 — ForkJoinPool et Work-Stealing](#4-java-7--forkjoinpool-et-work-stealing)
5. [Combien de Platform Threads avoir ?](#5-combien-de-platform-threads-avoir-)
6. [Java 8 — CompletableFuture et parallelStream](#6-java-8--completablefuture-et-parallelstream)
7. [Java 9 — Flow API et Reactive Streams](#7-java-9--flow-api-et-reactive-streams)
8. [Java 21 — Virtual Threads, Structured Concurrency et Scoped Values](#8-java-21--virtual-threads-structured-concurrency-et-scoped-values)
9. [Bonus — Le modèle Actor](#bonus--le-modèle-actor)

---

## Préambule : CPU-bound vs I/O-bound

Tout au long de ce cours, nous distinguons deux grandes familles de workloads. Cette distinction est **fondamentale** car les bonnes solutions ne sont pas les mêmes selon le cas.

### CPU-bound
Le facteur limitant est la **puissance de calcul du processeur**. Le CPU est saturé pendant le traitement.

> **Exemples :** tri de millions d'éléments, cryptographie, traitement d'image/vidéo, machine learning, compression de données.

### I/O-bound
Le facteur limitant est l'**attente de ressources externes**. Le CPU est souvent inactif pendant ce temps.

> **Exemples :** requêtes SQL, appels API REST, lecture/écriture de fichiers, appels réseau, communication inter-services.

---

## 1. Vue d'ensemble — L'évolution de la concurrence Java

Java 1.0, apparu en **1995**, était la première plateforme mainstream à inclure les threads dans le langage noyau. Créer des threads est facile ; les gérer à grande échelle est une toute autre affaire. Chaque génération suivante a répondu aux limitations de la précédente.

```
Java 1.0  →  Thread, Runnable, synchronized         (gestion manuelle)
Java 5    →  ExecutorService, Future, Callable       (gestion de pools)
Java 7    →  ForkJoinPool, Work-Stealing             (traitement récursif)
Java 8    →  CompletableFuture, parallelStream       (asynchrone composable)
Java 9    →  Flow API, Reactive Streams              (flux de données asynchrones)
Java 21   →  Virtual Threads, Structured Concurrency, Scoped Values
```

### Le fil conducteur

| Problème rencontré | Solution apportée |
|---|---|
| Gestion manuelle des threads | `ExecutorService` (Java 5) |
| Threads coûteux à créer | Pools de threads réutilisables |
| Pas de parallélisme récursif | `ForkJoinPool` (Java 7) |
| `Future` bloquant et non composable | `CompletableFuture` (Java 8) |
| Un seul résultat, pas de backpressure | `Flow API` (Java 9) |
| Platform threads limités et coûteux | Virtual Threads (Java 21) |
| Cycle de vie des tâches non structuré | Structured Concurrency (Java 21) |

---

## 2. Java 1.0 — Thread, Runnable, synchronized

### 2.1 Thread et Runnable

En Java 1, on interagit avec la concurrence via `Thread` (classe) ou `Runnable` (interface). On peut configurer chaque thread avec :

- Un **nom** — utile pour le debug et les logs
- Une **priorité** — de 1 à 10, par défaut à 5
- Un mode **daemon ou non** — voir ci-dessous

```java
public static final int MIN_PRIORITY  = 1;
public static final int NORM_PRIORITY = 5;
public static final int MAX_PRIORITY  = 10;
```

> 💡 **Daemon vs Non-Daemon**
> La JVM attend que tous les threads **non-daemon** se terminent avant de quitter. Un thread de paiement en cours doit être **non-daemon** pour ne pas être interrompu si le `main` se termine.
> Les threads daemon sont typiquement utilisés pour des tâches de maintenance en arrière-plan (garbage collection, monitoring...).

**Exemple de création manuelle (syntaxe Java 21+) :**

```java
public void useCaseJava1() {
    ThreadFactory factory = Thread.ofPlatform()
            .name("Thread-Lorenzo-", 0)  // noms : Thread-Lorenzo-0, -1, -2...
            .daemon(false)
            .priority(Thread.NORM_PRIORITY)
            .factory();

    for (int i = 1; i <= 10; i++) {
        factory.newThread(() ->
            logger.info("Hello from thread: {}", Thread.currentThread().getName())
        ).start();
    }
}
```

> ⚠️ **Problème fondamental** : on gère les threads à la main. Si on passe de 10 à 100 000 itérations, on crée 100 000 threads OS — lent, coûteux, voire impossible selon la limite de l'OS. C'est exactement ce que `ExecutorService` (Java 5) viendra résoudre.

**Pour la culture — équivalent avec Virtual Threads (Java 21+) :**

```java
public void useCaseJava1WithVirtualThreads() {
    ThreadFactory factory = Thread.ofVirtual()
            .name("VT-Thread-Lorenzo-", 0)
            .factory();

    for (int i = 1; i <= 10; i++) {
        factory.newThread(() ->
            logger.info("Hello from VT thread: {}", Thread.currentThread().getName())
        ).start();
    }
}
```

Nous reviendrons en détail sur les Virtual Threads dans la section 8.

---

### 2.2 synchronized — Protéger les données partagées

Quand plusieurs threads accèdent à la même donnée, on risque une **race condition** : le résultat dépend de l'ordre d'exécution des threads, ce qui est non-déterministe.

**Pourquoi `counter++` n'est pas atomique :**

```
counter++ se décompose en 3 opérations :
  1. READ  → lire la valeur de counter en mémoire
  2. ADD   → ajouter 1
  3. WRITE → écrire la nouvelle valeur

Si deux threads lisent counter=5 en même temps, tous deux écrivent 6.
Résultat : on a perdu un incrément.
```

**Exemple de race condition :**

```java
// ❌ Sans protection — résultat imprévisible
int counter = 0;

Runnable task = () -> {
    for (int i = 0; i < 1000; i++) {
        counter++; // READ → ADD → WRITE : non atomique !
    }
};

Thread t1 = new Thread(task);
Thread t2 = new Thread(task);
t1.start(); t2.start();
t1.join();  t2.join();

// counter devrait valoir 2000, mais vaut souvent moins (ex: 1743, 1891...)
System.out.println(counter);
```

`synchronized` résout ce problème en garantissant qu'**un seul thread à la fois** peut exécuter un bloc de code donné. Il peut s'appliquer de trois façons :

```java
// ① Sur une méthode entière (verrou = this)
public synchronized void increment() {
    counter++;
}

// ② Sur un bloc de code (verrou plus fin, préférable)
public void increment() {
    synchronized (this) {
        counter++;
    }
}

// ③ Sur un objet de verrouillage dédié (meilleure pratique)
private final Object lock = new Object();

public void increment() {
    synchronized (lock) {
        counter++;
    }
}
```

> ⚠️ **Limites de `synchronized`**
> - Pas de timeout possible (le thread attend indéfiniment)
> - Pas de `tryLock` (tentative sans blocage)
> - Bloque lecture ET écriture indistinctement
> - Java 5 introduira les `Lock` pour pallier ces limitations.

> 🎯 **CPU-bound** : `synchronized` protège l'accès concurrent à des données en mémoire — typiquement des opérations CPU légères.

---

## 3. Java 5 — Concurrency API

Java 5 introduit le package `java.util.concurrent`, une boîte à outils complète pour la concurrence industrielle. Nous couvrons ici ses grandes familles.

---

### 3.1 Executors

#### Le problème que ça résout

Créer un `Thread` manuellement à chaque tâche est coûteux (appel système OS). `ExecutorService` introduit le concept de **pool de threads** : un ensemble de threads réutilisables auxquels on soumet des tâches, sans se soucier de leur cycle de vie individuel.

#### Cycle de vie d'un ExecutorService

```java
// Création d'un pool à thread unique
ExecutorService service = Executors.newSingleThreadExecutor();

// Soumission d'une tâche (Runnable, sans retour)
service.execute(() -> System.out.println("Printing zoo inventory"));

// ① Arrêt propre : refuse les nouvelles tâches, attend la fin des tâches en cours
service.shutdown();

// ② Arrêt forcé : tente d'interrompre les tâches en cours (sans garantie)
List<Runnable> pending = service.shutdownNow();

// ③ Attente explicite après shutdown
service.shutdown();
service.awaitTermination(60, TimeUnit.SECONDS);
```

> ⚠️ **Toujours fermer un ExecutorService !**
> Si on ne le ferme pas, le thread non-daemon qu'il gère reste actif et **l'application ne se termine jamais**.

> 💡 **Java 19+** : `ExecutorService` implémente `AutoCloseable`. On peut donc utiliser le try-with-resources — `close()` appelle `shutdown()` puis `awaitTermination()` automatiquement.

```java
// Avec try-with-resources (Java 19+)
try (ExecutorService service = Executors.newFixedThreadPool(4)) {
    // soumettre des tâches...
} // fermeture automatique et propre
```

---

#### execute() vs submit() vs invokeAll()

| Méthode | Entrée | Sortie | Bloquant ? |
|---|---|---|---|
| `execute(Runnable)` | `Runnable` | `void` | Non |
| `submit(Runnable)` | `Runnable` | `Future<?>` | Non (`get()` l'est) |
| `submit(Callable<T>)` | `Callable<T>` | `Future<T>` | Non (`get()` l'est) |
| `invokeAll(Collection<Callable>)` | `Collection<Callable<T>>` | `List<Future<T>>` | Oui (attend toutes) |
| `invokeAny(Collection<Callable>)` | `Collection<Callable<T>>` | `T` | Oui (premier succès) |

**L'objet `Future<T>` :**

```java
public void useCaseJava5Callable() {
    try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
        Future<String> future = executorService.submit(() -> {
            Thread.sleep(Duration.ofSeconds(1));
            return "Résultat de la tâche";
        });

        logger.info("Tâche terminée ? {}", future.isDone()); // false

        try {
            // get() bloque jusqu'au résultat, avec timeout de sécurité
            String result = future.get(2, TimeUnit.SECONDS);
            logger.info("Résultat : {}", result);
        } catch (TimeoutException e) {
            future.cancel(true);
            logger.error("Timeout dépassé, tâche annulée");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Erreur lors de la récupération", e);
        }
    }
}
```

**Méthodes de `Future<T>` :**

| Méthode | Description |
|---|---|
| `get()` | Bloque jusqu'au résultat (indéfiniment) |
| `get(timeout, unit)` | Bloque avec un timeout maximum |
| `isDone()` | `true` si terminé (succès, erreur ou annulation) |
| `isCancelled()` | `true` si annulé |
| `cancel(mayInterrupt)` | Tente d'annuler la tâche |

---

#### Les types de pools disponibles

```java
// 1 seul thread — garantit l'exécution séquentielle des tâches
ExecutorService single = Executors.newSingleThreadExecutor();

// n threads fixes — recommandé pour les workloads stables
ExecutorService fixed = Executors.newFixedThreadPool(4);

// Pool élastique : crée à la demande, recycle les threads inactifs
// ⚠️ Pas de limite haute — risque de créer des milliers de threads sous charge
ExecutorService cached = Executors.newCachedThreadPool();

// Pour les tâches différées ou répétitives
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(2);
```

**Exemple de FixedThreadPool :**

```java
public void useCaseJava5Runnable() {
    try (ExecutorService executorService = Executors.newFixedThreadPool(5)) {
        for (int i = 1; i <= 10; i++) {
            executorService.execute(() ->
                logger.info("Hello from thread: {}", Thread.currentThread().getName())
            );
        }
    }
    // 10 tâches traitées par 5 threads réutilisés
}
```

---

#### ScheduledExecutorService

Pour les tâches différées ou répétitives — l'équivalent bas niveau du `@Scheduled` de Spring :

```java
try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {

    // Exécuter une fois, après 2 secondes
    scheduler.schedule(
        () -> System.out.println("Exécuté après 2s"),
        2, TimeUnit.SECONDS
    );

    // Répéter toutes les secondes (délai entre DÉMARRAGES)
    // ⚠️ Si la tâche prend 2s et le rate est 1s → chevauchement possible
    scheduler.scheduleAtFixedRate(
        () -> System.out.println("Taux fixe"),
        0, 1, TimeUnit.SECONDS
    );

    // Répéter 500ms APRÈS la fin de la tâche précédente
    // ✅ Pas de chevauchement possible
    scheduler.scheduleWithFixedDelay(
        () -> System.out.println("Délai fixe"),
        0, 500, TimeUnit.MILLISECONDS
    );

    Thread.sleep(5000);
}
```

| Méthode | Délai calculé depuis |
|---|---|
| `scheduleAtFixedRate` | Le démarrage de la tâche précédente |
| `scheduleWithFixedDelay` | La fin de la tâche précédente |

> 🎯 **CPU/I/O** : `ExecutorService` est adapté aux deux cas. Pour l'**I/O-bound**, le pool peut être plus grand que le nombre de cœurs (les threads attendent souvent). Pour le **CPU-bound**, on préférera un pool de taille égale au nombre de cœurs.

---

### 3.2 Concurrent Collections

Plusieurs threads accédant à la même collection peuvent provoquer des race conditions. Deux approches existent :

**Collections synchronisées** *(wrapper de `Collections`) :*

```java
List<String> syncList = Collections.synchronizedList(new ArrayList<>());
// Lock global sur chaque opération — lectures ET écritures bloquées
// Simple, mais performances limitées sous haute contention
```

**Collections concurrentes** *(conçues pour la concurrence) :*

| Classe | Particularité | Cas d'usage |
|---|---|---|
| `ConcurrentHashMap` | Lectures sans lock, écritures partitionnées | Map très lue et peu écrite |
| `CopyOnWriteArrayList` | Copie complète à chaque écriture | Lectures >> Écritures |
| `ConcurrentLinkedQueue` | File FIFO lock-free | Queue haute performance |
| `BlockingQueue` | Bloque producteur si pleine, consommateur si vide | Pattern producteur/consommateur |
| `ConcurrentSkipListMap` | Map triée et concurrente | Accès trié sous concurrence |

**Exemple de pattern Producteur/Consommateur avec `BlockingQueue` :**

```java
BlockingQueue<String> queue = new LinkedBlockingQueue<>(100); // capacité max = 100

// Producteur
Thread producer = Thread.ofPlatform().start(() -> {
    while (true) {
        queue.put("message"); // bloque si la queue est pleine (backpressure!)
    }
});

// Consommateur
Thread consumer = Thread.ofPlatform().start(() -> {
    while (true) {
        String msg = queue.take(); // bloque si la queue est vide
        process(msg);
    }
});
```

---

### 3.3 Synchronizers

Des utilitaires pour **coordonner plusieurs threads** entre eux :

**`CountDownLatch`** — "Attendre que N événements se produisent"

```java
// Attendre que 3 services démarrent avant de lancer l'application
CountDownLatch latch = new CountDownLatch(3);

executor.submit(() -> { startServiceA(); latch.countDown(); });
executor.submit(() -> { startServiceB(); latch.countDown(); });
executor.submit(() -> { startServiceC(); latch.countDown(); });

latch.await(); // le thread principal attend que les 3 services soient prêts
System.out.println("Tous les services démarrés !");
```

**`CyclicBarrier`** — "Tout le monde attend tout le monde, puis on repart"

```java
// Synchronisation par vague : 4 threads attendent qu'ils soient tous prêts
CyclicBarrier barrier = new CyclicBarrier(4, () ->
    System.out.println("Tous les threads ont atteint la barrière — on repart !")
);

for (int i = 0; i < 4; i++) {
    executor.submit(() -> {
        doPhase1Work();
        barrier.await(); // attend que les 4 threads soient là
        doPhase2Work();  // tous repartent ensemble
    });
}
```

**`Semaphore`** — "Maximum N accès simultanés à une ressource"

```java
// Limiter à 3 connexions simultanées vers un service externe
Semaphore semaphore = new Semaphore(3);

executor.submit(() -> {
    semaphore.acquire();         // attend un "ticket" disponible
    try {
        callExternalService();
    } finally {
        semaphore.release();     // rend le "ticket"
    }
});
```

---

### 3.4 Locks

Les `Lock` de Java 5 apportent ce que `synchronized` ne peut pas offrir :

| Fonctionnalité | `synchronized` | `ReentrantLock` |
|---|---|---|
| Timeout | ❌ | ✅ `tryLock(timeout)` |
| Tentative sans blocage | ❌ | ✅ `tryLock()` |
| Interruptible | ❌ | ✅ `lockInterruptibly()` |
| Lecture/Écriture séparés | ❌ | ✅ `ReadWriteLock` |
| Condition variables | Basique (`wait/notify`) | Avancée (`Condition`) |

**`ReentrantLock` — synchronized amélioré :**

```java
private final ReentrantLock lock = new ReentrantLock();

public void increment() {
    lock.lock();
    try {
        counter++;
    } finally {
        lock.unlock(); // TOUJOURS dans un finally
    }
}

// Avec timeout — ne bloque pas indéfiniment
public boolean tryIncrement() {
    if (lock.tryLock(500, TimeUnit.MILLISECONDS)) {
        try {
            counter++;
            return true;
        } finally {
            lock.unlock();
        }
    }
    return false; // n'a pas pu acquérir le lock
}
```

**`ReadWriteLock` — optimiser les accès en lecture :**

```java
private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
private final Map<String, String> cache = new HashMap<>();

// Plusieurs threads peuvent lire simultanément
public String get(String key) {
    rwLock.readLock().lock();
    try {
        return cache.get(key);
    } finally {
        rwLock.readLock().unlock();
    }
}

// L'écriture est exclusive — bloque toutes les lectures
public void put(String key, String value) {
    rwLock.writeLock().lock();
    try {
        cache.put(key, value);
    } finally {
        rwLock.writeLock().unlock();
    }
}
```

---

### 3.5 Volatile et Atomic

#### `volatile` — Garantit la visibilité

Sans `volatile`, chaque thread peut garder une copie locale d'une variable dans son cache CPU. `volatile` force les lectures et écritures à passer par la **mémoire principale**, garantissant que tous les threads voient la même valeur.

```java
// ✅ volatile : visibilité garantie — parfait pour un flag simple
private volatile boolean running = true;

// Thread A
public void stop() { running = false; }

// Thread B — verra la mise à jour sans délai
public void run() {
    while (running) { doWork(); }
}
```

```java
// ⚠️ volatile NE SUFFIT PAS pour les opérations composées
private volatile int counter = 0;
counter++; // toujours non-atomique : READ volatile → ADD → WRITE volatile
           // entre le READ et le WRITE, un autre thread peut modifier counter
```

#### `Atomic` — Visibilité ET atomicité

Les classes `Atomic` utilisent des instructions CPU de type **Compare-And-Swap (CAS)**, sans lock :

```
CAS(variable, valeurAttendue, nouvelleValeur) :
  si variable == valeurAttendue → écrire nouvelleValeur, retourner true
  sinon → ne rien faire, retourner false (et réessayer)
```

```java
// AtomicInteger — le compteur thread-safe par excellence
AtomicInteger counter = new AtomicInteger(0);

counter.incrementAndGet();         // ++counter (atomique)
counter.getAndIncrement();         // counter++ (atomique)
counter.addAndGet(5);              // counter += 5 (atomique)
counter.compareAndSet(10, 20);     // si counter==10 → counter=20

// Autres classes Atomic utiles
AtomicLong  total = new AtomicLong(0L);
AtomicBoolean flag = new AtomicBoolean(false);

// Pour les objets
AtomicReference<UserSession> session = new AtomicReference<>(null);
session.compareAndSet(null, new UserSession("Alice")); // init thread-safe
```

**Récapitulatif :**

| Mécanisme | Visibilité | Atomicité composée | Lock | Performance |
|---|---|---|---|---|
| `volatile` | ✅ | ❌ | ❌ | ⚡ Très haute |
| `synchronized` | ✅ | ✅ | ✅ | Moyenne |
| `AtomicInteger` | ✅ | ✅ | ❌ (CAS) | ⚡ Haute |
| `ReentrantLock` | ✅ | ✅ | ✅ | Moyenne+ |

> 🎯 **CPU-bound** : `Atomic` et `volatile` concernent les workloads où plusieurs threads partagent des compteurs ou des flags. Pour des sections critiques complexes, préférez `synchronized` ou `ReentrantLock`.

---

## 4. Java 7 — ForkJoinPool et Work-Stealing

### 4.1 Le concept Fork/Join

Le **Fork/Join framework** est conçu pour les tâches **CPU-bound récursives**. L'idée : une tâche trop large est **découpée (fork)** en sous-tâches plus petites, traitées en parallèle, puis les résultats sont **rassemblés (join)**.

```
                     SommeRecursive(1..1M)
                     /                  \
         SommeRecursive(1..500K)    SommeRecursive(500K..1M)
            /          \                /          \
    (1..250K)     (250K..500K)   (500K..750K)   (750K..1M)
       ...            ...            ...            ...
    [calcul]       [calcul]       [calcul]       [calcul]
        \              /               \              /
        250K           250K             250K          250K
              \                /
             500K            500K
                     \     /
                    1 000 000  ← résultat final
```

---

### 4.2 Work-Stealing — Maximiser l'utilisation des cœurs

Chaque thread du `ForkJoinPool` a sa propre **file de tâches** (deque). Si un thread finit toutes ses tâches pendant qu'un autre en a encore, il **vole** des tâches dans la file de l'autre, **par la fin** (pour éviter les conflits). Cela maximise l'utilisation de tous les cœurs disponibles.

```
Thread-0 : [tâche5][tâche4][tâche3]  ← travaille sur tâche3 (sa propre fin)
Thread-1 : []                         ← file vide, vole tâche5 (l'autre bout)
Thread-2 : [tâche8][tâche7]
Thread-3 : []                         ← file vide, vole tâche8
```

> 🎯 **CPU-bound** : ForkJoinPool est taillé pour les calculs intensifs parallélisables (tri, traitement d'image, agrégations massives...).
> **Ce n'est pas adapté aux tâches I/O-bound** qui bloquent les threads carrier sans libérer les cœurs CPU.

---

### 4.3 Implémentation

On étend `RecursiveAction` (sans retour) ou `RecursiveTask<T>` (avec retour) et on surcharge `compute()` :

```java
class SommeRecursive extends RecursiveTask<Long> {

    private final long[] array;
    private final int start, end;
    private static final int SEUIL = 1_000;

    SommeRecursive(long[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end   = end;
    }

    @Override
    protected Long compute() {
        if (end - start <= SEUIL) {
            // ① Assez petite → calcul direct (cas de base)
            long sum = 0;
            for (int i = start; i < end; i++) sum += array[i];
            return sum;
        }

        // ② Trop grande → on découpe (cas récursif)
        int mid = (start + end) / 2;
        SommeRecursive left  = new SommeRecursive(array, start, mid);
        SommeRecursive right = new SommeRecursive(array, mid, end);

        left.fork();                        // soumettre left en arrière-plan
        long rightResult = right.compute(); // traiter right dans ce thread
        long leftResult  = left.join();     // attendre le résultat de left

        return leftResult + rightResult;
    }
}

// Utilisation
ForkJoinPool pool = ForkJoinPool.commonPool();
long[] data = LongStream.rangeClosed(1, 1_000_000).toArray();
Long result = pool.invoke(new SommeRecursive(data, 0, data.length));
System.out.println("Somme : " + result); // 500000500000
```

> 💡 **Pourquoi `right.compute()` et non `right.fork()` ?**
> En traitant `right` dans le thread courant plutôt qu'en le forkant, on évite la surcharge d'une soumission supplémentaire. C'est le pattern recommandé : fork la première sous-tâche, compute la seconde.

---

### 4.4 API Fork/Join — Tableau de référence

| Méthode | Rôle | Appelé depuis |
|---|---|---|
| `fork()` | Soumet la tâche au pool (asynchrone) | Une tâche en cours |
| `join()` | Attend et récupère le résultat | Une tâche en cours |
| `compute()` | Exécute directement dans le thread courant | Une tâche en cours |
| `invoke(task)` | Lance et attend (fork + join) | Depuis l'extérieur du pool |
| `invokeAll(t1, t2)` | Forke deux tâches simultanément | Une tâche en cours |
| `managedBlock(blocker)` | Crée un thread supplémentaire si tous bloquent | Opération I/O dans le pool |

---

### 4.5 Le commonPool

Un `ForkJoinPool` partagé est créé automatiquement au démarrage de la JVM :

```java
ForkJoinPool commonPool = ForkJoinPool.commonPool();
int parallelism = commonPool.getParallelism(); // N-1 threads (N = nb de cœurs)
```

> ⚠️ **Attention au commonPool partagé**
> Le `ForkJoinPool.commonPool()` est utilisé par défaut par :
> - `parallelStream()`
> - `CompletableFuture.supplyAsync()` sans `Executor` explicite
>
> Ces deux usages se **concurrencent** pour les mêmes threads de travail. Sous charge, ils s'impactent mutuellement. Pour les workloads critiques, créez un `ForkJoinPool` dédié.

```java
// Pool dédié pour isoler les calculs critiques
ForkJoinPool customPool = new ForkJoinPool(
    Runtime.getRuntime().availableProcessors(),
    ForkJoinPool.defaultForkJoinWorkerThreadFactory,
    null,   // handler pour les exceptions non gérées
    false   // pas de mode asyncMode
);

Long result = customPool.invoke(new SommeRecursive(data, 0, data.length));
customPool.shutdown();
```

---

## 5. Combien de Platform Threads avoir ?

On entend souvent "2 threads par cœur", mais la réalité est plus nuancée. La bonne réponse dépend entièrement du **type de workload**.

---

### 5.1 CPU-bound : 1 thread par cœur

Pour des calculs intensifs, l'idéal est **1 thread par cœur CPU** logique. Avec davantage, le scheduler OS passe du temps à effectuer des **context switches** : sauvegarder l'état complet d'un thread, charger celui d'un autre. C'est du temps CPU dépensé à gérer les threads plutôt qu'à calculer.

```java
// ✅ Recommandé pour le CPU-bound
int nCores = Runtime.getRuntime().availableProcessors();
ExecutorService cpuPool = Executors.newFixedThreadPool(nCores);
```

> **Exemple :** avec 4 cœurs et 200 threads sur des calculs purs, on passe plus de temps à switcher de contexte qu'à calculer.

---

### 5.2 I/O-bound : bien plus de threads que de cœurs

Pour les workloads I/O-bound, les threads passent la plupart de leur temps à **attendre** (DB, HTTP, fichiers...). Un pool de 8 threads sera vite saturé si les appels prennent 2 secondes chacun — les requêtes s'accumulent en file d'attente.

```java
// ✅ Recommandé pour l'I/O-bound (adapter selon les latences mesurées)
ExecutorService ioPool = Executors.newFixedThreadPool(50);

// Règle empirique de départ :
// nbThreads = nbCœurs / (1 - facteurBlocking)
// facteurBlocking ≈ 0.9 pour I/O → nbThreads ≈ 10 × nbCœurs
```

---

### 5.3 La contrainte mémoire

Chaque platform thread consomme environ **2 Mo de stack** (configurable via `-Xss`). On ne peut donc pas créer arbitrairement de threads :

```
4 Go de RAM dédiée aux threads = environ 2 000 threads maximum
Tomcat plafonne par défaut à 200 threads
```

---

### 5.4 Deux pools séparés — la bonne pratique

La meilleure approche est d'avoir **deux pools distincts** pour ne pas mélanger les deux types de workloads :

```java
// Pool CPU : taille = nombre de cœurs
int nCores = Runtime.getRuntime().availableProcessors();
ExecutorService cpuPool = Executors.newFixedThreadPool(nCores);

// Pool I/O : taille plus grande, à calibrer selon les latences mesurées
ExecutorService ioPool = Executors.newFixedThreadPool(nCores * 10);
```

**Pourquoi les séparer ?**

Si une tâche I/O bloquante s'exécute dans le pool CPU, elle immobilise un thread carrier sans faire de calcul. Les autres tâches CPU se retrouvent en attente alors que les cœurs sont libres.

```
❌ Pool unique saturé par des I/O :

Thread-0 : [attend DB 2s...]        → cœur 0 libre mais thread bloqué
Thread-1 : [attend HTTP 1s...]      → cœur 1 libre mais thread bloqué
Thread-2 : [attend fichier 3s...]   → cœur 2 libre mais thread bloqué
Thread-3 : [calcul CPU en cours]    → seul thread utile

✅ Deux pools distincts :

cpuPool  → Thread-0,1,2,3 : calculs purs, cœurs pleinement utilisés
ioPool   → Thread-4..53   : attentes I/O, sans impacter le CPU
```

**Récapitulatif :**

| Workload | Taille recommandée | Raison |
|---|---|---|
| CPU-bound | `= nbCœurs` | Éviter les context switches |
| I/O-bound | `= nbCœurs / (1 - blockingFactor)` | Compenser les temps d'attente |
| Mixte | Deux pools séparés | Isolation des workloads |

> 💡 C'est exactement ce que **Project Reactor** propose avec ses schedulers : `Schedulers.parallel()` pour le CPU et `Schedulers.boundedElastic()` pour l'I/O. Les **Virtual Threads** (Java 21) résolvent ce problème différemment, en rendant les threads si légers qu'on peut en créer autant que nécessaire sans se soucier de la taille du pool.

---

## 6. Java 8 — CompletableFuture et parallelStream

### 6.1 Contexte et motivations

Java 8 opère un changement de paradigme avec les **lambdas** et les APIs fonctionnelles. `CompletableFuture` répond à deux limitations majeures de `Future` :

**Limitation 1 — `Future` est bloquant**

```java
Future<String> future = executor.submit(() -> fetchUser());
String user = future.get(); // ← BLOQUE le thread appelant jusqu'au résultat
// Le thread est immobilisé pendant toute la durée de la requête
```

**Limitation 2 — `Future` n'est pas composable**

```java
// Impossible de faire élégamment :
// "Quand le user est récupéré, fetch ses commandes, puis génère une facture"
// On serait obligé d'appeler get() à chaque étape → cascade de blocages
```

`CompletableFuture` résout les deux avec une API **non-bloquante et fluent** :

```java
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> fetchUserFromDB(userId))            // ① démarre en async
    .thenApply(user -> enrichWithPreferences(user))        // ② transformation
    .thenCompose(user -> fetchOrdersAsync(user.getId()))   // ③ chaînage async
    .handle((res, ex) -> {                                 // ④ gestion erreur
        if (ex != null) return "Valeur de repli : " + ex.getMessage();
        return res.toUpperCase();
    });

// Le thread appelant n'est jamais bloqué !
// Chaque étape s'exécute dès que la précédente est terminée.
```

---

### 6.2 Les opérateurs de transformation

#### Transformation simple — `thenApply`

```java
CompletableFuture<String> upper = CompletableFuture
    .supplyAsync(() -> "hello world")
    .thenApply(String::toUpperCase); // "HELLO WORLD"
```

#### Chaînage async — `thenCompose`

Quand la transformation elle-même est asynchrone (retourne un `CompletableFuture`), `thenApply` produirait un `CompletableFuture<CompletableFuture<T>>`. `thenCompose` "aplatit" ce résultat :

```java
// ❌ thenApply avec une fonction async → CompletableFuture<CompletableFuture<Orders>>
CompletableFuture<CompletableFuture<Orders>> nested =
    userFuture.thenApply(user -> fetchOrdersAsync(user.getId()));

// ✅ thenCompose → CompletableFuture<Orders>
CompletableFuture<Orders> flat =
    userFuture.thenCompose(user -> fetchOrdersAsync(user.getId()));
```

#### Gestion des erreurs — `handle`, `exceptionally`, `whenComplete`

```java
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> riskyOperation())

    // ① handle : reçoit (résultat, exception) — toujours appelé
    .handle((res, ex) -> {
        if (ex != null) return "valeur par défaut";
        return res;
    })

    // ② exceptionally : appelé uniquement en cas d'erreur
    .exceptionally(ex -> {
        logger.error("Erreur : {}", ex.getMessage());
        return "fallback";
    })

    // ③ whenComplete : observation sans transformation (comme un finally)
    .whenComplete((res, ex) -> {
        if (ex != null) logger.error("Échec", ex);
        else logger.info("Succès : {}", res);
    });
```

---

### 6.3 Variantes Async — Contrôle du thread d'exécution

La plupart des méthodes ont trois variantes pour contrôler **quel thread** exécute l'étape :

| Signature | Thread d'exécution |
|---|---|
| `thenApply(fn)` | Thread qui a complété l'étape précédente |
| `thenApplyAsync(fn)` | Thread du `ForkJoinPool.commonPool()` |
| `thenApplyAsync(fn, executor)` | Thread de l'`Executor` fourni |

```java
ExecutorService ioPool  = Executors.newFixedThreadPool(20); // pour l'I/O
ExecutorService cpuPool = Executors.newFixedThreadPool(4);  // pour le CPU

CompletableFuture
    .supplyAsync(() -> fetchFromDB(), ioPool)          // I/O
    .thenApplyAsync(data -> transform(data), cpuPool)  // CPU
    .thenApplyAsync(result -> saveToFile(result), ioPool)  // I/O
    .thenAccept(path -> logger.info("Sauvegardé : {}", path));
```

> 💡 En utilisant des pools dédiés, on évite de polluer le `commonPool` (partagé avec `parallelStream`) avec des tâches I/O bloquantes.

---

### 6.4 Combinaison de plusieurs CompletableFuture

#### Combiner deux résultats — `thenCombine`

```java
CompletableFuture<String>  userFuture   = CompletableFuture.supplyAsync(() -> fetchUser(userId));
CompletableFuture<Integer> creditFuture = CompletableFuture.supplyAsync(() -> fetchCredit(userId));

// Les deux s'exécutent en parallèle, on combine quand les deux sont prêts
CompletableFuture<String> combined = userFuture.thenCombine(
    creditFuture,
    (user, credit) -> user + " a un crédit de " + credit + "€"
);
```

#### Attendre toutes les tâches — `allOf`

```java
CompletableFuture<String>  userFuture      = CompletableFuture.supplyAsync(() -> fetchUser());
CompletableFuture<Integer> orderFuture     = CompletableFuture.supplyAsync(() -> fetchOrders());
CompletableFuture<String>  inventoryFuture = CompletableFuture.supplyAsync(() -> fetchInventory());

CompletableFuture<Void> all = CompletableFuture.allOf(
    userFuture, orderFuture, inventoryFuture
);

// Quand tout est prêt, récupérer les résultats
all.thenRun(() -> {
    String  user      = userFuture.join();      // join() sans blocage ici (déjà terminé)
    Integer orders    = orderFuture.join();
    String  inventory = inventoryFuture.join();
    buildDashboard(user, orders, inventory);
});
```

#### Prendre le premier — `anyOf` et `applyToEither`

```java
// anyOf : premier parmi N futures (retourne Object — peu typé)
CompletableFuture<Object> fastest = CompletableFuture.anyOf(
    future1, future2, future3
);

// applyToEither : premier entre deux futures (typé)
CompletableFuture<String> result = serviceA.applyToEither(
    serviceB,
    response -> response.toUpperCase() // transformation du premier résultat reçu
);
```

---

### 6.5 parallelStream

`parallelStream()` est la façon la plus simple d'exploiter plusieurs cœurs sur une collection. En interne, il utilise le **`ForkJoinPool.commonPool()`** pour distribuer les opérations entre les threads disponibles.

```java
List<Integer> numbers = IntStream.rangeClosed(1, 1_000_000)
    .boxed()
    .collect(Collectors.toList());

// Stream séquentiel — un seul thread
long sumSeq = numbers.stream()
    .mapToLong(Integer::longValue)
    .sum();

// Stream parallèle — distribué sur N-1 threads du commonPool
long sumPar = numbers.parallelStream()
    .mapToLong(Integer::longValue)
    .sum();
```

> 🎯 **CPU-bound** : `parallelStream` brille sur des collections larges avec des opérations de **calcul pur** (map mathématique, filter, reduce). Pour de l'I/O-bound (ex: requête DB par élément), préférez un `ExecutorService` dédié — les threads du `commonPool` ne doivent pas être bloqués sur des I/O.

#### ⚠️ Opérations à éviter ou à utiliser avec précaution

```java
// ❌ forEach — ordre non garanti, résultats incohérents
list.parallelStream().forEach(System.out::println); // ordre aléatoire

// ✅ forEachOrdered — préserve l'ordre, mais réduit le bénéfice du parallélisme
list.parallelStream().forEachOrdered(System.out::println);

// ❌ collect dans une collection non thread-safe — race condition !
List<String> result = new ArrayList<>();
list.parallelStream().forEach(result::add); // DANGEREUX

// ✅ Collectors thread-safe
List<String> safeResult = list.parallelStream()
    .collect(Collectors.toList());

// ❌ limit() et skip() — coordination coûteuse entre threads
list.parallelStream().limit(10).collect(...); // souvent plus lent qu'en séquentiel

// ❌ findFirst() — force un ordre, pénalise le parallélisme
Optional<String> first = list.parallelStream().findFirst();

// ✅ findAny() — prend le premier disponible, vraiment parallèle
Optional<String> any = list.parallelStream().findAny();
```

**Quand `parallelStream` aide vraiment :**

| Condition | Aide ? |
|---|---|
| Collection large (> 10 000 éléments) | ✅ Oui |
| Opérations CPU pures (calculs, transformations) | ✅ Oui |
| Opérations I/O (appels DB, HTTP...) | ❌ Non |
| Collections petites | ❌ Non (overhead > gain) |
| Ordre de traitement important | ⚠️ Possible mais coûteux |
| État partagé mutable | ❌ Non (race conditions) |

---

### 6.6 Limites de CompletableFuture

`CompletableFuture` est puissant mais présente trois limitations importantes qui motivent l'introduction de la Flow API en Java 9.

**Limite 1 — Pas de lazy computation**

Le calcul démarre **immédiatement** à l'appel de `supplyAsync()`, même si personne ne consomme encore le résultat.

```java
// Le calcul démarre immédiatement, même si on n'utilise jamais le résultat
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
    System.out.println("Calcul démarré !"); // affiché immédiatement
    return expensiveComputation();
});
// Aucun moyen de dire "démarre seulement quand j'en ai besoin"
```

**Limite 2 — Un seul résultat**

`CompletableFuture<T>` ne peut émettre **qu'une seule valeur** (ou une exception). Impossible de modéliser un flux de données continu.

```java
// On récupère TOUT en une fois → problème si le flux est infini ou très large
CompletableFuture<List<Event>> events =
    CompletableFuture.supplyAsync(() -> fetchAllEvents());

// Impossible de traiter les événements au fur et à mesure de leur arrivée
```

**Limite 3 — Pas de backpressure**

Si un producteur envoie des données plus vite que le consommateur ne peut les traiter, `CompletableFuture` n'offre aucun mécanisme pour ralentir le producteur.

```java
// ❌ Pas de contrôle du débit : le producteur ne peut pas être ralenti
for (int i = 0; i < 1_000_000; i++) {
    CompletableFuture
        .supplyAsync(() -> produceEvent(i))
        .thenAccept(event -> slowConsumer(event)); // le consommateur déborde !
}
// Résultat possible : saturation mémoire, OOMError, dégradation des performances
```

**Récapitulatif des limites :**

| Limitation | Impact | Solution apportée par |
|---|---|---|
| Eager (pas de lazy) | Gaspillage de ressources | Flow API (Java 9) |
| Un seul résultat | Pas de flux continu | Flow API (Java 9) |
| Pas de backpressure | Saturation du consommateur | Flow API (Java 9) |

> Ces trois limitations motivent la création de la **Flow API** en Java 9, conçue pour les flux de données asynchrones, paresseux et avec backpressure. Nous la détaillerons dans le chapitre suivant.

---

## 7. Java 9 — Flow API et Reactive Streams

### 7.1 Pourquoi la Flow API ?

Comme vu en 6.6, `CompletableFuture` ne peut pas modéliser un **flux de données continu avec backpressure**. La Flow API répond à ce besoin avec quatre interfaces standardisées, inspirées du standard **Reactive Streams** (initiative commune de Netflix, Pivotal, Lightbend en 2013).

```
Reactive Streams (standard)  →  Java 9 Flow API  →  Implémentations
                                                      ├── Project Reactor (Spring)
                                                      ├── RxJava
                                                      └── Akka Streams
```

> 💡 La Flow API est une **spécification**, pas une implémentation complète. Elle définit le contrat entre producteur et consommateur. Les bibliothèques comme Project Reactor en fournissent les implémentations prêtes à l'emploi.

---

### 7.2 Les quatre interfaces fondamentales

```java
// ① Publisher — produit les données à la demande
@FunctionalInterface
public interface Publisher<T> {
    void subscribe(Subscriber<? super T> subscriber);
}

// ② Subscriber — consomme les données
public interface Subscriber<T> {
    void onSubscribe(Subscription subscription); // appelé à l'abonnement
    void onNext(T item);                         // appelé pour chaque élément
    void onError(Throwable throwable);           // appelé en cas d'erreur
    void onComplete();                           // appelé en fin de flux
}

// ③ Subscription — lien entre Publisher et Subscriber, contrôle le débit
public interface Subscription {
    void request(long n); // demander n éléments supplémentaires (backpressure !)
    void cancel();        // annuler l'abonnement
}

// ④ Processor — transformation : Subscriber + Publisher à la fois
public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {}
```

---

### 7.3 Le protocole Reactive Streams

Le flux de communication suit un protocole strict :

```
Publisher                                    Subscriber
    │                                             │
    │ ← ← ← ← ← subscribe(subscriber) ← ← ← ← │
    │                                             │
    │ → onSubscribe(subscription) → → → → → → → │
    │                                             │
    │ ← ← ← ← ← subscription.request(3) ← ← ← │  (le sub demande 3 éléments)
    │                                             │
    │ → → → → → → onNext(item1) → → → → → → → → │
    │ → → → → → → onNext(item2) → → → → → → → → │
    │ → → → → → → onNext(item3) → → → → → → → → │
    │                                             │
    │ ← ← ← ← ← subscription.request(2) ← ← ← │  (le sub redemande)
    │                                             │
    │ → → → → → → onNext(item4) → → → → → → → → │
    │ → → → → → → onComplete() → → → → → → → → │  (flux terminé)
```

> 🎯 C'est le mécanisme de **backpressure** : le consommateur dit au producteur combien d'éléments il peut traiter. Le producteur ne peut jamais en envoyer plus que ce qui est demandé.

---

### 7.4 SubmissionPublisher — L'implémentation fournie par Java 9

Java 9 fournit une seule implémentation concrète : `SubmissionPublisher<T>`.

```java
public void useCaseFlowApi() throws InterruptedException {

    // Créer le Publisher
    SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

    // Créer et abonner un Subscriber personnalisé
    Flow.Subscriber<String> subscriber = new Flow.Subscriber<>() {

        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1); // demander le premier élément
            logger.info("Abonnement établi");
        }

        @Override
        public void onNext(String item) {
            logger.info("Reçu : {}", item);
            subscription.request(1); // demander le prochain après traitement
        }

        @Override
        public void onError(Throwable throwable) {
            logger.error("Erreur dans le flux", throwable);
        }

        @Override
        public void onComplete() {
            logger.info("Flux terminé !");
        }
    };

    publisher.subscribe(subscriber);

    // Émettre des données
    publisher.submit("Article 1");
    publisher.submit("Article 2");
    publisher.submit("Article 3");

    // Fermer proprement le flux
    publisher.close(); // déclenche onComplete()

    // Attendre que le subscriber finisse de traiter
    Thread.sleep(Duration.ofMillis(500));
}
```

---

### 7.5 Transformer un flux — Processor

Un `Processor` s'intercale entre Publisher et Subscriber pour transformer les données :

```java
// Processor qui convertit String en Integer (longueur de la chaîne)
class LengthProcessor extends SubmissionPublisher<Integer>
        implements Flow.Processor<String, Integer> {

    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE); // demander tout
    }

    @Override
    public void onNext(String item) {
        submit(item.length()); // émettre la longueur en aval
    }

    @Override
    public void onError(Throwable throwable) {
        closeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        close();
    }
}

// Utilisation : Publisher<String> → Processor → Subscriber<Integer>
SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
LengthProcessor processor = new LengthProcessor();

publisher.subscribe(processor);  // brancher le processor en aval du publisher
processor.subscribe(mySubscriber); // brancher le subscriber en aval du processor

publisher.submit("Hello");   // → processor reçoit "Hello" → émet 5
publisher.submit("Java");    // → processor reçoit "Java"  → émet 4
publisher.close();
```

---

### 7.6 Flow API vs Project Reactor

La Flow API est une **spécification minimale**. En pratique, on utilise Project Reactor (Spring) ou RxJava qui offrent des centaines d'opérateurs prêts à l'emploi :

| Fonctionnalité | Flow API (Java 9) | Project Reactor |
|---|---|---|
| `map`, `filter`, `flatMap` | ❌ À coder manuellement | ✅ Intégré |
| Gestion des erreurs | Basique | ✅ Riche (`retry`, `onErrorResume`...) |
| Scheduler / threading | Manuel | ✅ `subscribeOn`, `publishOn` |
| Backpressure | ✅ Protocole standard | ✅ Automatique |
| Interopérabilité | Standard JDK | Compatible Flow API |

```java
// Équivalent Project Reactor du flux ci-dessus — bien plus concis
Flux.just("Article 1", "Article 2", "Article 3")
    .map(String::toUpperCase)
    .filter(s -> s.length() > 5)
    .flatMap(s -> fetchDetailsAsync(s))
    .subscribe(
        item  -> logger.info("Reçu : {}", item),
        error -> logger.error("Erreur", error),
        ()    -> logger.info("Terminé !")
    );
```

> 🎯 **I/O-bound** : La Flow API et ses implémentations sont taillées pour les **flux de données asynchrones I/O-bound** — événements WebSocket, flux Kafka, résultats de requêtes paginées, SSE (Server-Sent Events)...

---

## 8. Java 21 — Virtual Threads, Structured Concurrency et Scoped Values

### 8.1 Le problème fondamental des Platform Threads

Rappel de la contrainte évoquée en section 5 :

```
1 Platform Thread  ≈  1 Thread OS  ≈  ~2 Mo de stack
4 Go RAM allouée   ≈  ~2 000 threads simultanés maximum
```

Pour une application qui reçoit 10 000 requêtes concurrentes (chacune faisant des appels DB et HTTP), il faudrait 10 000 threads OS — impossible. Les solutions historiques ont été :

- **Pools de threads limités** — file d'attente si le pool est saturé
- **Programmation réactive** (Project Reactor) — complexe, difficile à déboguer

Les **Virtual Threads** (Java 21, stable) apportent une troisième voie.

---

### 8.2 Virtual Threads — Architecture

Un Virtual Thread est un thread **géré par la JVM**, monté sur un **Platform Thread carrier** du `ForkJoinPool`. La JVM peut en créer des **millions** car ils ne correspondent pas à des threads OS.

```
JVM                         OS
┌──────────────────────────────────────────┐
│  Virtual Thread 1  ─── mounted on ──► Platform Thread (carrier) 1 ──► CPU Core 1
│  Virtual Thread 2  ─── mounted on ──► Platform Thread (carrier) 2 ──► CPU Core 2
│  Virtual Thread 3  ─── waiting I/O ──► unmounted (libère le carrier)
│  Virtual Thread 4  ─── waiting I/O ──► unmounted (libère le carrier)
│  ...                                                                  │
│  Virtual Thread N  (en attente, aucun carrier consommé)               │
└──────────────────────────────────────────┘
```

**Ce qui se passe lors d'une opération bloquante :**

```
① Virtual Thread appelle socket.read() (opération bloquante)
② La JVM détecte le blocage → démonte le VT du carrier
③ Le carrier est immédiatement disponible pour un autre VT
④ Quand le résultat arrive → la JVM remonte le VT sur un carrier disponible
⑤ Le VT reprend exactement là où il s'était arrêté
```

> 💡 Ce mécanisme s'appelle **park/unpark**. Il est transparent pour le développeur — on écrit du code bloquant classique, la JVM gère le reste.

---

### 8.3 Créer des Virtual Threads

```java
// ① Thread.ofVirtual() — création unitaire
Thread vt = Thread.ofVirtual()
        .name("VT-Lorenzo-", 0)
        .start(() -> logger.info("Hello depuis un Virtual Thread !"));

// ② Executor dédié Virtual Threads — UN NOUVEAU THREAD PAR TÂCHE
// C'est le pattern recommandé : pas de pool fixe, on crée autant que nécessaire
try (ExecutorService vte = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 100_000; i++) {
        vte.submit(() -> {
            Thread.sleep(Duration.ofMillis(100)); // simule de l'I/O
            logger.info("Tâche traitée sur : {}", Thread.currentThread());
        });
    }
} // fermeture propre : attend la fin de toutes les tâches

// ③ ThreadFactory pour intégration avec les frameworks
ThreadFactory vtFactory = Thread.ofVirtual()
        .name("VT-", 0)
        .factory();
```

> ⚠️ **Pas de pool de Virtual Threads**
> `newVirtualThreadPerTaskExecutor()` crée un nouveau VT par tâche — c'est voulu. Les VT étant quasi-gratuits à créer (~quelques Ko), les pooler n'apporte aucun bénéfice et empêche certaines optimisations JVM.

---

### 8.4 Virtual Threads vs Platform Threads — Benchmark comparatif

```java
// Simulation : 10 000 tâches, chacune attendant 1 seconde (I/O simulé)

// Avec Platform Threads (pool de 200)
try (ExecutorService pt = Executors.newFixedThreadPool(200)) {
    long start = System.currentTimeMillis();
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 10_000; i++) {
        futures.add(pt.submit(() -> Thread.sleep(Duration.ofSeconds(1))));
    }
    for (Future<?> f : futures) f.get();
    logger.info("Platform Threads : {}ms", System.currentTimeMillis() - start);
    // Résultat typique : ~50 secondes (10000/200 vagues × 1 seconde)
}

// Avec Virtual Threads
try (ExecutorService vte = Executors.newVirtualThreadPerTaskExecutor()) {
    long start = System.currentTimeMillis();
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 10_000; i++) {
        futures.add(vte.submit(() -> Thread.sleep(Duration.ofSeconds(1))));
    }
    for (Future<?> f : futures) f.get();
    logger.info("Virtual Threads : {}ms", System.currentTimeMillis() - start);
    // Résultat typique : ~1 seconde (tout en parallèle)
}
```

---

### 8.5 Pinning — La mise en garde principale

Un Virtual Thread est **pinnée** (coincée sur son carrier) dans deux situations :

```java
// ❌ Situation 1 : synchronized dans un bloc I/O bloquant
public synchronized String fetchData() {
    return httpClient.send(request).body(); // bloque ET pine le carrier !
    // Le carrier ne peut pas servir d'autres VT pendant toute la durée du call
}

// ✅ Solution : remplacer synchronized par ReentrantLock
private final ReentrantLock lock = new ReentrantLock();

public String fetchData() {
    lock.lock();
    try {
        return httpClient.send(request).body(); // VT démontable si I/O
    } finally {
        lock.unlock();
    }
}

// ❌ Situation 2 : méthodes natives (JNI)
// Inévitable — à minimiser dans les chemins critiques
```

> 💡 Java 24+ améliore ce point : `synchronized` ne pine plus dans la plupart des cas. Pour Java 21-23, surveiller avec `-Djdk.tracePinnedThreads=full`.

---

### 8.6 Ce que Virtual Threads NE remplacent pas

| Cas | Virtual Threads | Recommandation |
|---|---|---|
| I/O-bound (DB, HTTP, fichiers) | ✅ Excellent | Utiliser les VT |
| CPU-bound (calculs intensifs) | ❌ Pas d'apport | Rester sur `ForkJoinPool` |
| Backpressure sur flux de données | ❌ Pas de mécanisme | Flow API / Project Reactor |
| Flux infinis ou continus | ❌ Pas adapté | Reactive Streams |
| Composition fluent d'async | Possible mais verbeux | `CompletableFuture` ou Reactor |

---

### 8.7 Structured Concurrency (Java 21 Preview — Stable Java 25)

#### Le problème : le cycle de vie des tâches

Avec `ExecutorService`, les tâches enfants ont un cycle de vie **découplé** du code qui les crée :

```java
// ❌ Problème : que se passe-t-il si fetchUser() réussit mais fetchOrders() échoue ?
try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<User>   userFuture   = exec.submit(() -> fetchUser(userId));
    Future<Orders> ordersFuture = exec.submit(() -> fetchOrders(userId));

    User   user   = userFuture.get();   // et si ça lève une exception ?
    Orders orders = ordersFuture.get(); // l'autre tâche tourne encore !
    // Pas d'annulation automatique, pas de nettoyage garanti
}
```

**Structured Concurrency** impose une règle simple : **une tâche enfant ne peut pas survivre à sa tâche parente**. Comme les blocs `try-with-resources` garantissent la fermeture des ressources, `StructuredTaskScope` garantit l'arrêt des sous-tâches.

---

#### ShutdownOnFailure — Tout ou rien

```java
// Si UNE tâche échoue → toutes les autres sont annulées
public UserDashboard buildDashboard(long userId) throws Exception {

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

        // Forker les sous-tâches — elles démarrent immédiatement (sur des VT)
        StructuredTaskScope.Subtask<User>      userTask      = scope.fork(() -> fetchUser(userId));
        StructuredTaskScope.Subtask<Orders>    ordersTask    = scope.fork(() -> fetchOrders(userId));
        StructuredTaskScope.Subtask<Inventory> inventoryTask = scope.fork(() -> fetchInventory(userId));

        // Attendre que TOUTES les tâches soient terminées (ou l'une échoue)
        scope.join()
             .throwIfFailed(); // relance la première exception rencontrée

        // Si on arrive ici, tout a réussi
        return new UserDashboard(
            userTask.get(),
            ordersTask.get(),
            inventoryTask.get()
        );

    } // fermeture : annule toutes les tâches encore actives si on sort en exception
}
```

**Comportement :**

```
fetchUser()      ──────── OK (500ms)  ──────────────┐
fetchOrders()    ──── ÉCHEC (200ms)   → annule les 2 autres
fetchInventory() ──────────────────── (annulé à 200ms)

→ throwIfFailed() relance l'exception de fetchOrders()
→ fetchUser() et fetchInventory() proprement annulés
```

---

#### ShutdownOnSuccess — Prendre le plus rapide

```java
// Dès qu'UNE tâche réussit → les autres sont annulées
public String fetchFastestQuote(String productId) throws Exception {

    try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {

        scope.fork(() -> fetchFromSupplierA(productId)); // peut prendre 300ms
        scope.fork(() -> fetchFromSupplierB(productId)); // peut prendre 150ms
        scope.fork(() -> fetchFromSupplierC(productId)); // peut prendre 500ms

        scope.join(); // attend le premier succès

        return scope.result(); // retourne le résultat du gagnant
        // Les autres sont annulés automatiquement à la fermeture du scope
    }
}
```

---

#### Le principe de Structured Concurrency

```
void traiterCommande() {                   ← tâche parente
    try (var scope = new StructuredTaskScope...) {
        scope.fork(() -> validerStock());  ← enfant 1
        scope.fork(() -> calculerPrix()); ← enfant 2
        scope.fork(() -> notifierClient());← enfant 3
        scope.join();
    }
    // ICI : garantie que tous les enfants sont terminés (succès, erreur ou annulés)
}
```

> 🎯 **Observabilité améliorée** : avec Structured Concurrency, un thread dump JVM montre clairement la hiérarchie parent → enfants, facilitant le débogage.

---

### 8.8 Scoped Values (Java 21 Preview — Stable Java 25)

#### Le problème avec ThreadLocal

`ThreadLocal` est le mécanisme historique pour partager des données dans le contexte d'un thread (utilisateur courant, locale, contexte de sécurité...). Il souffre de trois problèmes avec les Virtual Threads :

```java
// ❌ Problèmes de ThreadLocal
ThreadLocal<User> currentUser = new ThreadLocal<>();

// 1. Mutabilité — n'importe quel code peut écraser la valeur
currentUser.set(adminUser); // écrase la valeur précédente sans avertissement

// 2. Héritage coûteux — InheritableThreadLocal copie TOUTES les valeurs
//    dans les threads enfants → problème avec des millions de VT
InheritableThreadLocal<User> inheritedUser = new InheritableThreadLocal<>();

// 3. Fuite mémoire — si remove() est oublié, la valeur reste en mémoire
//    tant que le thread vit (Platform Thread réutilisé dans un pool → jamais)
currentUser.set(user);
// ... oubli de currentUser.remove() → fuite
```

#### ScopedValue — La solution

```java
// Déclaration — immuable par design
static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();
static final ScopedValue<RequestContext> REQUEST_CTX = ScopedValue.newInstance();

// Binding — la valeur est visible uniquement dans le scope délimité
public void handleRequest(HttpRequest request) {
    User user = authenticate(request);

    ScopedValue.runWhere(CURRENT_USER, user, () -> {
        // CURRENT_USER vaut `user` uniquement dans ce bloc et ses appelants
        processRequest(request);
        // Hors de ce bloc → CURRENT_USER n'est plus défini
    });
}

// N'importe où dans la pile d'appels sous handleRequest()
public void processPayment(Order order) {
    User user = CURRENT_USER.get(); // toujours le bon user, sans le passer en paramètre
    logger.info("Paiement traité pour : {}", user.getName());
}
```

**Combinaison avec Structured Concurrency — héritage naturel :**

```java
ScopedValue.runWhere(CURRENT_USER, user, () -> {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

        // CURRENT_USER est automatiquement disponible dans les sous-tâches !
        scope.fork(() -> {
            User u = CURRENT_USER.get(); // ✅ reçu automatiquement
            return validateOrder(u);
        });

        scope.fork(() -> {
            User u = CURRENT_USER.get(); // ✅ reçu automatiquement
            return checkInventory(u);
        });

        scope.join().throwIfFailed();
    }
});
```

**ScopedValue vs ThreadLocal — Comparatif :**

| Critère | `ThreadLocal` | `ScopedValue` |
|---|---|---|
| Mutabilité | ✅ Mutable | ❌ Immuable (par design) |
| Portée | Durée de vie du thread | Bloc de code délimité |
| Héritage aux sous-tâches | Coûteux (copie complète) | ✅ Efficace et automatique |
| Risque de fuite mémoire | ✅ Oui (remove() oublié) | ❌ Non (scope borné) |
| Performance avec VT | Problématique | ✅ Optimisé |

---

### 8.9 Vision d'ensemble — Quand utiliser quoi en Java 21+

```
Besoin                                     Solution recommandée
──────────────────────────────────────────────────────────────────────
Tâche I/O-bound simple                  →  Virtual Thread
10 000 requêtes HTTP/DB concurrentes    →  newVirtualThreadPerTaskExecutor()
Tâche CPU-bound intensive               →  ForkJoinPool / parallelStream
Plusieurs sous-tâches liées (tout/rien) →  StructuredTaskScope.ShutdownOnFailure
Prendre le résultat le plus rapide      →  StructuredTaskScope.ShutdownOnSuccess
Flux de données continu + backpressure  →  Flow API / Project Reactor
Contexte de requête partagé             →  ScopedValue
Contexte mutable par thread             →  ThreadLocal (cas legacy)
Composition complexe d'async            →  CompletableFuture ou Reactor
```

---

## Bonus — Le modèle Actor

### Le principe

Là où toutes les approches vues jusqu'ici tentent de **gérer** le partage d'état entre threads, le modèle Actor l'**élimine par design**, proposé par Carl Hewitt au MIT en 1973.

Un Actor est une unité de calcul autonome avec trois propriétés fondamentales :
- Un **état strictement privé** — jamais accessible de l'extérieur
- Une **mailbox** — une file de messages entrants
- Un **comportement** — il traite les messages un par un, séquentiellement

Les actors ne se parlent **jamais directement** : ils s'envoient des **messages asynchrones**. Chaque actor étant mono-thread sur son état, il n'y a **aucun besoin de lock ou de synchronisation** par construction.

**Akka** (ou son fork open-source **Apache Pekko** depuis le changement de licence de 2022) est l'implémentation de référence sur la JVM. L'écosystème va bien au-delà des actors simples : clustering multi-nœuds, sharding, event sourcing, streaming réactif...

---

### Quand c'est pertinent

```
✅ Entités métier nombreuses avec état isolé
   (ex: un actor par serveur, qui gère ses tables, sans avoir à se coordonner avec les autres)
✅ Concurrence élevée sans vouloir gérer les locks manuellement
✅ Systèmes distribués qui doivent tolérer les pannes
✅ Workflows longs avec états multiples
```

---

### Quand ça devient contre-productif

Le modèle Actor repose sur une **promesse implicite** : les messages échangés sont des données immutables. Si cette promesse est brisée, toutes les garanties s'effondrent.

**Le piège classique — envoyer un objet mutable :**
```
Actor A crée une List<Order> et l'envoie à Actor B
→ A et B ont tous les deux une référence vers la même liste
→ Si A continue de modifier la liste pendant que B la lit
→ Race condition classique — exactement ce qu'on voulait éviter
```

Les autres situations où le modèle devient pénalisant :

```
⚠️ CRUD simple : overhead conceptuel et technique injustifié
⚠️ Besoin de résultats synchrones : le modèle est async-first,
   forcer du synchrone dessus est un anti-pattern douloureux
⚠️ Équipe non habituée : penser en messages/comportements
   demande un vrai changement de paradigme
⚠️ Workload CPU-bound pur : les actors n'apportent rien ici,
   un ForkJoinPool est plus adapté
```

> 🎯 Le modèle Actor et les Virtual Threads ne sont **pas en compétition** — ils opèrent à des niveaux différents. Akka peut d'ailleurs utiliser les VT comme substrate d'exécution. Les VT sont un **mécanisme**, les actors sont une **façon de structurer** l'architecture.

---

## Conclusion — L'évolution complète

| Version | Apport principal | Problème résolu |
|---|---|---|
| Java 1.0 | `Thread`, `Runnable`, `synchronized` | Concurrence de base |
| Java 5 | `ExecutorService`, `Future`, `Concurrent Collections`, `Atomic` | Gestion industrielle des threads |
| Java 7 | `ForkJoinPool`, Work-Stealing | Parallélisme récursif CPU-bound |
| Java 8 | `CompletableFuture`, `parallelStream` | Async composable, parallélisme simple |
| Java 9 | `Flow API` | Flux continu, backpressure standardisée |
| Java 21 | Virtual Threads | I/O-bound scalable sans réactif |
| Java 21 | Structured Concurrency | Cycle de vie des tâches garanti |
| Java 21 | Scoped Values | Contexte immutable partagé efficacement |

> **Le message central** : il n'existe pas de solution universelle. Choisir le bon outil selon la nature du workload (CPU vs I/O), le volume de concurrence, et les besoins de composition est la compétence clé d'un développeur Java senior.

---

### ⚠️ La nuance essentielle : la théorie ne suffit pas

Tout ce qui précède est de la **théorie** — des règles, des heuristiques, des bonnes pratiques. Elles sont utiles pour orienter les choix, mais elles ne remplacent pas **la mesure en conditions réelles**.

En production, les choses se compliquent rapidement :

**Les ressources sont partagées**

```
Serveur physique
├── Application A  (CPU-bound, calculs de reporting)
├── Application B  (I/O-bound, API REST vers 3 bases de données)
├── Application C  (traitement de flux Kafka)
└── JVM de monitoring, agents APM, sidecars...

→ Le ForkJoinPool.commonPool() de l'application A
  concurrence avec celui de l'application B
→ Les cœurs CPU disponibles ne sont pas ceux annoncés par
  Runtime.getRuntime().availableProcessors()
→ La bande passante réseau est partagée entre toutes les apps
```

**Les hypothèses théoriques tombent souvent**

```
Théorie :  nbThreads = nbCœurs / (1 - blockingFactor)
Réalité :  le blockingFactor varie selon la charge,
           la latence réseau du moment,
           l'état du pool de connexions DB,
           les GC pauses, les pics de trafic...
```

**Ce qui marche en isolation peut dégrader en cohabitation**

```
✅ Des millions de Virtual Threads sur une appli seule  → excellent, c'est leur force
⚠️ 4 applis cohabitant sur le même serveur            → contention sur les Platform Threads
                                                          (carriers) qui sont limités au
                                                          nombre de cœurs CPU disponibles
⚠️ parallelStream agressif sur une appli              → starve les autres applis qui
                                                          partagent le même commonPool
```

> 💡 Pour rappel : les Virtual Threads sont quasi-illimités (on peut en créer des millions), mais ils reposent tous sur un pool de **Platform Threads carriers** dont la taille est fixée au nombre de cœurs CPU. C'est à ce niveau, partagé entre toutes les JVM du serveur, que la contention peut apparaître.

**La règle d'or est donc :**

> 📏 **Mesurer, pas supposer.**
> Définissez des métriques claires (throughput, latence P99, consommation CPU, mémoire), mettez en place des benchmarks réalistes avec JMH, profilez avec async-profiler ou JFR, et itérez. La configuration optimale d'un pool de threads ou d'un scheduler est **spécifique à chaque application, chaque infrastructure, chaque profil de charge**.

---

### 🌍 Comparaison avec d'autres langages

Java n'est pas le seul à avoir résolu (ou tenté de résoudre) ces problèmes. Un rapide tour d'horizon pour situer les choix de Java dans un contexte plus large :

| Langage | Modèle de concurrence | Points forts | Limites |
|---|---|---|---|
| **JavaScript** | Event loop mono-thread, `async/await`, Promises | Simple à raisonner, pas de race conditions sur les données | Pas de vrai parallélisme CPU, Workers séparés et lourds |
| **Python** | GIL + `asyncio` + `multiprocessing` | Syntaxe async claire | Le GIL empêche le multi-threading CPU réel (en cours de résolution avec PEP 703) |
| **Go** | Goroutines + channels (modèle CSP) | Goroutines légères nativement, channels idiomatiques | Runtime moins configurable, pas de generics historiquement |
| **Rust** | `async/await` + runtimes (Tokio) | Zéro data race garanti à la compilation, performances maximales | Pas de runtime intégré, courbe d'apprentissage steep |
| **C++** | `std::thread`, coroutines C++20 | Contrôle total, performances | Très manuel, risques mémoire, complexité accrue |
| **Java 21** | Virtual Threads + Structured Concurrency | Rétrocompatible, lisible, scalable | JVM overhead, GC pauses, pinning à surveiller |

**Ce qui est frappant :**

- **Go** a nativement ce que Java vient d'atteindre avec les Virtual Threads — les goroutines existent depuis 2009, avec le même principe M:N (N goroutines mappées sur M threads OS). Java arrive plus tard mais avec l'avantage de la **rétrocompatibilité** : tout le code bloquant existant bénéficie des VT sans réécriture.

- **JavaScript/Node.js** a résolu le problème de l'I/O scalable d'une autre manière : en n'ayant **qu'un seul thread** et un event loop non-bloquant. Très efficace pour l'I/O, mais le CPU-bound reste un angle mort et le modèle de callbacks/Promises est souvent jugé moins lisible.

- **Rust avec Tokio** est probablement le modèle le plus performant brut — mais au prix d'une complexité et d'une rigueur que peu d'équipes peuvent se permettre durablement.

- **Python** reste historiquement limité par le GIL pour la concurrence CPU, bien que `asyncio` soit mature pour l'I/O. Le projet no-GIL (PEP 703, Python 3.13 expérimental) pourrait changer la donne.

> 🎯 **En résumé** : Java 21 avec les Virtual Threads rattrape Go sur le terrain de la concurrence légère, tout en préservant l'écosystème existant. Ce n'est pas le modèle le plus novateur, mais c'est probablement le **meilleur compromis pragmatique** pour les équipes qui ont déjà une base de code Java.

---

## Bibliographie — Pour aller plus loin

### Certifications 

- **OCP**
  https://ocpj21.javastudyguide.com/ch10.html#the-concurrency-api

### 📚 Livres

- **Java Concurrency in Practice** — Brian Goetz et al. *(la référence absolue sur la concurrence Java)*
  https://jcip.net/

- **Effective Java, 3e édition** — Joshua Bloch *(chapitres 10-11 sur la concurrence)*
  https://www.pearson.com/en-us/subject-catalog/p/effective-java/P200000000138

- **The Art of Multiprocessor Programming** — Herlihy & Shavit *(algorithmes concurrents bas niveau)*
  https://www.sciencedirect.com/book/9780123973375/the-art-of-multiprocessor-programming

---

### 📄 JEPs (Java Enhancement Proposals) — Sources primaires

- **JEP 444 — Virtual Threads (Java 21, stable)**
  https://openjdk.org/jeps/444

- **JEP 453 — Structured Concurrency (Java 21, preview)**
  https://openjdk.org/jeps/453

- **JEP 446 — Scoped Values (Java 21, preview)**
  https://openjdk.org/jeps/446

- **JEP 266 — Flow API / Reactive Streams (Java 9)**
  https://openjdk.org/jeps/266

---

### 🌐 Documentation officielle

- **Java 21 API — `java.util.concurrent` (package complet)**
  https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html

- **Java Flow API — Javadoc officiel**
  https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Flow.html

- **Project Reactor — Documentation de référence**
  https://projectreactor.io/docs/core/release/reference/

- **Reactive Streams Specification**
  https://www.reactive-streams.org/

- **Inside Java — Tag "Loom" (articles officiels Oracle sur les VT)**
  https://inside.java/tag/loom

---

### 🎥 Talks vidéo

- **"Java 21 Virtual Threads" — JDK IO 2023, José Paumard**
  https://www.youtube.com/watch?v=6dpHdo-UnCg

- **"When Loom Meets Reactive Programming" — Devoxx, Nicolai Parlog**
  https://www.youtube.com/watch?v=jRDlTl-wC6s

- **"Structured Concurrency" — Inside Java Podcast, Ron Pressler**
  https://inside.java/2021/05/10/structured-concurrency/

- **"CompletableFuture: The Promises of Java" — Devoxx**
  https://www.youtube.com/watch?v=-MBPQ7NIL_Y

---

### 🛠️ Outils

- **JMH — Java Microbenchmark Harness** *(benchmarks précis, éviter les pièges JIT)*
  https://github.com/openjdk/jmh

- **async-profiler** *(flamegraphs CPU/mémoire, incontournable pour profiler les VT)*
  https://github.com/async-profiler/async-profiler

- **Java Flight Recorder + JDK Mission Control** *(intégré au JDK, visualisation des threads)*
  https://github.com/openjdk/jmc

- **VisualVM** *(monitoring JVM temps réel)*
  https://visualvm.github.io/

- **Gatling** *(tests de charge)*
  https://gatling.io/

---

### 📰 Articles et ressources pratiques

- **Baeldung — Java Concurrency (catalogue complet de tutoriels)**
  https://www.baeldung.com/java-concurrency

- **Baeldung — Guide to Virtual Threads**
  https://www.baeldung.com/java-virtual-thread-vs-thread

- **Thorben Janssen — Virtual Threads deep dive**
  https://thorben-janssen.com/java-virtual-threads/

- **PEP 703 — Python no-GIL** *(pour la comparaison Python)*
  https://peps.python.org/pep-0703/

- **Go — Effective Concurrency (goroutines & channels)**
  https://go.dev/doc/effective_go#concurrency

---

*Fin du cours complet sur la concurrence Java.*
