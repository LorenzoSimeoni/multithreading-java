# multithreading-java
# 🧵 Java Concurrency — From Java 1.0 to Java 25
A comprehensive course on the evolution of concurrency and asynchronous
programming in Java, with a ready-to-use presentation and runnable code examples.
## 📦 Content
- 📖 `course/` — Full course in Markdown (Java 1.0 → Java 25)
- 🎨 `slides/` — Marp presentation ready to export
- ☕ `code/` — Java project with runnable examples per chapter
## 🗂️ Chapters
| # | Topic | Key Concepts |
|---|---|---|
| 1 | Overview | Timeline, CPU-bound vs I/O-bound |
| 2 | Java 1.0 | Thread, Runnable, synchronized, race condition |
| 3 | Java 5 | ExecutorService, Future, Locks, Atomic, ConcurrentCollections |
| 4 | Java 7 | ForkJoinPool, Work-Stealing |
| 5 | Thread Sizing | How many threads? CPU vs I/O |
| 6 | Java 8 | CompletableFuture, parallelStream |
| 7 | Java 9 | Flow API, Reactive Streams, Project Reactor |
| 8 | Java 21/25 | Virtual Threads, Structured Concurrency, Scoped Values |
| + | Bonus | Actor Model, Akka |
## 🛠️ Tech Stack
- Java 25+
- Spring Boot 4
- Maven
## 🚀 Run the examples
```bash
git clone https://github.com/your-user/java-concurrency

cd java-concurrency/code

./mvnw spring-boot:run