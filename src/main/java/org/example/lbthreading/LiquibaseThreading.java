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
         * No special initialization required
         * Note: ThreadLocaleScopeManager deprecated in 4.28.0
         */
    }

}
