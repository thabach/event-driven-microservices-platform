#!/bin/sh

NEXUS_URL="http://${EVENTDRIVENMICROSERVICESPLATFORM_NEXUS_1_PORT_8081_TCP_ADDR}:${EVENTDRIVENMICROSERVICESPLATFORM_NEXUS_1_PORT_8081_TCP_PORT}/nexus"

POST_REPO_SERVICE="/service/local/repositories"
GET_REPO_SERVICE="/service/local/repositories"
PUT_PUBLIC_REPO_SERVICE="/service/local/repo_groups/public"

POST_REPO_URL="$NEXUS_URL$POST_REPO_SERVICE"
GET_REPO_URL="$NEXUS_URL$GET_REPO_SERVICE"
PUT_PUBLIC_REPO_URL="$NEXUS_URL$PUT_PUBLIC_REPO_SERVICE"

cat > spring-snapshots.json <<- EOM
  {
  "data":
    {
    "repoType":"proxy",
    "id":"spring-snapshots",
    "name":"spring-snapshots",
    "browseable":true,
    "indexable":true,
    "notFoundCacheTTL":1440,
    "artifactMaxAge":-1,
    "metadataMaxAge":1440,
    "itemMaxAge":1440,
    "repoPolicy":"RELEASE",
    "provider":"maven2",
    "providerRole":"org.sonatype.nexus.proxy.repository.Repository",
    "downloadRemoteIndexes":true,
    "autoBlockActive":true,
    "fileTypeValidation":true,
    "exposed":true,
    "checksumPolicy":"WARN",
    "remoteStorage":
      {
        "remoteStorageUrl":"https://repo.spring.io/snapshot/",
        "authentication":null,
        "connectionSettings":null
      }
    }
  }
EOM

cat > spring-milestones.json <<- EOM
  {
  "data":
    {
    "repoType":"proxy",
    "id":"spring-milestones",
    "name":"spring-milestones",
    "browseable":true,
    "indexable":true,
    "notFoundCacheTTL":1440,
    "artifactMaxAge":-1,
    "metadataMaxAge":1440,
    "itemMaxAge":1440,
    "repoPolicy":"RELEASE",
    "provider":"maven2",
    "providerRole":"org.sonatype.nexus.proxy.repository.Repository",
    "downloadRemoteIndexes":true,
    "autoBlockActive":true,
    "fileTypeValidation":true,
    "exposed":true,
    "checksumPolicy":"WARN",
    "remoteStorage":
      {
        "remoteStorageUrl":"https://repo.spring.io/milestone/",
        "authentication":null,
        "connectionSettings":null
      }
    }
  }
EOM

echo ""
echo "####################################################################################"
echo "# Checking if Repository [spring-snapshots] exists."
echo "####################################################################################"
echo ""

REPOSITORY_ID="spring-snapshots"
GET_SPRING_SNAPSHOT_REPO_URL="${GET_REPO_URL}/${REPOSITORY_ID}"

HTTPCODE=`curl -s -o /dev/null -w "%{http_code}" -H "Accept: application/json" -H "Content-Type: application/json" -X GET -u admin:admin123 $GET_SPRING_SNAPSHOT_REPO_URL`
echo "HTTP RESPONSE CODE $HTTPCODE"

if [ "${HTTPCODE}" = "404" ];
then
  echo ""
  echo "####################################################################################"
  echo "# Creating Repository ${REPOSITORY_ID}"
  echo "####################################################################################"
  echo ""
  curl -i -H "Accept: application/json" -H "Content-Type: application/json" -f -X POST -v -d "@spring-snapshots.json" -u admin:admin123 $POST_REPO_URL
fi

echo ""
echo "####################################################################################"
echo "# Checking if Repository [spring-snapshots] exists."
echo "####################################################################################"
echo ""

REPOSITORY_ID="spring-milestones"
GET_SPRING_SNAPSHOT_REPO_URL="${GET_REPO_URL}/${REPOSITORY_ID}"

HTTPCODE=`curl -s -o /dev/null -w "%{http_code}" -H "Accept: application/json" -H "Content-Type: application/json" -X GET -u admin:admin123 $GET_SPRING_SNAPSHOT_REPO_URL`
echo "HTTP RESPONSE CODE $HTTPCODE"

if [ "${HTTPCODE}" = "404" ];
then
  echo ""
  echo "####################################################################################"
  echo "# Creating Repository ${REPOSITORY_ID}"
  echo "####################################################################################"
  echo ""
  curl -i -H "Accept: application/json" -H "Content-Type: application/json" -f -X POST -v -d "@spring-milestones.json" -u admin:admin123 $POST_REPO_URL
fi

echo ""
echo "####################################################################################"
echo "# Add Spring Repositories to Public Nexus Repository"
echo "####################################################################################"
echo ""

cat > public-repository.json <<- EOM
{
"data":{"id":"public","name":"Public Repositories","format":"maven2","exposed":true,"provider":"maven2","repositories":[{"id":"releases"},{"id":"snapshots"},{"id":"thirdparty"},{"id":"central"},{"id":"jboss-ea-repository"},{"id":"jboss-ga-repository"},{"id":"apache-snapshots"},{"id":"codehaus-snapshots"},{"id":"spring-milestones"},{"id":"spring-snapshots"}]}}
EOM

curl -i -s -o /dev/null -H "Accept: application/json" -H "Content-Type: application/json" -f -X PUT -v -d "@public-repository.json" -u admin:admin123 $PUT_PUBLIC_REPO_URL

echo ""
echo "####################################################################################"
echo "# Done"
echo "####################################################################################"
echo ""
