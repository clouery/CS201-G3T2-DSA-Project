#!/usr/bin/env bash

# Always run from the project root.
cd "$(dirname "$0")" || exit 1

# Compile the project.
echo "Compiling project..."
./mvnw compile || exit 1

# Launch the application.
echo "Starting application..."
java -cp target/classes tdtsp.Main