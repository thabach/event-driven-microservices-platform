
docker network create --driver bridge prodnetwork

cd edmp-monitoring
mvn clean install
docker build -t edmp-monitoring .
docker rm $(docker ps -a -q --filter="name=edmp-monitoring")
docker run --name="edmp-monitoring" --net=prodnetwork -p 10001:8080 edmp-monitoring

cd edmp-config-server
mvn clean install
docker build -t edmp-config-server .
docker rm $(docker ps -a -q --filter="name=edmp-config-server")
docker run --name="edmp-config-server" --net=prodnetwork -p 18888:8888 edmp-config-server


# Utility commands

## connect to docker container
docker exec -i -t ${CONTAINER ID} bash
