# Next implementation steps

1. Copy the modules into a local LogicForge checkout and add the four `settings.gradle` entries.
2. Run existing LogicForge tests unchanged; processor modules must not break them.
3. Run `processor-core` tests and the external Klaus functional suite. Fix the core before adding features.
4. Obtain Visual6502 data locally, run `Visual6502ImportTool`, then instantiate `Mos6502TransistorChip`.
5. Differential-test the Java transistor solver against reference Visual6502 half-cycle traces.
6. Add a headless RAM/ROM + clock circuit test around `Mos6502TransistorBehavior`.
7. Only then add a component definition and renderer/palette entry.
8. Add a physical-package adapter exposing individual DIP pins; keep the silicon engine unchanged.
9. Add a faster exact-microcycle model between the functional and transistor fidelity levels if needed.
10. Add undocumented fast-model opcodes only as a separate `NMOS_SILICON` compatibility mode; never silently mix them into the documented ISA profile.
