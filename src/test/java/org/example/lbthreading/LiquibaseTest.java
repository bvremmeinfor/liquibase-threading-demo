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

/**
 * Simple test - create one database without spawning threads.
 * <p>
 *     This is done to provoke interference between tests spawning threads and
 *     running on the test thread. See {@link Scope}.
 * </p>
 */
public class LiquibaseTest {

    private static final String DATABASE_NAME_PREFIX = "DB_SINGLE_";
    private final Map<String, MemoryDatabase> liveConnections = new ConcurrentHashMap<>();

    @Before
    public void setup() {
        LiquibaseThreading.initialize();
    }

    @After
    public void tearDown() {
        teardownLiveConnections();
    }

    @Test
    public void itCanMaintainDatabase() {
            final String dbName = DATABASE_NAME_PREFIX + "0";

            createMemoryDatabase(dbName);
            maintainDatabase(dbName);
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

                liquibase.update(new Contexts(), new LabelExpression("1.0"));
                liquibase.update(new Contexts(), new LabelExpression("2.0"));

                final List<String> tableNames = db.queryTables();

                assertTrue("Expected to find table1 in " + tableNames, tableNames.contains("table1"));
                assertTrue("Expected to find table2 in " + tableNames, tableNames.contains("table2"));
                assertTrue("Expected to find table_v2_conf in " + tableNames, tableNames.contains("table_v2_conf"));


                assertLiquibaseLocksReleased(dbName, db);

                System.out.println("-- database maintenance OK for: " + dbName);
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


    private void teardownLiveConnections() {
        final Map<String, MemoryDatabase> all = new LinkedHashMap<>(liveConnections);
        liveConnections.clear();

        all.values().forEach(MemoryDatabase::close);

        final List<String> notClosed = all.keySet().stream().filter(MemoryDatabase::databaseExists).collect(Collectors.toList());

        if (!notClosed.isEmpty()) {
            throw new IllegalStateException("Failed to close all databases, open: " + notClosed);
        }
    }

}
