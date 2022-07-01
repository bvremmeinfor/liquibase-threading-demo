package org.example.lbthreading;

import liquibase.Scope;
import liquibase.ScopeManager;

/**
 * Thread local scope manager as suggested in:
 * https://github.com/liquibase/liquibase/issues/2248
 * <p>
 * Note: Using Broscious version (single root scope).
 */
public class ThreadLocalScopeManager extends ScopeManager {

    private final ThreadLocal<Scope> currentScope = new ThreadLocal<>();
    private final Scope rootScope;

    ThreadLocalScopeManager() {
        this.rootScope = Scope.getCurrentScope();
    }

    @Override
    public synchronized Scope getCurrentScope() {
        Scope returnedScope = currentScope.get();

        if (returnedScope == null) {
            returnedScope = rootScope;
        }

        return returnedScope;
    }

    @Override
    protected Scope init(Scope scope) throws Exception {
        return scope;
    }

    @Override
    protected synchronized void setCurrentScope(Scope scope) {
        this.currentScope.set(scope);
    }
}