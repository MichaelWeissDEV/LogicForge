package dev.logicforge.processor.mos6502;

import dev.logicforge.processor.ArrayByteBus;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optional command-line harness for a separately obtained Klaus Dormann functional-test
 * image. The GPL test binary is intentionally not bundled with this source tree.
 *
 * Usage: KlausFunctionalTestHarness image.bin [startHex] [successTrapHex] [maxInstructions]
 */
public final class KlausFunctionalTestHarness {
    private KlausFunctionalTestHarness() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 4) {
            System.err.println("usage: KlausFunctionalTestHarness image.bin [0400] [3469] [100000000]");
            System.exit(2);
        }
        int start = args.length > 1 ? Integer.parseInt(args[1], 16) : 0x0400;
        int success = args.length > 2 ? Integer.parseInt(args[2], 16) : 0x3469;
        long max = args.length > 3 ? Long.parseLong(args[3]) : 100_000_000L;

        byte[] image = Files.readAllBytes(Path.of(args[0]));
        if (image.length > 0x10000) {
            throw new IllegalArgumentException("Functional-test image exceeds 64 KiB");
        }
        ArrayByteBus bus = new ArrayByteBus();
        bus.load(0, image);
        Mos6502 cpu = new Mos6502(bus);
        cpu.setPc(start);

        int previous = -1;
        for (long count = 0; count < max; count++) {
            int before = cpu.pc();
            cpu.stepInstruction();
            int after = cpu.pc();
            if (after == before || after == previous) {
                if (after == success) {
                    System.out.printf("PASS at $%04X after %d instructions%n", after, count + 1);
                    return;
                }
                throw new AssertionError("Functional test trapped at $%04X after %d instructions"
                        .formatted(after, count + 1));
            }
            previous = before;
        }
        throw new AssertionError("Functional test exceeded " + max + " instructions at $%04X"
                .formatted(cpu.pc()));
    }
}
