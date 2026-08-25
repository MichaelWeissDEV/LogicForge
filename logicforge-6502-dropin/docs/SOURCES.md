# Source/provenance map

No third-party source code or chip data is copied into this bundle. The implementation was
written against public specifications and independently structured.

## Primary processor references

- Western Design Center documentation index:
  https://wdc65xx.com/support/documentation/
  Useful for current 65xx programming documentation and historical manuals.

- MOS/Mostek 6502 hardware manual mirrors and historical reference cards.
  Used for original NMOS pinout, vectors, addressing and instruction behavior.

## Transistor-level reference

- Visual6502 project:
  https://github.com/trebonian/visual6502
  https://visual6502.org/

Relevant external files:

- `transdefs.js`: transistor id, gate node, channel node 1, channel node 2, geometry
- `segdefs.js`: node polygons and `+`/`-` pull-up marker
- `nodenames.js`: symbolic signal name -> node id

The Visual6502 repository explicitly warns that files have varying licenses/copyright. This
bundle therefore contains only a parser and does not redistribute those data files. Check the
terms of the exact upstream files before vendoring or redistributing them.

## Independent validation suite

- Klaus Dormann 6502/65C02 functional tests:
  https://github.com/Klaus2m5/6502_65C02_functional_tests

The repository is GPL-3.0. The test ROM/source is deliberately not included here. The provided
`KlausFunctionalTestHarness` accepts a separately obtained test binary.

The functional test is valuable because it covers all valid original NMOS 6502 opcodes and
addressing modes; the repository also includes interrupt and decimal tests.
