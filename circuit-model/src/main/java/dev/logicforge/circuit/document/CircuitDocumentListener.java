package dev.logicforge.circuit.document;

/**
 * Notified after a {@link CircuitDocument} changed. Deliberately a document-scoped
 * listener rather than an application-wide event bus: the set of things that can happen
 * to a document is small and explicit.
 */
@FunctionalInterface
public interface CircuitDocumentListener {

    void onCircuitChanged(CircuitDocument document, CircuitChange change);
}
