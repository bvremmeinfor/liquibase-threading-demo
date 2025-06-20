package org.example.lbthreading;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.changelog.ChangeSet;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
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
         * We need many threads to provoke issues with Liquibase lock release
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

        maintainTasks.forEach(LiquibaseThreadingTest::assertResolved);
    }

    private void maintainDatabase(final String dbName) {

        System.out.println("-- maintaining database: " + dbName);

        final MemoryDatabase db = getDatabase(dbName);

        try (Connection con = db.getConnection()) {

            final Map<String, Object> liquibaseConfiguration = Map.of(
                    "liquibase.analytics.enabled", false // Disable analytics
            );

            Scope.child(liquibaseConfiguration, () -> {

                final Liquibase liquibase = new Liquibase("/db_schema/changelog.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(con));

                final List<ChangeSet> pending = liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression());
                if (pending.isEmpty()) {
                    fail("Expected pending database changesets");
                }

                /*
                 * First upgrade
                 */
                liquibase.update(new Contexts(), new LabelExpression("1.0"));
                System.out.println("-- database maintenance (step 1 - create) OK for: " + dbName);

                final List<String> tableNamesAfterFirstMigration = db.queryTables();

                assertTrue("Expected to find table1 in " + tableNamesAfterFirstMigration, tableNamesAfterFirstMigration.contains("table1"));
                assertTrue("Expected to find table2 in " + tableNamesAfterFirstMigration, tableNamesAfterFirstMigration.contains("table2"));
                assertFalse("Did NOT expect to find table_v2_conf in " + tableNamesAfterFirstMigration, tableNamesAfterFirstMigration.contains("table_v2_conf"));


                /*
                 * Second upgrade
                 */
                assertLiquibaseLocksReleased(dbName, db); // Dangling locks from step one will block this step!

                liquibase.update(new Contexts(), new LabelExpression("2.0"));

                final List<String> tableNamesAfterSecondMigration = db.queryTables();
                assertTrue("Expected to find table_v2_conf in " + tableNamesAfterSecondMigration, tableNamesAfterSecondMigration.contains("table_v2_conf"));

                System.out.println("-- database maintenance (step 2) OK for: " + dbName);

                assertLiquibaseLocksReleased(dbName, db); // This can still fail
            });

        } catch (Exception e) {
            System.out.println("-- database maintenance failed for: " + dbName);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static void assertLiquibaseLocksReleased(String dbName, MemoryDatabase db) {
        final List<Map<String, String>> locks = db.query("SELECT * FROM DATABASECHANGELOGLOCK");

        if (locks.size() != 1) {
            throw new AssertionError("Expected exactly one lock but got: " + locks + " in " + dbName);
        }

        final Map<String, String> lock = locks.get(0);
        if (!"FALSE".equals(lock.get("locked"))) {
            fail("Expected NO Liquibase locks after upgrade of " + dbName + ", but found: " + lock);
        }
    }

    private void createMemoryDatabase(final String dbName) {
        System.out.println("-- creating memory database: " + dbName);
        liveConnections.put(dbName, MemoryDatabase.create(dbName));
        System.out.println("-- memory database created: " + dbName);
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
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to terminate all threads in a timely fashion");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for all threads to terminate in a timely fashion");
        }
    }

    private void teardownLiveConnections() {
        final Map<String, MemoryDatabase> all = new LinkedHashMap<>(liveConnections);
        liveConnections.clear();

        all.values().forEach(MemoryDatabase::close);

        final List<String> notClosed = all.keySet().stream().filter(MemoryDatabase::databaseExists).collect(Collectors.toList());

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
