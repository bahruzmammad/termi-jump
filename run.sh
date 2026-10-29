
#!/bin/sh
# Build and start the cross-platform Termi-Jump application.

set -eu

# Change to the directory containing this script.
cd "$(dirname "$0")"

# Build the application.
./build.sh

# Run the application from the generated JAR.
# exec replaces this script process so signals and exit status come from Java.
exec java -jar "target/termijump.jar"
