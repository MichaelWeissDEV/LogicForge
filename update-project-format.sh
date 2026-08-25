#!/bin/bash
sed -i '' 's/writePort(connection.from())/writeEndpoint(connection.from())/g' /Users/michaelweiss/git/github/LogicForge/project-format/src/main/java/dev/logicforge/format/ProjectFormat.java
sed -i '' 's/writePort(connection.to())/writeEndpoint(connection.to())/g' /Users/michaelweiss/git/github/LogicForge/project-format/src/main/java/dev/logicforge/format/ProjectFormat.java
