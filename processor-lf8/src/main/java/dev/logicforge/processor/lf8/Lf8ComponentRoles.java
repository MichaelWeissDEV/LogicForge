package dev.logicforge.processor.lf8;

import java.util.Arrays;

/** Stable machine-readable identities for architectural LF-8 components. */
public final class Lf8ComponentRoles {

    public static final String CPU = "lf8.cpu";
    public static final String CPU_DATAPATH = "lf8.cpu.datapath";
    public static final String CPU_CONTROL = "lf8.cpu.control";

    public static final String DATAPATH_PC = "lf8.datapath.pc";
    public static final String DATAPATH_SP = "lf8.datapath.sp";
    public static final String DATAPATH_IR = "lf8.datapath.ir";
    public static final String DATAPATH_REGISTER_FILE = "lf8.datapath.register-file";
    public static final String DATAPATH_FLAGS = "lf8.datapath.flags";
    public static final String DATAPATH_IE = "lf8.datapath.interrupt-enable";

    public static final String CONTROL_MICROSTEP = "lf8.control.microstep";
    public static final String CONTROL_MICROCODE = "lf8.control.microcode";
    public static final String CONTROL_IRQ_INPUT = "lf8.control.irq-input";
    public static final String CONTROL_IRQ_TAKEN = "lf8.control.irq-taken";
    public static final String CONTROL_NMI_TAKEN = "lf8.control.nmi-taken";
    public static final String CONTROL_HALT = "lf8.control.halt";

    private Lf8ComponentRoles() {
    }

    /** Slash-separated role path used by headless hierarchy tools. */
    public static String path(String... roles) {
        if (roles == null || roles.length == 0
                || Arrays.stream(roles).anyMatch(role -> role == null || role.isBlank()
                || role.contains("/"))) {
            throw new IllegalArgumentException("Semantic role paths require non-blank role segments");
        }
        return String.join("/", roles);
    }
}
