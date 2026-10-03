#!/bin/sh
#
# Gradle wrapper with auto-download support. Downloads Gradle 9.3.1 if not already cached.
# Uses gradle-wrapper.properties for version and download URL.
# Extracted from official Gradle wrapper to support CI/CD environments.

set -e

APP_HOME=$( cd -P "$(dirname "$0")" > /dev/null && pwd -P )

# Read gradle-wrapper.properties
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.properties" ]; then
    echo "ERROR: gradle/wrapper/gradle-wrapper.properties not found" >&2
    exit 1
fi

# Extract distribution URL and checksum from properties
DIST_URL=$(grep distributionUrl "$APP_HOME/gradle/wrapper/gradle-wrapper.properties" | cut -d'=' -f2)
DIST_CHECKSUM=$(grep distributionSha256Sum "$APP_HOME/gradle/wrapper/gradle-wrapper.properties" | cut -d'=' -f2)

# Unescape URL
DIST_URL=$(echo "$DIST_URL" | sed 's/\\//g')

# Gradle user home
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists"

# Extract Gradle version from URL (e.g., gradle-9.3.1-bin.zip -> 9.3.1)
GRADLE_VERSION=$(echo "$DIST_URL" | sed 's/.*gradle-\([^-]*\).*/\1/')

# Create dist directory if not exists
mkdir -p "$DIST_DIR"

# Check if Gradle is already cached
GRADLE_BIN_HOME=""
for candidate in "$DIST_DIR"/gradle-"$GRADLE_VERSION"-*/gradle-"$GRADLE_VERSION"; do
    if [ -d "$candidate" ]; then
        GRADLE_BIN_HOME="$candidate"
        break
    fi
done

# Download if not cached
if [ -z "$GRADLE_BIN_HOME" ] || [ ! -f "$GRADLE_BIN_HOME/bin/gradle" ]; then
    ZIP_FILE=$(basename "$DIST_URL")
    ZIP_PATH="$DIST_DIR/$ZIP_FILE"
    
    echo "Downloading Gradle $GRADLE_VERSION..."
    if command -v curl > /dev/null; then
        curl -f -L --output "$ZIP_PATH" "$DIST_URL" || { echo "Failed to download Gradle"; exit 1; }
    elif command -v wget > /dev/null; then
        wget -q --output-document="$ZIP_PATH" "$DIST_URL" || { echo "Failed to download Gradle"; exit 1; }
    else
        echo "ERROR: curl or wget is required to download Gradle" >&2
        exit 1
    fi
    
    # Verify checksum if available
    if [ -n "$DIST_CHECKSUM" ]; then
        echo "Verifying checksum..."
        if command -v sha256sum > /dev/null; then
            ACTUAL_CHECKSUM=$(sha256sum "$ZIP_PATH" | cut -d' ' -f1)
        elif command -v shasum > /dev/null; then
            ACTUAL_CHECKSUM=$(shasum -a 256 "$ZIP_PATH" | cut -d' ' -f1)
        else
            ACTUAL_CHECKSUM=""
        fi
        
        if [ -n "$ACTUAL_CHECKSUM" ] && [ "$ACTUAL_CHECKSUM" != "$DIST_CHECKSUM" ]; then
            echo "ERROR: Checksum mismatch for $ZIP_FILE" >&2
            rm "$ZIP_PATH"
            exit 1
        fi
    fi
    
    # Extract
    echo "Extracting Gradle..."
    UNZIP_DIR="$DIST_DIR/gradle-$GRADLE_VERSION-temp"
    rm -rf "$UNZIP_DIR"
    mkdir -p "$UNZIP_DIR"
    unzip -q "$ZIP_PATH" -d "$UNZIP_DIR"
    
    # Move to final location
    FINAL_DIR="$DIST_DIR/gradle-$GRADLE_VERSION-$(date +%s)"
    mv "$UNZIP_DIR/gradle-$GRADLE_VERSION" "$FINAL_DIR"
    rm -rf "$UNZIP_DIR"
    
    GRADLE_BIN_HOME="$FINAL_DIR"
    rm "$ZIP_PATH"
fi

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    [ -x "$JAVACMD" ] || { echo "ERROR: JAVA_HOME is invalid: $JAVA_HOME" >&2; exit 1; }
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || { echo "ERROR: JAVA_HOME is not set and no 'java' in PATH" >&2; exit 1; }
fi

# Run Gradle
exec "$GRADLE_BIN_HOME/bin/gradle" "$@"
