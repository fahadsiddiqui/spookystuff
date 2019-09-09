#!/usr/bin/env bash

CRDIR="$(cd "`dirname "$0"`"; pwd)"
FWDIR="$(cd "`dirname "$0"`"/..; pwd)"

source "${CRDIR}/.test-common.sh"

mvn test -f "$FWDIR"/pom.xml -Pdist "$@"
