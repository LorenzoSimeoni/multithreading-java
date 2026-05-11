Tout au long de ce cours, nous distinguons deux grandes familles de workloads. Cette distinction est **fondamentale** car les bonnes solutions ne sont pas les mêmes selon le cas.

### CPU-bound

Le facteur limitant est la **puissance de calcul du processeur**. Le CPU est saturé pendant le traitement.

> **Exemples :** tri de millions d'éléments, cryptographie, traitement d'image/vidéo, machine learning, compression de données.

### I/O-bound

Le facteur limitant est l'**attente de ressources externes**. Le CPU est souvent inactif pendant ce temps.

> **Exemples :** requêtes SQL, appels API REST, lecture/écriture de fichiers, appels réseau, communication inter-services.

Globalement, c'est l'I/O bound qui est souvent limitant dans une application web classique ou même un batch. Par exemple sur RGP, on attend la réponse d'elastic, puis de google, pour de la base, mais on passe très peu de temps à calculer des choses.

Java 1.0, apparu en **1995**, était la première plateforme mainstream à inclure les threads dans le langage noyau. Créer des threads est facile ; les gérer à grande échelle est une toute autre affaire. Chaque génération suivante a répondu aux limitations de la précédente.

```text
Java 1.0  →  Thread, Runnable, synchronized         (gestion manuelle)
Java 5    →  ExecutorService, Future, Callable       (gestion de pools)
Java 7    →  ForkJoinPool, Work-Stealing             (traitement récursif)
Java 8    →  CompletableFuture, parallelStream       (asynchrone composable)
Java 9    →  Flow API, Reactive Streams              (flux de données asynchrones)
Java 21   →  Virtual Threads, Structured Concurrency, Scoped Values (Plus grand avancées depuis des décennies pour certains)
```

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

**`synchronized` résout les problèmes de race conditions** — un seul thread à la fois dans le bloc.
Malheureusement, on bloque tous les threads sans timeout et sans différenciation entre lecture et écriture. Le résultat est une baisse drastique des performances. Il faut donc utiliser Synchronized avec parcimonie.

---

## 3. Java 5 — Concurrency API

Java 5 introduit le package `java.util.concurrent`, une boîte à outils complète pour la concurrence industrielle. Nous couvrons ici ses grandes familles.

---

### 3.1 Executors

#### Le problème que ça résout

Créer un `Thread` manuellement à chaque tâche est coûteux (appel système OS). `ExecutorService` introduit le concept de **pool de threads** : un ensemble de threads réutilisables auxquels on soumet des tâches, sans se soucier de leur cycle de vie individuel.

> ⚠️ **Toujours fermer un ExecutorService !**
> Si on ne le ferme pas, le thread non-daemon qu'il gère reste actif et **l'application ne se termine jamais**.
> 💡 **Java 19+** : `ExecutorService` implémente `AutoCloseable`. On peut donc utiliser le try-with-resources — `close()` appelle `shutdown()` puis `awaitTermination()` automatiquement.

En plus de ça on a accès à l'objet Future. Avant en java 1 on avait seulement des Runnable, on pouvait difficilement accéder au résultat de la tache qu'on envoyait à notre Thread.
Maintenant Future met en place les Callable ce qui va nous permettre d'accéder à la valeur calculée de manière asynchrone par un autre thread. Notamment avec Future.get(), ou get avec timeout.

On peut créer plusieurs ExecutorService. Soit avec un Pool de thread défini, soit un pool élastique qui peut en créer à la volée et recycle les threads inactifs. Mais il faut faire attention à ce qu'il n'en créé pas des milliers.

On peut aussi créer des ScheduledExecutorService pour effectuer des tâches qui se repètent ou qu'on doit différer.

Globalement Executors est une grosse avancée, car on gère moins les threads, une fois la création du pool faites, on a juste à assigner des tâches et le pool fait le reste.

On va passer rapidement dessus, mais en plus des Executors la Concurrency API mets en places 3 choses importantes pour le multithreading :
Les Concurrent collections. On peut avoir des race conditions si plusieurs threads veulent accéder à la même liste par exemple et dans ce cas on a deux choix. Faire une liste syncrhonized, qui bloque la lecture et l'écriture comme vu plus haut. Soit utiliser les collections concurrentes qui souvent permettent les lectures concurrentes avec un lock sur les écritures. A voir au cas par cas, car pas forcément plus performantes dans certains cas.

### 3.3 Synchronizers

Des utilitaires pour **coordonner plusieurs threads** entre eux :

**`CountDownLatch`** — "Attendre que N événements se produisent"

**`CyclicBarrier`** — "Tout le monde attend tout le monde, puis on repart"

**`Semaphore`** — "Maximum N accès simultanés à une ressource"

### 3.4 Locks

Les `Lock` de Java 5 apportent ce que `synchronized` ne peut pas offrir, un try avec ou sans timeout, lecture et ecriture séparées, ou avec un objet Condition plus complet que le "wait"/"notify" de synchronized.

### 3.5 Volatile et Atomic

#### `volatile` — Garantit la visibilité

Sans `volatile`, chaque thread peut garder une copie locale d'une variable dans son cache CPU. `volatile` force les lectures et écritures à passer par la **mémoire principale**, garantissant que tous les threads voient la même valeur.

#### `Atomic` — Visibilité ET atomicité

Les classes `Atomic` utilisent des instructions CPU de type **Compare-And-Swap (CAS)**, sans lock :

> 🎯 **CPU-bound** : `Atomic` et `volatile` concernent les workloads où plusieurs threads partagent des compteurs ou des flags. Pour des sections critiques complexes, préférez `synchronized` ou `ReentrantLock`.

---

## 5. Combien de Platform Threads avoir ?

On entend souvent "2 threads par cœur", mais la réalité est plus nuancée. La bonne réponse dépend entièrement du **type de workload**.

---

### 5.1 CPU-bound : 1 thread par cœur

Pour des calculs intensifs, l'idéal est **1 thread par cœur CPU** logique. Avec davantage, le scheduler OS passe du temps à effectuer des **context switches** : sauvegarder l'état complet d'un thread, charger celui d'un autre. C'est du temps CPU dépensé à gérer les threads plutôt qu'à calculer.

> **Exemple :** avec 4 cœurs et 200 threads sur des calculs purs, on passe plus de temps à switcher de contexte qu'à calculer.

---

### 5.2 I/O-bound : bien plus de threads que de cœurs

Pour les workloads I/O-bound, les threads passent la plupart de leur temps à **attendre** (DB, HTTP, fichiers...). Un pool de 8 threads sera vite saturé si les appels prennent 2 secondes chacun — les requêtes s'accumulent en file d'attente.

---

### 5.3 La contrainte mémoire

Chaque platform thread consomme environ **2 Mo de stack** (configurable via `-Xss`). On ne peut donc pas créer arbitrairement de threads :

```text
4 Go de RAM dédiée aux threads = environ 2 000 threads maximum
Tomcat plafonne par défaut à 200 threads (Donc en gros on a 200 requetes concurrentes max de clients dans l'application)
```

### 5.4 Deux pools séparés — la bonne pratique

La meilleure approche est d'avoir **deux pools distincts** pour ne pas mélanger les deux types de workloads :

**Pourquoi les séparer ?**

Si une tâche I/O bloquante s'exécute dans le pool CPU, elle immobilise un thread carrier sans faire de calcul. Les autres tâches CPU se retrouvent en attente alors que les cœurs sont libres.

C'est ce que fait le projet Reactor qu'on verra plus tard.

---

## 4. Java 7 — ForkJoinPool et Work-Stealing

### 4.1 Le concept Fork/Join

Le **Fork/Join framework** est optimisé pour les tâches **CPU-bound récursives**. L'idée : une tâche trop large est **découpée (fork)** en sous-tâches plus petites, traitées en parallèle, puis les résultats sont **rassemblés (join)**. L'utilisation de ParallelStream sur une collection va faire appel à un splititerator qui va permettre de découper la tache.

### 4.2 Work-Stealing — Maximiser l'utilisation des cœurs

Chaque thread du `ForkJoinPool` a sa propre **file de tâches** (deque). Si un thread finit toutes ses tâches pendant qu'un autre en a encore, il **vole** des tâches dans la file de l'autre, **par la fin** (pour éviter les conflits). Cela maximise l'utilisation de tous les cœurs disponibles.

Un `ForkJoinPool` partagé est créé automatiquement au démarrage de la JVM, de taille nb de coeurs - 1 :

> ⚠️ **Attention au commonPool partagé**
> Le `ForkJoinPool.commonPool()` est utilisé par défaut par :
>
> - `parallelStream()`
> - `CompletableFuture.supplyAsync()` sans `Executor` explicite
>
> Ces deux usages se **concurrencent** pour les mêmes threads de travail. Sous charge, ils s'impactent mutuellement. Pour les workloads critiques, créez un `ForkJoinPool` dédié.

---

## 6. Java 8 — CompletableFuture et parallelStream

### 6.1 Contexte et motivations

Java 8 opère un changement de paradigme avec les **lambdas** et les APIs fonctionnelles. `CompletableFuture` répond à deux limitations majeures de `Future` :

**Limitation 1 — `Future` est bloquant**
**Limitation 2 — `Future` n'est pas composable**

`CompletableFuture` résout les deux avec une API **non-bloquante et fluent** :

Le thread courant peut continuer à s'éxecuter et le CompletableFuture va pouvoir chainer les opérations sur un ou plusieurs thread et gérer les erreurs, ou résultats, avec une grande variétée de fonction de chainage. Quant Future lui va être beaucoup plus rapidement bloquant.

On peut préciser un pool de thread à Completable future, si on ne veut pas utiliser le CommonPool comme vu précédemment.

### 6.6 Limites de CompletableFuture

`CompletableFuture` est puissant mais présente trois limitations importantes qui motivent l'introduction de la Flow API en Java 9.

**Limite 1 — Pas de lazy computation**

Le calcul démarre **immédiatement** à l'appel de `supplyAsync()`, même si personne ne consomme encore le résultat.

**Limite 2 — Un seul résultat**

`CompletableFuture<T>` ne peut émettre **qu'une seule valeur** (ou une exception). Impossible de modéliser un flux de données continu.

**Limite 3 — Pas de backpressure**

Si un producteur envoie des données plus vite que le consommateur ne peut les traiter, `CompletableFuture` n'offre aucun mécanisme pour ralentir le producteur.

### 6.5 parallelStream

`parallelStream()` est la façon la plus simple d'exploiter plusieurs cœurs sur une collection. En interne, il utilise le **`ForkJoinPool.commonPool()`** pour distribuer les opérations entre les threads disponibles.

> 🎯 **CPU-bound** : `parallelStream` brille sur des collections larges avec des opérations de **calcul pur** (map mathématique, filter, reduce). Pour de l'I/O-bound (ex: requête DB par élément), préférez un `ExecutorService` dédié — les threads du `commonPool` ne doivent pas être bloqués sur des I/O.

**Quand `parallelStream` aide vraiment :**

| Condition                                       | Aide ?                   |
| ----------------------------------------------- | ------------------------ |
| Collection large (> 10 000 éléments)            | ✅ Oui                   |
| Opérations CPU pures (calculs, transformations) | ✅ Oui                   |
| Opérations I/O (appels DB, HTTP...)             | ❌ Non                   |
| Collections petites                             | ❌ Non (overhead > gain) |
| Ordre de traitement important                   | ⚠️ Possible mais coûteux |
| État partagé mutable                            | ❌ Non (race conditions) |

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

> 🎯 **I/O-bound** : La Flow API et ses implémentations sont taillées pour les **flux de données asynchrones I/O-bound** — événements WebSocket, flux Kafka, résultats de requêtes paginées, SSE (Server-Sent Events)...

LIMITE :
Callback hell / code éclaté
La logique métier devient fragmentée en callbacks imbriqués, ce qui nuit à la lisibilité.

Adoption totale obligatoire
Toute la chaîne doit être “reactive” : un seul appel bloquant casse tout le modèle.

Debugging difficile
Les stacks sont asynchrones, les erreurs sont propagées différemment, le suivi du flot d’exécution est plus complexe.

Courbe d’apprentissage
Le modèle réactif est moins intuitif pour beaucoup de développeurs Java habitués à l’impératif.

Overhead inutile pour des cas simples

Difficile à intégrer avec du code bloquant/legacy

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

> 💡 Ce mécanisme s'appelle **park/unpark**. Il est transparent pour le développeur — on écrit du code bloquant classique, la JVM gère le reste.

> ⚠️ **Pas de pool de Virtual Threads**
> `newVirtualThreadPerTaskExecutor()` crée un nouveau VT par tâche — c'est voulu. Les VT étant quasi-gratuits à créer (~quelques Ko), les pooler n'apporte aucun bénéfice et empêche certaines optimisations JVM.

---

### 8.4 Virtual Threads vs Platform Threads — Benchmark comparatif

Simulation : 10 000 tâches, chacune attendant 1 seconde (I/O simulé)
SANS VT
Résultat typique : ~50 secondes (10000/200 vagues × 1 seconde)
Avec VT
Résultat typique : ~1 seconde (tout en parallèle)

---

### 8.5 Pinning — La mise en garde principale

Un Virtual Thread est **pinnée** (coincée sur son carrier) dans deux situations :

❌ Situation 1 : synchronized dans un bloc I/O bloquant
bloque ET pine le carrier !
Le carrier ne peut pas servir d'autres VT pendant toute la durée du call

> 💡 Java 24+ améliore ce point : `synchronized` ne pine plus dans la plupart des cas. Pour Java 21-23, surveiller avec `-Djdk.tracePinnedThreads=full`.

// ❌ Situation 2 : méthodes natives (JNI) (Par exemple chez nous traitement d'image dans sis-batch-image)
// Inévitable — à minimiser dans les chemins critiques

---

### 8.6 Ce que Virtual Threads NE remplacent pas

| Cas                              | Virtual Threads       | Recommandation                 |
| -------------------------------- | --------------------- | ------------------------------ |
| I/O-bound (DB, HTTP, fichiers)   | ✅ Excellent          | Utiliser les VT                |
| CPU-bound (calculs intensifs)    | ❌ Pas d'apport       | Rester sur `ForkJoinPool`      |
| Backpressure sur flux de données | ❌ Pas de mécanisme   | Flow API / Project Reactor     |
| Flux infinis ou continus         | ❌ Pas adapté         | Reactive Streams               |
| Composition fluent d'async       | Possible mais verbeux | `CompletableFuture` ou Reactor |

Globalement c'est une révolution pour l'I/O. Tomcat qui plafonnait à 200 requetes concurrente peut maintenant gérer des miliers ou centaines de miliers de connexions simultannés. Des benchmarks montrent une multiplication des requetes concurrente entre 10 et 100 sans modification du code applicatif (à l'inverse de la flow API).

### 8.7 Structured Concurrency (Java 21 Preview — Stable Java 25)

#### Le problème : le cycle de vie des tâches

Avec `ExecutorService`, les tâches enfants ont un cycle de vie **découplé** du code qui les crée :

**Structured Concurrency** impose une règle simple : **une tâche enfant ne peut pas survivre à sa tâche parente**. Comme les blocs `try-with-resources` garantissent la fermeture des ressources, `StructuredTaskScope` garantit l'arrêt des sous-tâches.

#### ShutdownOnFailure — Tout ou rien

#### ShutdownOnSuccess — Prendre le plus rapide

### 8.8 Scoped Values (Java 21 Preview — Stable Java 25)

#### Le problème avec ThreadLocal

`ThreadLocal` est le mécanisme historique pour partager des données dans le contexte d'un thread (utilisateur courant, locale, contexte de sécurité...). Il souffre de quatre problèmes avec les Virtual Threads :

// ❌ Problèmes de ThreadLocal
ThreadLocal<User> currentUser = new ThreadLocal<>();

// 1. Mutabilité — n'importe quel code peut écraser la valeur
currentUser.set(adminUser); // écrase la valeur précédente sans avertissement

// 2. Fuite mémoire — si remove() est oublié, la valeur reste en mémoire
// tant que le thread vit (Platform Thread réutilisé dans un pool → jamais)
currentUser.set(user);
// ... oubli de currentUser.remove() → fuite

// 3. Explosion mémoire avec Virtual Threads
// Chaque Virtual Thread possède sa propre map de ThreadLocal.
// Si tu crées des millions de VT, tu as des millions de valeurs stockées,
// ce qui peut saturer la mémoire très rapidement.

**ScopedValue vs ThreadLocal — Comparatif :**

| Critère                  | `ThreadLocal`            | `ScopedValue`              |
| ------------------------ | ------------------------ | -------------------------- |
| Mutabilité               | ✅ Mutable               | ❌ Immuable (par design)   |
| Portée                   | Durée de vie du thread   | Bloc de code délimité      |
| Héritage aux sous-tâches | Coûteux (copie complète) | ✅ Efficace et automatique |
| Risque de fuite mémoire  | ✅ Oui (remove() oublié) | ❌ Non (scope borné)       |
| Performance avec VT      | Problématique            | ✅ Optimisé                |

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
