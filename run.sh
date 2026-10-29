
#!/bin/sh
# this script builds and starts the cross-platform termijump application

set -eu

# change to the directory where this script is located
cd "$(dirname "$0")"

# build the application
./build.sh

# run the application using the generated jar file
exec java -jar "target/termijump.jar"
