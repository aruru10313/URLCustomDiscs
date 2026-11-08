#!/usr/bin/env bash
# Builds target/URLCustomDiscs-oraxen.jar without Maven: compiles against the Paper 26.3 API and
# shades the runtime libraries (org.json, mp3agic, xz) the same way the pom's assembly does.
# Dependency jars go in libs/ (see pom.xml for coordinates; downloaded from Maven Central).
set -e
cd "$(dirname "$0")"
JDK="/c/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin"
PAPER="../JjimongReboot/deps/paper-api-26.3.build.46-alpha.jar"
LIB="/c/Users/hayul/OneDrive/Desktop/Turret-Conquest-Server/libraries"
CP="$PAPER"
for j in $(find "$LIB/net/kyori" "$LIB/org/jspecify" "$LIB/org/jetbrains" "$LIB/net/md-5" "$LIB/org/joml" "$LIB/com/google/guava/guava" -name "*.jar" 2>/dev/null | grep -vE "/4\.[0-9]+\.[0-9]+/"); do CP="$CP;$(cygpath -w "$j")"; done
for j in libs/*.jar; do CP="$CP;$j"; done

rm -rf target/classes && mkdir -p target/classes
"$JDK/javac" --release 21 -encoding UTF-8 -nowarn -cp "$CP" -d target/classes $(find src/main/java -name "*.java")
cp src/main/resources/* target/classes/
# shade runtime libraries
for j in libs/json-*.jar libs/mp3agic-*.jar libs/xz-*.jar; do
  (cd target/classes && "$JDK/jar" xf "../../$j")
done
rm -rf target/classes/META-INF/*.SF target/classes/META-INF/*.RSA target/classes/META-INF/*.DSA target/classes/META-INF/MANIFEST.MF target/classes/META-INF/versions
"$JDK/jar" --create --file target/URLCustomDiscs-oraxen.jar -C target/classes .
echo "built target/URLCustomDiscs-oraxen.jar"
