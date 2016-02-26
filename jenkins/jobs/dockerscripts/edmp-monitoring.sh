#!/bin/bash

docker build -t edmp-monitoring .
docker rm $(docker ps -a -q --filter="name=edmp-monitoring") | true
docker run --name="edmp-monitoring" --net=prodnetwork -p 10001:8080 edmp-monitoring
