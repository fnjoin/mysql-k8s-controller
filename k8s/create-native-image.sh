#!/bin/bash

# Point to minikube' docker daemon
eval $(minikube docker-env)

# Make sure docker daemon is present
docker ps > /dev/null 2> /dev/null
if [ $? -ne 0 ]; then
  echo 'ERROR: Docker daemon is not available'
  exit 1
fi

# create the image
export NATIVE_IMAGE=true
if [ "$(uname -m)" == "arm64" ]; then
  ./gradlew bootBuildImage \
    --imageName=fnjoin.com/mysql-controller-native:1.0 \
    --builder=maliksalman/paketo-buildpacks-builder-arm64:focal
else
  ./gradlew bootBuildImage \
    --imageName=fnjoin.com/mysql-controller-native:1.0
fi