## Useful Docker commands

### Create Docker Network

```
docker network create --driver bridge prodnetwork
```

### Compile, Build and Run Docker Container - edmp-config-server

```
cd edmp-config-server
mvn clean install
docker build -t edmp-config-server .
docker rm $(docker ps -a -q --filter="name=edmp-config-server")
docker run --name="edmp-config-server" --net=prodnetwork -p 18888:8888 edmp-config-server
```

### Compile, Build and Run Docker Container - edmp-monitoring

```
cd edmp-monitoring
mvn clean install
docker build -t edmp-monitoring .
docker rm $(docker ps -a -q --filter="name=edmp-monitoring")
docker run --name="edmp-monitoring" --net=prodnetwork -p 10001:8080 edmp-monitoring
```

## Docker Utility Commands

### Connect to running Docker Container

```
docker exec -i -t ${CONTAINER ID} bash
```
