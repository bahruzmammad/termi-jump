
#!/bin/sh
# Build the cross-platform application JAR.

set -eu

# Change to the directory containing this script.
cd "$(dirname "$0")"

# Package the application while skipping tests; use test.sh to run them separately.
mvn -q -DskipTests package

# Print the path Maven uses for the runnable artifact.
printf 'Built target/termijump.jar\n'
