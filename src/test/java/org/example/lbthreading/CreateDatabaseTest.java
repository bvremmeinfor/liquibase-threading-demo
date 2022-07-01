package org.example.lbthreading;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CreateDatabaseTest {

    private final MemoryDatabase memoryDatabase = MemoryDatabase.create("createTest");

    @After
    public void teardown() {
        memoryDatabase.close();
    }

    @Test
    public void itCanCreateDatabaseFromScratch() throws SQLException, LiquibaseException {
        try (Connection con = memoryDatabase.getConnection()) {

            final Liquibase liquibase = new Liquibase(
                    "/db_schema/changelog.xml",
                    new ClassLoaderResourceAccessor(),
                    new JdbcConnection(con));


            final List<ChangeSet> pending = liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression());
            if (pending.isEmpty()) {
                fail("Expected pending database changesets");
            }

            liquibase.update(new Contexts(), new LabelExpression());

            final List<String> tableNames = memoryDatabase.queryTables();

            assertTrue("Expected to find table1 in " + tableNames, tableNames.contains("table1"));
            assertTrue("Expected to find table2 in " + tableNames, tableNames.contains("table2"));
       }
    }

}
