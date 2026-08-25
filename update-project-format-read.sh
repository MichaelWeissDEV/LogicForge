#!/bin/bash
sed -i '' 's/PortReference from = readPort/dev.logicforge.circuit.document.PortEndpoint from = readEndpoint/g' /Users/michaelweiss/git/github/LogicForge/project-format/src/main/java/dev/logicforge/format/ProjectFormat.java
sed -i '' 's/PortReference to = readPort/dev.logicforge.circuit.document.PortEndpoint to = readEndpoint/g' /Users/michaelweiss/git/github/LogicForge/project-format/src/main/java/dev/logicforge/format/ProjectFormat.java
