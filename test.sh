
#!/bin/sh
# Run the Termi-Jump tests on a supported platform.

set -eu

# Change to the directory containing this script.
cd "$(dirname "$0")"

# Maven compiles production and test code, then runs the JUnit test suite.
mvn -q test
