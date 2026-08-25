#!/bin/bash
awk '
/PHASE 5/ {
    print "        private void checkEndpoint(dev.logicforge.circuit.document.PortEndpoint ep, java.util.Map<PortReference, Boolean> seen) {"
    print "            Boolean asWhole = seen.get(ep.port());"
    print "            if (asWhole == null) {"
    print "                seen.put(ep.port(), ep.isWhole());"
    print "            } else if ((asWhole && ep.isBit()) || (!asWhole && ep.isWhole())) {"
    print "                issues.add(ValidationIssue.error("
    print "                    \"Port \" + ep.portName() + \" has both bus-level and bit-level connections; this is not allowed\","
    print "                    ep.componentId(), ep.portName()));"
    print "            }"
    print "        }"
    print ""
}
{print}
' /Users/michaelweiss/git/github/LogicForge/circuit-compiler/src/main/java/dev/logicforge/compiler/CircuitCompiler.java > /tmp/CircuitCompiler.java
mv /tmp/CircuitCompiler.java /Users/michaelweiss/git/github/LogicForge/circuit-compiler/src/main/java/dev/logicforge/compiler/CircuitCompiler.java
