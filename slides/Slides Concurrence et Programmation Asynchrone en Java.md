---
marp: true
theme: default
paginate: true
style: |
  section {
    font-family: 'Segoe UI', sans-serif;
    font-size: 1.1rem;
  }
  section.cover {
    background: #1a1a2e;
    color: white;
    text-align: center;
    justify-content: center;
  }
  section.cover h1 {
    font-size: 2.5rem;
    color: #e94560;
    margin-bottom: 0.5rem;
  }
  section.cover h3 {
    color: #a8b2d8;
    font-weight: normal;
  }
  h2 {
    color: #e94560;
    border-bottom: 2px solid #e94560;
    padding-bottom: 0.3rem;
  }
  h3 {
    color: #0f3460;
  }
  code {
    background: #f4f4f4;
    border-radius: 4px;
    font-size: 0.85rem;
  }
  pre {
    background: #1a1a2e;
    color: #a8b2d8;
    border-radius: 8px;
    font-size: 0.8rem;
  }
  table {
    font-size: 0.85rem;
    width: 100%;
  }
  th {
    background: #0f3460;
    color: white;
  }
  .tag-cpu {
    background: #ff6b6b22;
    border-left: 4px solid #e94560;
    padding: 0.5rem 1rem;
    border-radius: 4px;
    font-size: 0.9rem;
  }
  .tag-io {
    background: #6bcfff22;
    border-left: 4px solid #0f3460;
    padding: 0.5rem 1rem;
    border-radius: 4px;
    font-size: 0.9rem;
  }
  .warning {
    background: #fff3cd;
    border-left: 4px solid #ffc107;
    padding: 0.5rem 1rem;
    border-radius: 4px;
    font-size: 0.9rem;
  }
  .tip {
    background: #d4edda;
    border-left: 4px solid #28a745;
    padding: 0.5rem 1rem;
    border-radius: 4px;
    font-size: 0.9rem;
  }
---

<!-- _class: cover -->

# Concurrence et Programmation Asynchrone en Java
### De Java 1.0 à Java 25

---

## 🤔 Pourquoi le multithreading ?

Un programme classique s'exécute **séquentiellement** — une instruction après l'autre, sur un seul fil d'exécution.

```
Requête utilisateur
      │
      ▼
  Appel DB (500ms)        ← CPU inactif, on attend
      │
      ▼
  Appel HTTP (300ms)      ← CPU inactif, on attend
      │
      ▼
  Réponse (800ms total)
```

**Le problème :** pendant ces 800ms, le CPU ne fait rien.
Pendant ce temps, 1000 autres utilisateurs attendent leur tour.

---

## 🤔 Pourquoi le multithreading ?

Le multithreading permet de **faire avancer plusieurs tâches en parallèle** :

```
Thread 1 → Requête utilisateur A : [DB...][HTTP...][réponse]
Thread 2 → Requête utilisateur B :    [DB...][HTTP...][réponse]
Thread 3 → Requête utilisateur C :       [DB...][HTTP...][réponse]
```

**Concrètement pour nous :**
- Servir **plus d'utilisateurs simultanément** sans augmenter le hardware
- **Accélérer les traitements** en parallélisant les calculs
- **Ne pas bloquer** l'application pendant une opération longue

> ⚠️ Mais faire cohabiter plusieurs fils d'exécution sur les mêmes données, c'est là que ça se complique.

---

## ⚙️ CPU-bound vs I/O-bound

Deux familles de workloads — **la distinction la plus importante du cours**.

| | CPU-bound | I/O-bound |
|---|---|---|
| **Facteur limitant** | Puissance de calcul | Attente de ressources externes |
| **CPU pendant la tâche** | Saturé | Souvent inactif |
| **Exemples** | Tri, cryptographie, ML, compression | Requêtes SQL, appels HTTP, fichiers |
| **Stratégie threads** | 1 thread par cœur | Beaucoup plus de threads que de cœurs |

<br>

<div class="tip">
💡 La plupart des applications backend sont majoritairement <strong>I/O-bound</strong> — on attend plus qu'on ne calcule.
</div>

---

## 📅 L'évolution en un coup d'œil

```
Java 1.0  →  Thread, Runnable, synchronized      (gestion manuelle)
Java 5    →  ExecutorService, Future, Callable    (gestion de pools)
Java 7    →  ForkJoinPool, Work-Stealing          (parallélisme récursif)
Java 8    →  CompletableFuture, parallelStream    (async composable)
Java 9    →  Flow API, Reactive Streams           (flux + backpressure)
Java 21   →  Virtual Threads                      (I/O scalable, code simple)
Java 21   →  Structured Concurrency               (cycle de vie garanti)
Java 21   →  Scoped Values                        (contexte immutable)
```

**Le fil conducteur :** chaque génération répond aux **limitations de la précédente**.

---

## 📅 L'évolution en un coup d'œil

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

## ☕ Java 1.0 — Thread & Runnable

En Java 1, on crée des threads **manuellement** :

```java
ThreadFactory factory = Thread.ofPlatform()
        .name("Thread-", 0)
        .daemon(false)
        .priority(Thread.NORM_PRIORITY)
        .factory();

for (int i = 1; i <= 10; i++) {
    factory.newThread(() ->
        System.out.println("Hello from " + Thread.currentThread().getName())
    ).start();
}
```

**Chaque thread OS consomme ≈ 2 Mo de stack.**

<div class="warning">
⚠️ Si on passe de 10 à 100 000 itérations → 100 000 threads OS.
Lent, coûteux, voire impossible selon la limite de l'OS.
</div>

---

## ☕ Java 1.0 — La Race Condition

Quand deux threads modifient la même donnée simultanément :

```
counter++ se décompose en 3 opérations :
  1. READ  → lire counter (valeur = 5)
  2. ADD   → ajouter 1   (valeur = 6)
  3. WRITE → écrire 6

Thread A lit 5 → Thread B lit 5 → A écrit 6 → B écrit 6
Résultat : on attendait 7, on obtient 6. Un incrément est perdu.
```

**`synchronized` résout ça** — un seul thread à la fois dans le bloc :

```java
private final Object lock = new Object();

public void increment() {
    synchronized (lock) {
        counter++; // un seul thread ici à la fois
    }
}
```

<div class="warning">
⚠️ Limites : pas de timeout, pas de tryLock, bloque lecture ET écriture.
</div>

---

## ☕ Java 5 — ExecutorService

**Le problème :** créer un thread par tâche est coûteux.
**La solution :** un pool de threads réutilisables.

```java
// Avant Java 5 — un thread par tâche ❌
new Thread(() -> processRequest()).start();

// Java 5 — pool de threads réutilisables ✅
try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
    pool.submit(() -> processRequest()); // soumis au pool
    pool.submit(() -> processRequest()); // réutilise un thread libre
}
```

| Pool | Usage |
|---|---|
| `newFixedThreadPool(n)` | Workload stable, taille connue |
| `newSingleThreadExecutor()` | Exécution séquentielle garantie |
| `newCachedThreadPool()` | Pics de charge ⚠️ pas de limite haute |
| `newScheduledThreadPool(n)` | Tâches différées ou répétitives |

---

## ☕ Java 5 — Future\<T\>

`submit()` retourne un `Future<T>` pour récupérer le résultat :

```java
Future<String> future = pool.submit(() -> {
    Thread.sleep(1000);
    return "résultat";
});

// ... on peut faire autre chose ici ...

String result = future.get(2, TimeUnit.SECONDS); // bloque jusqu'au résultat
```

<div class="warning">
⚠️ <strong>Problème fondamental de Future :</strong><br>
<code>get()</code> est <strong>bloquant</strong> — le thread appelant est immobilisé.<br>
Impossible de chaîner des opérations sans cascade de blocages.<br>
→ Java 8 résoudra ça avec <code>CompletableFuture</code>.
</div>

---

## ☕ Java 5 — Survol des autres outils

Java 5 introduit aussi une boîte à outils complète — à connaître, sans s'y attarder :

| Famille | Outils | En une phrase |
|---|---|---|
| **Locks** | `ReentrantLock`, `ReadWriteLock` | `synchronized` avec timeout et tryLock |
| **Atomic** | `AtomicInteger`, `AtomicReference` | Compteurs thread-safe sans lock (CAS) |
| **Collections** | `ConcurrentHashMap`, `BlockingQueue` | Collections conçues pour la concurrence |
| **Synchronizers** | `CountDownLatch`, `Semaphore`, `CyclicBarrier` | Coordination entre threads |

<div class="tip">
💡 <strong>Règle pratique :</strong> préférer les <code>Atomic</code> pour les compteurs simples,
les <code>Lock</code> quand on a besoin de timeout ou de séparer lecture/écriture.
</div>

---

## 🧵 Combien de threads avoir ?

La réponse dépend entièrement du **type de workload** :

```
CPU-bound → 1 thread par cœur
            Au-delà : context switches inutiles, le CPU gère les threads
            plutôt que de calculer

I/O-bound → beaucoup plus que de cœurs
            Les threads passent leur temps à attendre
            Règle empirique : nbCœurs / (1 - blockingFactor)
            blockingFactor ≈ 0.9 pour I/O → ~10× nbCœurs
```

**La contrainte mémoire :**
```
1 Platform Thread ≈ 2 Mo de stack
4 Go RAM → ~2 000 threads max
Tomcat par défaut → 200 threads
```

---

## 🧵 Combien de threads avoir ?

**La bonne pratique : deux pools séparés**

```
❌ Pool unique saturé par des I/O :
Thread-0 : [attend DB 2s...]   → cœur libre mais thread bloqué
Thread-1 : [attend HTTP 1s...] → cœur libre mais thread bloqué
Thread-2 : [calcul CPU]        → seul thread utile

✅ Deux pools distincts :
cpuPool → threads = nbCœurs     : calculs purs
ioPool  → threads = nbCœurs×10  : attentes I/O
```

<div class="tip">
💡 C'est exactement ce que <strong>Project Reactor</strong> propose avec
<code>Schedulers.parallel()</code> (CPU) et <code>Schedulers.boundedElastic()</code> (I/O).<br>
Les <strong>Virtual Threads</strong> (Java 21) résolvent ce problème autrement.
</div>

---

## ☕ Java 7 — ForkJoinPool & Work-Stealing

Conçu pour les tâches **CPU-bound récursives** : diviser pour mieux régner.

```
              SommeRecursive(1..1M)
              /                   \
   (1..500K)                    (500K..1M)
   /        \                   /        \
(1..250K) (250K..500K)   (500K..750K) (750K..1M)
  [calcul]   [calcul]      [calcul]    [calcul]
      \          /               \         /
      500K                      500K
              \                /
                  1 000 000  ✅
```

**Work-Stealing :** si un thread finit sa file, il **vole** des tâches aux autres.
→ Tous les cœurs restent occupés.

<div class="tag-cpu">
🎯 CPU-bound uniquement — ne pas utiliser pour de l'I/O bloquant.
</div>

---

## ☕ Java 8 — CompletableFuture

**Le problème de `Future` :** bloquant et non composable.

```java
// ❌ Avant — cascade de blocages
User user     = userFuture.get();    // bloque
Orders orders = orderFuture.get();   // bloque encore

// ✅ CompletableFuture — chaînage non-bloquant
CompletableFuture
    .supplyAsync(() -> fetchUser(userId))          // démarre en async
    .thenApply(user -> enrich(user))               // transformation
    .thenCompose(user -> fetchOrders(user.getId())) // chaînage async
    .handle((res, ex) -> ex != null ? "fallback" : res);
```

Le thread appelant **n'est jamais bloqué** — chaque étape s'exécute dès que la précédente est terminée.

---

## ☕ Java 8 — CompletableFuture

**Combiner plusieurs tâches en parallèle :**

```java
// Deux appels en parallèle, on combine quand les deux sont prêts
CompletableFuture<String> userFuture   = supplyAsync(() -> fetchUser());
CompletableFuture<Integer> creditFuture = supplyAsync(() -> fetchCredit());

userFuture.thenCombine(creditFuture,
    (user, credit) -> user + " — crédit : " + credit + "€"
);

// Attendre toutes les tâches
CompletableFuture.allOf(userFuture, creditFuture, inventoryFuture)
    .thenRun(() -> buildDashboard());

// Prendre le plus rapide
serviceA.applyToEither(serviceB, response -> response.toUpperCase());
```

<div class="tip">
💡 <strong>parallelStream()</strong> suit la même logique pour les collections CPU-bound —
distribue les opérations sur le <code>ForkJoinPool.commonPool()</code>.
</div>

---

## ☕ Java 8 — Limites de CompletableFuture

Trois limitations qui motivent la Flow API :

| Limite | Problème concret |
|---|---|
| **Eager** — pas de lazy | Le calcul démarre immédiatement, même si personne ne consomme le résultat |
| **Un seul résultat** | Impossible de modéliser un flux continu d'événements |
| **Pas de backpressure** | Le producteur ne peut pas être ralenti si le consommateur déborde |

```
Producteur → 1 000 000 événements/s
Consommateur → 100 événements/s

Résultat : saturation mémoire, OOMError
CompletableFuture ne peut pas dire "attends, je suis débordé"
```

→ **Java 9 introduit la Flow API** pour répondre à ces trois limites.

---

## ☕ Java 9 — Flow API & Reactive Streams

**L'idée centrale :** le consommateur contrôle le débit — c'est la **backpressure**.

```
Publisher                              Subscriber
    │                                       │
    │ ←──────── subscribe() ───────────────│
    │ ──── onSubscribe(subscription) ──────→│
    │                                       │
    │ ←──── subscription.request(3) ───────│  "donne-moi 3 éléments"
    │ ──── onNext(item1) ──────────────────→│
    │ ──── onNext(item2) ──────────────────→│
    │ ──── onNext(item3) ──────────────────→│
    │                                       │
    │ ←──── subscription.request(2) ───────│  "j'en veux 2 de plus"
    │ ──── onComplete() ───────────────────→│
```

La Flow API est une **spécification** — les implémentations sont dans **Project Reactor** (Spring WebFlux) ou **RxJava**.

---

## ☕ Java 9 — Flow API & Reactive Streams

**En pratique avec Project Reactor :**

```java
Flux.range(1, 1_000_000)
    .subscribeOn(Schedulers.boundedElastic())  // pool I/O
    .flatMap(id -> fetchFromDB(id))
    .publishOn(Schedulers.parallel())          // pool CPU
    .map(data -> heavyTransformation(data))
    .subscribe(
        result -> logger.info("Traité : {}", result),
        error  -> logger.error("Erreur", error),
        ()     -> logger.info("Terminé !")
    );
```

<div class="tip">
💡 Reactor gère nativement deux pools séparés :<br>
<code>boundedElastic()</code> → I/O &nbsp;&nbsp; <code>parallel()</code> → CPU
</div>

<div class="warning">
⚠️ Adoption totale obligatoire — un seul appel bloquant dans la chaîne annule tous les bénéfices.
</div>

---

## ☕ Java 21 — Virtual Threads

**Le problème :** 1 Platform Thread ≈ 1 Thread OS ≈ 2 Mo. Tomcat → 200 threads max.

```
JVM
┌─────────────────────────────────────────────────────┐
│  VT 1  ──mounted──► Carrier Thread 1 ──► CPU Core 1 │
│  VT 2  ──mounted──► Carrier Thread 2 ──► CPU Core 2 │
│  VT 3  ──waiting I/O──► unmounted  (carrier libéré)  │
│  VT 4  ──waiting I/O──► unmounted  (carrier libéré)  │
│  VT 5..1 000 000  (en attente, aucun carrier consommé)│
└─────────────────────────────────────────────────────┘
```

Quand un VT fait une opération bloquante → **il se démonte du carrier**.
Le carrier est immédiatement libre pour un autre VT.
Quand l'I/O répond → le VT se remonte sur un carrier disponible.

**Code inchangé — la JVM gère tout.**

---

## ☕ Java 21 — Virtual Threads

**Utilisation :**

```java
// Une ligne dans Spring Boot 3.2+
spring.threads.virtual.enabled=true

// Ou directement
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 100_000; i++) {
        executor.submit(() -> processRequest()); // 100 000 VT, pas de problème
    }
}
```

**⚠️ Le piège principal — Thread Pinning :**

```java
// ❌ synchronized + I/O = VT coincé sur son carrier (pinning)
public synchronized String fetchData() {
    return httpClient.send(request).body(); // bloque le carrier !
}

// ✅ ReentrantLock — le VT se démonte proprement
lock.lock();
try { return httpClient.send(request).body(); }
finally { lock.unlock(); }
```

---

## ☕ Java 21 — Structured Concurrency

**Le problème :** avec `ExecutorService`, si une tâche échoue, les autres continuent de tourner.

```java
// ✅ ShutdownOnFailure — tout ou rien
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var userTask  = scope.fork(() -> fetchUser(id));
    var orderTask = scope.fork(() -> fetchOrders(id));
    var stockTask = scope.fork(() -> fetchStock(id));

    scope.join().throwIfFailed();
    // Si UNE tâche échoue → les deux autres sont annulées automatiquement
    // Si tout réussit → on récupère les 3 résultats
    return new Dashboard(userTask.get(), orderTask.get(), stockTask.get());
}

// ✅ ShutdownOnSuccess — prendre le plus rapide
try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
    scope.fork(() -> fetchFromSupplierA(id)); // 300ms
    scope.fork(() -> fetchFromSupplierB(id)); // 150ms ← gagne
    scope.fork(() -> fetchFromSupplierC(id)); // 500ms
    return scope.join().result();
}
```

---

## ☕ Java 21 — Scoped Values

**Le problème de `ThreadLocal` à l'ère des Virtual Threads :**

| Critère | `ThreadLocal` | `ScopedValue` |
|---|---|---|
| Mutabilité | Mutable (n'importe qui peut écraser) | Immuable par design |
| Portée | Durée de vie du thread | Bloc de code délimité |
| Héritage sous-tâches | Copie coûteuse | Référence partagée, gratuite |
| Fuite mémoire | ⚠️ Si `remove()` oublié | ✅ Nettoyage automatique |

```java
static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();

ScopedValue.runWhere(CURRENT_USER, authenticatedUser, () -> {
    processRequest();       // CURRENT_USER disponible ici
    processPayment();       // et dans tout l'arbre d'appels
    // hors du bloc → automatiquement effacé
});
```

---

## 🎭 Bonus — Le modèle Actor

Toutes les approches vues jusqu'ici **gèrent** le partage d'état.
Le modèle Actor l'**élimine par design** — Carl Hewitt, MIT, 1973.

**Un Actor, c'est :**
- Un **état strictement privé** — jamais accessible de l'extérieur
- Une **mailbox** — file de messages entrants
- Un **comportement** — traite les messages **un par un**

```
Actor A ──message──► Mailbox B ──► Actor B traite seul son état
                                   (pas de lock, pas de race condition)
```

**Akka** / **Apache Pekko** (fork open-source depuis 2022) sont les implémentations de référence sur la JVM.

---

## 🎭 Bonus — Le modèle Actor

**Quand c'est pertinent :**

```
✅ Entités métier nombreuses avec état isolé
   → Comme des serveurs en restaurant : chaque serveur gère ses propres
     tables sans se coordonner avec les autres. Pas de lock, pas de
     goulot d'étranglement. Un serveur inactif ne consomme rien.

✅ Systèmes distribués multi-nœuds (Akka Cluster)
✅ Tolérance aux pannes via supervision hiérarchique
   → Parent décide : redémarrer l'enfant ? l'arrêter ? escalader ?
✅ Workflows longs avec états multiples
```

**⚠️ Quand ça devient contre-productif :**
```
→ Messages avec objets mutables : deux actors sur la même référence
  = race condition classique, toutes les garanties s'effondrent
→ CRUD simple : overhead injustifié
→ Besoin de résultats synchrones : le modèle est async-first
→ CPU-bound pur : ForkJoinPool est plus adapté
```

---

## 🗺️ Récap — Quand utiliser quoi ?

| Besoin | Solution |
|---|---|
| Tâche I/O-bound simple | Virtual Thread |
| 10 000 requêtes HTTP/DB concurrentes | `newVirtualThreadPerTaskExecutor()` |
| Calcul CPU intensif récursif | `ForkJoinPool` |
| Parallélisme simple sur collection | `parallelStream()` |
| Orchestration async avec dépendances | `CompletableFuture` |
| Flux continu + backpressure | Flow API / Project Reactor |
| Plusieurs sous-tâches liées (tout/rien) | `StructuredTaskScope.ShutdownOnFailure` |
| Prendre le résultat le plus rapide | `StructuredTaskScope.ShutdownOnSuccess` |
| Contexte de requête partagé | `ScopedValue` |
| Entités métier isolées, distribué | Modèle Actor (Akka/Pekko) |

---

## ⚠️ La nuance essentielle

**Tout ça, c'est de la théorie.** En production, les ressources sont partagées :

```
Serveur physique
├── Application A  (CPU-bound)
├── Application B  (I/O-bound)       → commonPool partagé entre A et B
├── Application C  (Kafka)           → carriers partagés entre toutes
└── Agents APM, sidecars...          → cœurs CPU pas ceux annoncés
```

Les hypothèses théoriques tombent souvent :
```
Théorie : nbThreads = nbCœurs / (1 - blockingFactor)
Réalité : blockingFactor varie selon la charge, la latence réseau,
          l'état du pool DB, les GC pauses, les pics de trafic...
```

<div class="warning">
📏 <strong>Mesurer, pas supposer.</strong> JMH pour les benchmarks,
async-profiler pour les flamegraphs, JFR pour le monitoring JVM.
La configuration optimale est spécifique à chaque infra.
</div>

---

## 🌍 Java dans le paysage des langages

| Langage | Modèle | Force | Limite |
|---|---|---|---|
| **JavaScript** | Event loop mono-thread | Simple, pas de race conditions | Pas de vrai parallélisme CPU |
| **Python** | GIL + asyncio | Syntaxe claire | GIL bloque le CPU multi-thread |
| **Go** | Goroutines + channels | Léger nativement depuis 2009 | Runtime moins configurable |
| **Rust** | async/await + Tokio | Zéro data race à la compilation | Courbe d'apprentissage très steep |
| **Java 21** | Virtual Threads + SC | Rétrocompatible, lisible, scalable | JVM overhead, GC pauses |

**Go** a eu les goroutines depuis 2009 — Java arrive plus tard mais avec un avantage clé : **tout le code bloquant existant bénéficie des VT sans réécriture**.

---

<!-- _class: cover -->

# Merci 🙏

### Ressources pour aller plus loin

**JEPs officielles**
openjdk.org/jeps/444 — Virtual Threads
openjdk.org/jeps/453 — Structured Concurrency

**Talks**
"Java 21 Virtual Threads" — José Paumard, JDK IO 2023
"When Loom Meets Reactive" — Nicolai Parlog, Devoxx

**Lecture**
Java Concurrency in Practice — Brian Goetz
jcip.net

**Outils**
JMH · async-profiler · Java Flight Recorder
```