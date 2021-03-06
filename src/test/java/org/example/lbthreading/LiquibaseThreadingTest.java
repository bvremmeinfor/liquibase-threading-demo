package org.example.lbthreading;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LiquibaseThreadingTest {

    private static final String DATABASE_NAME_PREFIX = "DB_MT_";
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, MemoryDatabase> liveConnections = new ConcurrentHashMap<>();

    @Before
    public void setup() {
       LiquibaseThreading.initialize();
    }

    @After
    public void tearDown() {
        teardownLiveConnections();
        shutdownExecutorService();
    }

    @Test
    public void itCanMaintainDatabasesInParallel() {
        /*
         * 4 threads seems to be sufficient to provoke most errors
         */
        final int threadCount = Math.min(16, Runtime.getRuntime().availableProcessors() * 2);

        System.out.println("Liquibase threading test will use " + threadCount + " threads.");

        assertMaintainDatabases(threadCount);
    }

    private void assertMaintainDatabases(final int threadCount) {
        final List<Future<?>> maintainTasks = new ArrayList<>();

        /*
         * We want to stress initialization as much as possible, so we
         * wait for all thread ready before we start.
         */
        final ThreadAligner threadAligner = new ThreadAligner(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String dbName = DATABASE_NAME_PREFIX + i;

            createMemoryDatabase(dbName);

            maintainTasks.add(executor.submit(() -> {
                threadAligner.awaitAllReady();
                maintainDatabase(dbName);
            }));
        }

        maintainTasks.forEach(f -> assertResolved(f));
    }

    private void maintainDatabase(final String dbName) {

        final MemoryDatabase db = getDatabase(dbName);

        try (Connection con = db.getConnection()) {

            final Liquibase liquibase = new Liquibase(
                    "/db_schema/changelog.xml",
                    new ClassLoaderResourceAccessor(),
                    new JdbcConnection(con));

            final List<ChangeSet> pending = liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression());
            if (pending.isEmpty()) {
                fail("Expected pending database changesets");
            }

            liquibase.update(new Contexts(), new LabelExpression());

            final List<String> tableNames = db.queryTables();

            assertTrue("Expected to find table1 in " + tableNames, tableNames.contains("table1"));
            assertTrue("Expected to find table2 in " + tableNames, tableNames.contains("table2"));

        } catch (SQLException | LiquibaseException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void createMemoryDatabase(final String dbName) {
        liveConnections.put(dbName, MemoryDatabase.create(dbName));
    }

    private MemoryDatabase getDatabase(final String dbName) {
        final MemoryDatabase db = liveConnections.get(dbName);
        assertNotNull("Memory database not created for " + dbName, db);
        return db;
    }

    private static <T> T assertResolved(Future<T> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted");
        } catch (TimeoutException | ExecutionException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void shutdownExecutorService() {
        executor.shutdownNow();

        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to terminate all threads in a timely fashion");
        }
    }

    private void teardownLiveConnections() {
        final Map<String, MemoryDatabase> all = new LinkedHashMap<>(liveConnections);
        liveConnections.clear();

        all.values().stream().forEach(MemoryDatabase::close);

        final List<String> notClosed = all.keySet().stream()
                .filter(MemoryDatabase::databaseExists)
                .collect(Collectors.toList());

        if (!notClosed.isEmpty()) {
            throw new IllegalStateException("Failed to close all databases, open: " + notClosed);
        }
    }

    private static class ThreadAligner {

        private final CyclicBarrier barrier;

        public ThreadAligner(int threads) {
            this.barrier = new CyclicBarrier(threads);
        }

        void awaitAllReady() {
            try {
                barrier.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException e) {
                throw new IllegalStateException(e);
            }
        }
    }

}
