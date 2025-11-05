package com.tbelousov.tutube.event;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * SimpleEventBus - лёгкая in-memory шина событий.
 * <ul>
 *   <li>At-least-once delivery в рамках одного процесса: если событие успешно добавлено в очередь,
 *   мы считаем его принятым и постараемся доставить его подписчикам даже если одновременно начался shutdown.</li>
 *   <li>Порядок доставки между разными воркерами не гарантируется.
 *   Внутри одного воркера события обрабатываются в порядке очереди.</li>
 *   <li>Диспетчеризация по точному классу события. Это намеренно упрощает быстрый lookup.</li>
 *   <li>Backpressure: {@code publish()} блокирует до timeout (по умолчанию {@link #PUBLISH_TIMEOUT_SEC} sec),
 *   затем бросает {@link RejectedExecutionException}.</li>
 *   <li>Безопасное завершение: при остановке блоки обработки дочитывают очередь и выключаются мягко.</li>
 * </ul>
 */
@Slf4j
public class SimpleEventBus implements AutoCloseable {
    private final static int PUBLISH_TIMEOUT_SEC = 5;

    /** Очередь событий. Ограничена по capacity, чтобы не съесть всю память при наплыве. */
    private final BlockingQueue<DomainEvent> queue;

    /**
     * Подписчики по типу события. {@link CopyOnWriteArrayList} даёт:
     * <ul>
     *   <li>O(1) "снимок" при итерации без внешней синхронизации</li>
     *   <li>безопасные добавления при редких изменениях состава подписчиков (по факту - только при старте приложения)</li>
     * </ul>
     */
    private final Map<Class<?>, CopyOnWriteArrayList<EventHandler<?>>> handlers = new ConcurrentHashMap<>();

    /** Пул воркеров, которые снимают события из очереди и вызывают подписчиков. */
    private final ExecutorService workers;

    /** Стандартный флаг "я работаю" */
    private volatile boolean running = true;

    /** Защита от повторного закрытия. {@code closed} гарантирует, что {@link #close()} выполнится один раз. */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Метрики (для красоты)
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong dequeuedCount = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /** Счётчик для человекочитаемых имён потоков. */
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    /**
     * Создаёт шину событий.
     *
     * @param queueCapacity максимальная ёмкость очереди (минимум 1)
     * @param workerThreads число воркеров (минимум 1)
     * @param threadNamePrefix префикс имени потоков (по умолчанию {@code evt-worker-})
     */
    public SimpleEventBus(int queueCapacity, int workerThreads, String threadNamePrefix) {
        final var prefix = threadNamePrefix != null ? threadNamePrefix : "evt-worker-";
        this.queue = new LinkedBlockingQueue<>(Math.max(1, queueCapacity));
        int n = Math.max(1, workerThreads);
        this.workers = Executors.newFixedThreadPool(n, r -> {
            var t = new Thread(r, prefix + threadCounter.getAndIncrement());
            t.setUncaughtExceptionHandler((th, ex) -> log.error("Uncaught exception in {}", th.getName(), ex));
            return t;
        });
        for (int i = 0; i < n; i++) {
            workers.submit(this::loop);
        }
        log.info("SimpleEventBus started with {} worker(s), capacity={}", n, queueCapacity);
    }

    /**
     * Подписывает обработчик на конкретный тип события.
     */
    public <E extends DomainEvent> void subscribe(EventHandler<E> handler) {
        handlers.compute(handler.eventType(), (key, list) -> {
            CopyOnWriteArrayList<EventHandler<?>> l =
                    list != null ? list : new CopyOnWriteArrayList<>();

            // Использую знак == (а не equals), чтобы проверить ссылки на объекты.
            // Чтобы не было двойной подписки одним и тем же инстансом
            boolean exists = false;
            for (EventHandler<?> h : l) {
                if (h == handler) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                l.add(handler);
            }
            return l;
        });
        log.debug("Subscribed handler {} to {}", handler.getClass().getSimpleName(), handler.eventType().getSimpleName());
    }

    /**
     * Публикует событие с дефолтным таймаутом ожидания места в очереди ({@link #PUBLISH_TIMEOUT_SEC} секунд).
     * Если очередь заполнена более {@link #PUBLISH_TIMEOUT_SEC} секунд - бросает {@link RejectedExecutionException}.
     *
     * @param event событие (не null)
     * @throws NullPointerException если event == null
     * @throws IllegalStateException если шина уже остановлена
     * @throws RejectedExecutionException если очередь переполнена и таймаут истёк
     */
    public void publish(DomainEvent event) {
        publishWithTimeout(event, PUBLISH_TIMEOUT_SEC);
    }

    /**
     * Публикует событие, ожидая свободное место в очереди до указанного таймаута.
     * <p>
     * Если {@code offer()} завершился успешно, событие считается принятым, даже если параллельно начался shutdown.
     * Это обеспечивает at-least-once: или вернули ошибку публикации, или обработали.
     * </p>
     * @param event событие (не null)
     * @param timeout время ожидания свободного места (SECONDS)
     * @throws NullPointerException если event == null
     * @throws IllegalStateException если шина уже остановлена до попытки публикации
     * @throws RejectedExecutionException если очередь переполнена и истёк таймаут
     */
    public void publishWithTimeout(DomainEvent event, long timeout) {
        if (event == null) {
            throw new NullPointerException("event");
        }
        if (!running) {
            // После остановки публикации не принимаются
            throw new IllegalStateException("EventBus is stopped");
        }

        try {
            boolean added = queue.offer(event, timeout, TimeUnit.SECONDS);
            if (!added) {
                rejectedCount.incrementAndGet();
                throw new RejectedExecutionException(
                        String.format("EventBus queue is full (capacity=%d, pending=%d) for event %s",
                                queue.remainingCapacity() + queue.size(),
                                queue.size(),
                                event.getClass().getSimpleName())
                );
            }
            // Не делаем remove, даже если параллельно начался shutdown
            // Событие попало в очередь и будет обработано: воркеры дочитают очередь
            publishedCount.incrementAndGet();
            log.trace("Event published: {}", event.getClass().getSimpleName());
        } catch (InterruptedException e) {
            // Прерывание трактуем как отмену публикации
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing event", e);
        }
    }

    /**
     * Главный цикл воркера: снимает события из очереди и вызывает подписчиков.
     * Используем poll с таймаутом (а не take), чтобы периодически проверять флаг {@link #running}
     * и завершаться быстро при shutdown.
     */
    private void loop() {
        log.debug("Worker thread started: {}", Thread.currentThread().getName());

        while (running) {
            try {
                var event = queue.poll(500, TimeUnit.MILLISECONDS);

                if (event != null) {
                    dequeuedCount.incrementAndGet();
                    if (dispatch(event) > 0) {
                        // processedCount считается "хотя бы одним обработчиком"
                        processedCount.incrementAndGet();
                    }
                }
                // Если event == null, просто проверяем running и продолжаем

            } catch (InterruptedException e) {
                // Прерывание - штатный сигнал к остановке лупа
                Thread.currentThread().interrupt();
                log.debug("Worker interrupted: {}", Thread.currentThread().getName());
                break;
            } catch (Throwable t) {
                // Остальные ошибки лупа фиксируем, но продолжаем работать
                errorCount.incrementAndGet();
                log.error("EventBus loop error in {}", Thread.currentThread().getName(), t);
            }
        }

        log.debug("Worker thread exiting: {}", Thread.currentThread().getName());
    }

    /**
     * Доставляет событие всем подписчикам соответствующего класса.
     *
     * @return число обработчиков, которые были вызваны (даже если кто-то кинул исключение после начала {@code handle()}).
     */
    @SuppressWarnings("unchecked")
    private int dispatch(DomainEvent event) {
        var list = handlers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            log.trace("No handlers for {}", event.getClass().getSimpleName());
            return 0;
        }
        int invoked = 0;
        for (EventHandler<?> h : list) {
            try {
                ((EventHandler<DomainEvent>) h).handle(event);
                invoked++;
            } catch (Exception e) {
                // Ошибка конкретного обработчика не роняет всю шину
                errorCount.incrementAndGet();
                log.error("Handler {} failed on event {}",
                        h.getClass().getSimpleName(),
                        event.getClass().getSimpleName(), e);
            }
        }
        return invoked;
    }

    /**
     * Корректно останавливает шину: запрещает новые публикации, даёт воркерам время
     * обработать оставшееся в очереди, затем при необходимости форсирует остановку.
     * <ul>
     *   <li>После {@code close()}: publish* бросает {@link IllegalStateException}.</li>
     *   <li>События, которые успели попасть в очередь до начала shutdown, будут обработаны
     *       (или пока не истечёт таймаут мягкой остановки).</li>
     * </ul>
     */
    @PreDestroy
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            log.warn("SimpleEventBus already closed");
            return;
        }

        log.info("Shutting down SimpleEventBus... (pending events: {})", queue.size());

        running = false; // публикации больше не принимаем
        workers.shutdown(); // мягкое завершение воркеров (они выйдут после poll timeout)

        try {
            // Даём 30 секунд на обработку оставшихся событий
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Workers didn't terminate in time, forcing shutdown. Pending events: {}", queue.size());

                var droppedTasks = workers.shutdownNow();
                log.warn("Forcefully terminated. Dropped tasks: {}", droppedTasks.size());

                // Пытаемся дождаться прерывания
                if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("Workers still running after shutdownNow!");
                }
            } else {
                log.info("All workers terminated gracefully");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted during shutdown");
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logMetrics();

        log.info("SimpleEventBus stopped. Unprocessed events in queue: {}", queue.size());
    }

    private void logMetrics() {
        log.info("EventBus metrics - Published: {}, Dequeued: {}, Processed: {}, Rejected: {}, Errors: {}",
                publishedCount.get(),
                dequeuedCount.get(),
                processedCount.get(),
                rejectedCount.get(),
                errorCount.get());
    }
}