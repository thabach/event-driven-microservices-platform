#!/bin/bash

sudo /usr/bin/docker build -t edmp-config-server .
sudo /usr/bin/docker rm $(sudo /usr/bin/docker ps -a -q --filter="name=edmp-config-server")
CONTAINER_ID=`sudo /usr/bin/docker ps | grep "edmp-monitoring" | awk '{print $1 }'`
MONITORING_IP=`sudo /usr/bin/docker inspect --format '{{ .NetworkSettings.Networks.prodnetwork.IPAddress }}' ${CONTAINER_ID}`
sudo /usr/bin/docker run --name="edmp-config-server" -e "MONITORING_IP=${MONITORING_IP}" --net=prodnetwork -p 18888:8888 edmp-config-server
