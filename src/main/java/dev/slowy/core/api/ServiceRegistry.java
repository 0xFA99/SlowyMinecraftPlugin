package dev.slowy.core.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight, thread-safe service registry for SlowyCore2.
 * Decouples managers and modules without tight circular dependencies.
 */
public final class ServiceRegistry {

    private static final Map<Class<?>, Object> SERVICES = new ConcurrentHashMap<>();

    private ServiceRegistry() {}

    public static <T> void register(Class<T> type, T instance) {
        SERVICES.put(type, instance);
    }

    public static <T> void unregister(Class<T> type) {
        SERVICES.remove(type);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        Object service = SERVICES.get(type);
        if (service == null) {
            throw new IllegalStateException("Service not registered: " + type.getName());
        }
        return (T) service;
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getOptional(Class<T> type) {
        return Optional.ofNullable((T) SERVICES.get(type));
    }

    public static void clear() {
        SERVICES.clear();
    }
}
