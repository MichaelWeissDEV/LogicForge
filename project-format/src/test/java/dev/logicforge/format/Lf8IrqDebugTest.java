package dev.logicforge.format;

import dev.logicforge.circuit.document.CircuitProject;
import dev.logicforge.compiler.CompilationResult;
import dev.logicforge.compiler.CircuitCompiler;
import dev.logicforge.library.ComponentRegistry;
import dev.logicforge.logic.LogicState;
import dev.logicforge.processor.lf8.Lf8ComputerFactory;
import dev.logicforge.processor.lf8.Lf8Isa;
import dev.logicforge.processor.lf8.Lf8Microcode;
import dev.logicforge.simulation.Simulation;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Lf8IrqDebugTest {
    @Test
    void debug() throws Exception {
        int[] program = {
                op(Lf8Isa.EI),
                op(Lf8Isa.JMP), 0x12, 0x00,
                op(Lf8Isa.NOP), op(Lf8Isa.NOP), op(Lf8Isa.NOP), op(Lf8Isa.NOP),
                op(Lf8Isa.LDI), 1, 1,
                op(Lf8Isa.INC), 1,
                op(Lf8Isa.STORE), 1, 0x00, 0x80,
                op(Lf8Isa.IRET),
                op(Lf8Isa.LDI), 4, 0xff,
                op(Lf8Isa.LDI), 5, 0x01,
                op(Lf8Isa.ADD), 4, 5,
                op(Lf8Isa.LDI), 2, 5,
                op(Lf8Isa.DEC), 2,
                op(Lf8Isa.JNZ), 0x1e, 0x00,
                op(Lf8Isa.LDI), 3, 0x77,
                op(Lf8Isa.STORE), 3, 0x01, 0x80,
                op(Lf8Isa.HLT),
        };
        CircuitProject project = Lf8ComputerFactory.create(program);
        Path dir = Files.createTempDirectory("irqdbg");
        Path file = dir.resolve("x." + ProjectFormat.EXTENSION);
        ProjectFormat.save(project, file);
        CircuitProject loaded = ProjectFormat.load(file);
        CompilationResult compiled = new CircuitCompiler(ComponentRegistry.standard()).compile(loaded, "main");
        Simulation sim = new Simulation(compiled.circuit());

        int clkId = compiled.componentByLabel("CLK").orElseThrow();
        int resetId = compiled.componentByLabel("RESET").orElseThrow();
        int irqId = compiled.componentByLabel("IRQ").orElseThrow();
        int ieId = compiled.componentByLabel("IE_REGISTER").orElseThrow();
        int flagsId = compiled.componentByLabel("FLAGS_REGISTER").orElseThrow();
        int pcId = compiled.componentByLabel("PC").orElseThrow();
        int irId = compiled.componentByLabel("IR").orElseThrow();

        sim.setInput(resetId, LogicState.ONE);
        sim.setInput(resetId, LogicState.ZERO);
        sim.setInput(irqId, LogicState.ZERO);

        int spId = compiled.componentByLabel("SP").orElseThrow();
        int irqTakenId = compiled.componentByLabel("IRQ_TAKEN_LATCH").orElseThrow();

        int microsteps = Lf8Microcode.MICROSTEPS;
        for (int instr = 0; instr < 5; instr++) {
            for (int e = 0; e < microsteps; e++) {
                sim.setInput(clkId, LogicState.ZERO);
                sim.setInput(clkId, LogicState.ONE);
            }
            System.out.println("after instr " + instr + ": IE=" + sim.readOutput(ieId, 0)
                    + " FLAGS=" + sim.readOutput(flagsId, 0)
                    + " PC=" + sim.readOutput(pcId, 0)
                    + " IR=" + sim.readOutput(irId, 0));
        }

        sim.setInput(irqId, LogicState.ONE);
        for (int e = 0; e < microsteps; e++) {
            sim.setInput(clkId, LogicState.ZERO);
            sim.setInput(clkId, LogicState.ONE);
        }
        sim.setInput(irqId, LogicState.ZERO);
        System.out.println("after LDI R2 (irq now live): IE=" + sim.readOutput(ieId, 0)
                + " FLAGS=" + sim.readOutput(flagsId, 0)
                + " PC=" + sim.readOutput(pcId, 0)
                + " SP=" + sim.readOutput(spId, 0)
                + " IRQ_TAKEN=" + sim.readOutput(irqTakenId, 0));

        for (int e = 0; e < microsteps; e++) {
            sim.setInput(clkId, LogicState.ZERO);
            sim.setInput(clkId, LogicState.ONE);
        }
        System.out.println("after entry: IE=" + sim.readOutput(ieId, 0)
                + " FLAGS=" + sim.readOutput(flagsId, 0)
                + " PC=" + sim.readOutput(pcId, 0)
                + " SP=" + sim.readOutput(spId, 0)
                + " IRQ_TAKEN=" + sim.readOutput(irqTakenId, 0));

        for (int instr = 0; instr < 4; instr++) {
            for (int e = 0; e < microsteps; e++) {
                sim.setInput(clkId, LogicState.ZERO);
                sim.setInput(clkId, LogicState.ONE);
            }
            System.out.println("after handler instr " + instr + ": IE=" + sim.readOutput(ieId, 0)
                    + " FLAGS=" + sim.readOutput(flagsId, 0)
                    + " PC=" + sim.readOutput(pcId, 0)
                    + " SP=" + sim.readOutput(spId, 0)
                    + " IRQ_TAKEN=" + sim.readOutput(irqTakenId, 0));
        }

        System.out.println("RAM[0]=" + sim.memoryPage(compiled.componentByLabel("RAM").orElseThrow(), 0, 1)
                .orElseThrow().wordAt(0));

        for (int instr = 0; instr < 12; instr++) {
            for (int e = 0; e < microsteps; e++) {
                sim.setInput(clkId, LogicState.ZERO);
                sim.setInput(clkId, LogicState.ONE);
            }
            System.out.println("after resume instr " + instr + ": PC=" + sim.readOutput(pcId, 0)
                    + " FLAGS=" + sim.readOutput(flagsId, 0)
                    + " SP=" + sim.readOutput(spId, 0));
        }
        System.out.println("RAM[1]=" + sim.memoryPage(compiled.componentByLabel("RAM").orElseThrow(), 1, 1)
                .orElseThrow().wordAt(1));
    }

    private static int op(dev.logicforge.processor.lf8.Lf8Instruction i) {
        return i.opcode();
    }
}
