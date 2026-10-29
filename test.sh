
#!/bin/sh
# this script runs the termijump tests on any supported platform

set -eu

# change to the directory where this script is located
cd "$(dirname "$0")"

mvn -q test
