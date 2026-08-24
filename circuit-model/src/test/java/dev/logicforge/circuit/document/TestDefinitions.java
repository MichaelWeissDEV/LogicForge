package dev.logicforge.circuit.document;

import dev.logicforge.circuit.component.ComponentCategory;
import dev.logicforge.circuit.component.ComponentDefinition;
import dev.logicforge.circuit.component.ParameterSpec;
import dev.logicforge.circuit.component.ParameterValues;
import dev.logicforge.circuit.component.PortDirection;
import dev.logicforge.circuit.component.PortSpec;
import dev.logicforge.circuit.geometry.CircuitPoint;
import dev.logicforge.circuit.geometry.CircuitSize;
import dev.logicforge.circuit.geometry.PortSide;
import java.util.List;

/** A minimal two-input gate definition, so the model can be tested without the library. */
final class TestDefinitions {

    static final ParameterSpec.IntegerParameter INPUTS =
            new ParameterSpec.IntegerParameter("inputs", "Input Count", 2, 2, 8);

    static final ComponentDefinition GATE = new ComponentDefinition() {

        @Override
        public String id() {
            return "test.gate";
        }

        @Override
        public String displayName() {
            return "Gate";
        }

        @Override
        public ComponentCategory category() {
            return ComponentCategory.LOGIC;
        }

        @Override
        public String description() {
            return "test gate";
        }

        @Override
        public List<ParameterSpec<?>> parameters() {
            return List.of(INPUTS);
        }

        @Override
        public List<PortSpec> ports(ParameterValues values) {
            int inputs = values.getInt(INPUTS);
            List<PortSpec> ports = new java.util.ArrayList<>();
            for (int i = 0; i < inputs; i++) {
                double y = (i - (inputs - 1) / 2.0) * 16;
                ports.add(PortSpec.of("IN" + i, PortDirection.INPUT,
                        new CircuitPoint(-24, y), PortSide.LEFT));
            }
            ports.add(PortSpec.of("OUT", PortDirection.OUTPUT, new CircuitPoint(24, 0), PortSide.RIGHT));
            return ports;
        }

        @Override
        public CircuitSize bodySize(ParameterValues values) {
            return new CircuitSize(48, Math.max(48, values.getInt(INPUTS) * 16 + 16));
        }
    };

    private TestDefinitions() {
    }
}
