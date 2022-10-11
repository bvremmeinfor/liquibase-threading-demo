# Liquibase Threading Test

Demo Liquibase in a multithreaded environment, requires 4.17.0 or higher. Uses ThreadLocalScopeManager
class now shipped with Liquibase. With some considerations regarding initialization, Liquibase can be 
used in a multi-threaded environment.


Run testLoop.sh script for 100 individual tests with error reporting (new JVM with fresh empty statics every time).
Test uses memory database, no additional setup required.



Issues prior to 4.17.0:
* https://github.com/liquibase/liquibase/issues/2248 - ScopeManager heavy thread load
* https://github.com/liquibase/liquibase/issues/2018 - Create ThreadLocal ScopeManager
* https://github.com/liquibase/liquibase/pull/1768 - Root scope is initialization
* https://github.com/liquibase/liquibase/issues/2966 - Multi-threaded locking issue and scope errors

