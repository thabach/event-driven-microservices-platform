#!/bin/bash

docker build -t edmp-config-server .
docker rm $(docker ps -a -q --filter="name=edmp-config-server")
CONTAINER_ID=`docker ps | grep "edmp-monitoring" | awk '{print $1 }'``
MONITORING_IP=`docker inspect --format '{{ .NetworkSettings.Networks.prodnetwork.IPAddress }}' ${CONTAINER_ID}`
docker run --name="edmp-config-server" -e "MONITORING_IP=${MONITORING_IP}" --net=prodnetwork -p 18888:8888 edmp-config-server
