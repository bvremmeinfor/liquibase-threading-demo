package org.example.lbthreading;

import liquibase.Scope;
import liquibase.SingletonObject;

import java.util.Map;

/**
 * Thread safe scope wrapper for root-scope (@link this#getSingleton(Class)} requires sync).
 */
@SuppressWarnings({
		"java:S1185" // Overriding methods should do more than simply call the same method in the super class -- we add synchronized keyword
})
class SynchronizedScope extends Scope {

	SynchronizedScope(Scope parent, Map<String, Object> scopeValues) {
		super(parent, scopeValues);
	}

	@Override
	public synchronized <T> T get(String key, Class<T> type) {
		return super.get(key, type);
	}

	@Override
	public synchronized <T> T get(String key, T defaultValue) {
		return super.get(key, defaultValue);
	}

	@Override
	public synchronized <T extends SingletonObject> T getSingleton(Class<T> type) {
		return super.getSingleton(type);
	}


}
