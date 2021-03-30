#!/usr/bin/env bash

# Exit script if a statement returns a non-true return value.
set -o errexit

# Use the error status of the first failure, rather than that of the last item in a pipeline.
set -o pipefail

cd "$(dirname "$0")"

# shellcheck disable=SC1091
source ../set-build-environment.sh

MINION_TARBALL="$(find ../.. -name \*-minion.tar.gz -type f | grep opennms-assemblies | head -n 1)"
if [ -z "${MINION_TARBALL}" ]; then
  echo "unable to find minion tarball in opennms-assemblies"
  exit 1
fi
cp "${MINION_TARBALL}" tarball/minion.tar.gz

docker build -t meridian-minion \
  --build-arg BUILD_DATE="${BUILD_DATE}" \
  --build-arg VERSION="${VERSION}" \
  --build-arg SOURCE="${SOURCE}" \
  --build-arg REVISION="${REVISION}" \
  --build-arg BUILD_JOB_ID="${BUILD_JOB_ID}" \
  --build-arg BUILD_NUMBER="${BUILD_NUMBER}" \
  --build-arg BUILD_URL="${BUILD_URL}" \
  --build-arg BUILD_BRANCH="${BUILD_BRANCH}" \
  .

docker image save meridian-minion -o images/container.oci

rm -f rpms/*.repo
../stop_yum_server.sh
