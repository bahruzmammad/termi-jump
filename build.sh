
#!/bin/sh
# this script builds the cross-platform application jar file

set -eu

# change to the directory where this script is located
cd "$(dirname "$0")"

mvn -q -DskipTests package

printf 'Built target/termijump.jar\n'
