# Liquibase Threading Test

Demo Liquibase in a multithreaded environment, requires 4.17.0 or higher. Uses ThreadLocalScopeManager
class now shipped with Liquibase. With some considerations regarding initialization, Liquibase can be 
used in a multi-threaded environment.


Run testLoop.sh script for 100 individual tests with error reporting (new JVM with fresh empty statics every time).
Test uses memory database, no additional setup required.

Issues in 4.19.1:
* new threading issue found related to MDC handling in Scope (+memory leak). See [Scope](https://github.com/liquibase/liquibase/pull/3574/files#diff-02cf9dc5731d4b4cab085adaefa3a0c592e2af76b14c0e0f781f4544c7153007) here.
* 1-2% chance for threading related error (usually some kind of NPE) - your mileage may vary (0.4% - 2.7% measured in extended test runs)
* MDCObjects in static Scope map are never removed, memory leak

Issues prior to 4.17.0:
* https://github.com/liquibase/liquibase/issues/2248 - ScopeManager heavy thread load
* https://github.com/liquibase/liquibase/issues/2018 - Create ThreadLocal ScopeManager
* https://github.com/liquibase/liquibase/pull/1768 - Root scope is initialization
* https://github.com/liquibase/liquibase/issues/2966 - Multi-threaded locking issue and scope errors

