#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.
# ----------------------------------------------------------------------------

set -e

if [ -z "$JAVA_HOME" ] && [ -d "$HOME/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.12.1+1/Contents/Home" ]; then
  JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.12.1+1/Contents/Home"
  export JAVA_HOME
fi

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
elif [ -x "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" ]; then
  exec "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" "$@"
else
  echo "Error: Maven ('mvn') was not found on PATH." >&2
  exit 1
fi
