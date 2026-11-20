#!/bin/sh
set -u
root=$(cd "$(dirname "$0")" && pwd)
dist="$root/dist"
mkdir -p "$dist"
status=""

run() {
    dir=$1
    jdk=$2
    task=$3
    if [ -z "$jdk" ]; then
        status="$status
$dir SKIPPED (JDK missing)"
        return
    fi
    echo "== $dir"
    if (cd "$root/$dir" && JAVA_HOME="$jdk" sh ./gradlew "$task" --console=plain); then
        status="$status
$dir OK"
        if [ "$task" = build ]; then
            ver=$(sed -n 's/^mod_version=//p' "$root/$dir/gradle.properties" | tr -d '\r')
            for f in "$root/$dir/build/libs/player-event-log-$ver+"*.jar; do
                case "$f" in
                    *-sources.jar|*-dev.jar|*-slim.jar) ;;
                    *) [ -f "$f" ] && cp "$f" "$dist/" ;;
                esac
            done
        fi
    else
        status="$status
$dir FAILED"
    fi
}

run core "${JAVA21_HOME:-}" test
for v in 1.16.5 1.18.2 1.20.1 1.21.1 1.21.11 26.3; do
    run "fabric-$v" "${JAVA25_HOME:-}" build
done
run forge-1.20.1 "${JAVA17_HOME:-}" build
run forge-1.12.2 "${JAVA8_HOME:-}" build

echo "$status"
(cd "$dist" && sha256sum player-event-log-*.jar > SHA256SUMS.txt)
