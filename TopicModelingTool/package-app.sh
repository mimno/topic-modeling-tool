#!/usr/bin/env bash
# Build a native installer that bundles its own Java runtime, so users do not
# need to install Java. Run after `mvn package`, on the platform you are
# building for (jpackage cannot cross-compile).
#
#   ./package-app.sh            # .dmg on macOS, .msi on Windows, .deb on Linux
#   ./package-app.sh app-image  # just the application folder, no installer

set -euo pipefail
cd "$(dirname "$0")"

NAME="Topic Modeling Tool"
VERSION=$(sed -n 's:.*<version>\([0-9.]*\)</version>.*:\1:p' pom.xml | head -1)
MAIN_CLASS=cc.mallet.topics.gui.TopicModelingToolGUI
ICONS=src/main/deploy/package

case "$(uname -s)" in
    Darwin)               TYPE=${1:-dmg}; ICON="$ICONS/macosx/TopicModelingTool.icns" ;;
    MINGW*|MSYS*|CYGWIN*) TYPE=${1:-msi}; ICON="$ICONS/windows/TopicModelingTool.ico" ;;
    *)                    TYPE=${1:-deb}; ICON="" ;;
esac

rm -rf target/jpackage-input target/dist
mkdir -p target/jpackage-input
cp target/TopicModelingTool.jar target/jpackage-input/

ARGS=(
    --type "$TYPE"
    --name "$NAME"
    --app-version "$VERSION"
    --vendor "Topic Modeling Tool contributors"
    --description "Topic modeling for folders of text files, built on MALLET"
    --input target/jpackage-input
    --main-jar TopicModelingTool.jar
    --main-class "$MAIN_CLASS"
    --java-options "-Dfile.encoding=UTF-8 -Xmx4g"
    # From `jdeps --print-module-deps`, plus logging, extra charsets and screen-reader support.
    --add-modules java.base,java.desktop,java.prefs,java.logging,jdk.charsets,jdk.accessibility
    --dest target/dist
)
[ -n "$ICON" ] && ARGS+=(--icon "$ICON")

case "$TYPE" in
    msi|exe) ARGS+=(--win-menu --win-shortcut --win-dir-chooser) ;;
    deb|rpm) ARGS+=(--linux-shortcut) ;;
esac

jpackage "${ARGS[@]}"
ls -l target/dist
