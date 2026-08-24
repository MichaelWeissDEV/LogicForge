package dev.logicforge.circuit.document;

import java.util.UUID;

/** Identifies one port of one placed component. */
public record PortReference(UUID componentId, String portName) {

    public PortReference {
        if (componentId == null || portName == null) {
            throw new IllegalArgumentException("Port references need a component and a port name");
        }
    }

    @Override
    public String toString() {
        return componentId + "." + portName;
    }
}
