package org.example.lbthreading;

import liquibase.Scope;
import liquibase.ScopeManager;

/**
 * Thread local scope manager as suggested in:
 * https://github.com/liquibase/liquibase/issues/2248
 * <p>
 * Note: Using Broscious version (single root scope).
 * Note2: Modified with SynchronizedRootScope
 *
 *
 */
public class ThreadLocalScopeManager extends ScopeManager {

    private final SynchronizedScope rootScope;
    private final ThreadLocal<Scope> threadLocalScopes = new ThreadLocal<>();

    ThreadLocalScopeManager(SynchronizedScope rootScope) {
        this.rootScope = rootScope;
    }

    @Override
    public Scope getCurrentScope() {
        Scope current = threadLocalScopes.get();

        if (current == null) {
            threadLocalScopes.set(rootScope);
            current = rootScope;
        }

        return current;
    }

    @Override
    protected void setCurrentScope(Scope scope) {
        threadLocalScopes.set(scope);
    }

    @Override
    protected Scope init(Scope scope) throws Exception {
        return rootScope;
    }

}