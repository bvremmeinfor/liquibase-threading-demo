# Liquibase Threading Test

Demo Liquibase in a multithreaded environment, related issues:
* https://github.com/liquibase/liquibase/issues/2248 - ScopeManager heavy thread load
* https://github.com/liquibase/liquibase/issues/2018 - Create ThreadLocal ScopeManager
* https://github.com/liquibase/liquibase/pull/1768 - Root scope is initialization

Demo uses unmodified Liquibase with custom scope manager (ThreadLocalScopeManager with synchronized root scope).
Liquibase is initialized in synchronized block (scope init is unsynchronized static out-of-the-box).

This is fairly thread-safe, LiquibaseThreadTest fails 10%-20% of the time.
Run testLoop.sh script for 100 individual tests with error reporting (new JVM with fresh empty statics every time).

At this stage is 'consistently' fails in SqlGeneratorFactory (location varies) and it is always related to 
shared-static/unsynchronized Maps. A simple fix is to add synchronized on method:
```
public synchronized SortedSet<SqlGenerator> getGenerators(SqlStatement statement, Database database)
```

This makes the maps partially synchronized - not good, but good enough maybe?




