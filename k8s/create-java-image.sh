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
./gradlew bootBuildImage --imageName=fnjoin.com/mysql-controller-java:1.0