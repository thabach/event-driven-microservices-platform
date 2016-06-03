FROM java:8-jdk
MAINTAINER Tobias Flohre tobias.flohre@codecentric.de

ENV SCCS_VERSION 1.1.0.RELEASE
EXPOSE 8888
WORKDIR /opt/edmp-config-server/
COPY configserver.properties /opt/edmp-config-server/configserver.properties
RUN curl -LO http://search.maven.org/remotecontent?filepath=org/springframework/cloud/spring-cloud-config-server/${SCCS_VERSION}/spring-cloud-config-server-${SCCS_VERSION}.jar
CMD java -Djava.security.egd=file:/dev/./urandom -jar spring-cloud-config-server-${SCCS_VERSION}.jar
