package org.example.lbthreading;

import liquibase.Scope;

public final class LiquibaseThreading {

    /* Guarded by this class */
    private static ThreadLocalScopeManager threadLocalScopeManager;


    private LiquibaseThreading() {}


    /**
     * Scope manager is static property on Scope.
     * Wrap and synchronized if *concurrent* initialization is expected (enforce single init).
     */
    public static synchronized void initialize() {

        if (threadLocalScopeManager == null) {
            final Scope rootScope = Scope.getCurrentScope();

            /*
             * Synchronize access to singletons in root scope by wrapping the scope
             */
            final SynchronizedScope synchronizedScope = new SynchronizedScope(rootScope, null);
            threadLocalScopeManager = new ThreadLocalScopeManager(synchronizedScope);

            Scope.setScopeManager(threadLocalScopeManager);
        }
    }

}
