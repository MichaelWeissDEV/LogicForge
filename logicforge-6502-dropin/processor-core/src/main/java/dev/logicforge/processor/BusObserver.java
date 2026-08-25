package dev.logicforge.processor;

/** Optional observation hook for tests, traces and debugger tooling. */
@FunctionalInterface
public interface BusObserver {
    BusObserver NONE = access -> { };
    void onAccess(BusAccess access);
}
