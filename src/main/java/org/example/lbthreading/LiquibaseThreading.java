package org.example.lbthreading;

import liquibase.Scope;
import liquibase.ScopeManager;
import liquibase.ThreadLocalScopeManager;

public final class LiquibaseThreading {


    private LiquibaseThreading() {
    }


    /**
     * Wrap and synchronized if *concurrent* initialization is expected (enforce single init).
     */
    public static synchronized void initialize() {

        /*
         * Release the scope attached to this thread - it can be affected by other threads.
         * From 4.31.1
         */
        System.out.println("!!! CLEARING LIQUIBASE SCOPE MANAGER ON ROOT THREAD !!!");
        Scope.setScopeManager(null);
    }

}
