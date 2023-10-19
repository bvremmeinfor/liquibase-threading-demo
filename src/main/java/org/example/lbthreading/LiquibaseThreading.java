package org.example.lbthreading;

import liquibase.Scope;
import liquibase.ThreadLocalScopeManager;

public final class LiquibaseThreading {

    /* Guarded by this class */
    private static ThreadLocalScopeManager threadLocalScopeManager;


    private LiquibaseThreading() {
    }


    /**
     * Scope manager is static property on Scope.
     * Wrap and synchronized if *concurrent* initialization is expected (enforce single init).
     */
    public static synchronized void initialize() {

        if (threadLocalScopeManager == null) {
            threadLocalScopeManager = new ThreadLocalScopeManager();
            Scope.setScopeManager(threadLocalScopeManager);
        }
    }

}
