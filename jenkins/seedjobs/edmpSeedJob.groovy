import groovy.json.JsonSlurper
import hudson.FilePath
import hudson.*

println "############################################################################################################"
println "Reading project configuration from json"

hudson.FilePath workspace = hudson.model.Executor.currentExecutor().getCurrentWorkspace()
File file = new File("${workspace}/jenkins/seedjobs/edmp-project-configuration.json")
def slurper = new JsonSlurper()
URL configFileUrl = new URL("${EDMP_CONFIG_URL}")
projects = slurper.parseText(configFileUrl.text)

println "############################################################################################################"
println "Create Default Views and Admin Jobs"
println ""

def edmpGitUrl="https://github.com/codecentric/event-driven-microservices-platform"
globalProdNetwork="eventdrivenmicroservicesplatform_prodnetwork"
createDockerJob("docker-admin-version", "", "sudo /usr/bin/docker version", "")
createDockerJob("docker-admin-list-running-container", "", "sudo /usr/bin/docker ps", "")
createDockerJob("docker-admin-list-images", "", "sudo /usr/bin/docker images", "")
createDockerJob("docker-admin-list-networks", "", "sudo /usr/bin/docker network ls", "")
createDockerJob("edmp-sample-app-redis", "", "(sudo /usr/bin/docker stop edmp-sample-app-redis | true) && (sudo /usr/bin/docker rm edmp-sample-app-redis | true) && sudo /usr/bin/docker run -d --name edmp-sample-app-redis --net=${globalProdNetwork} redis", "")

createListViews("Admin", "Contains all admin jobs", ".*admin-.*")
createListViews("Docker Admin", "Contains all docker admin jobs", "docker-admin-.*")
createListViews("EDMP Jobs", "Contains all Event Driven Microservices Platform jobs", "edmp-.*")

println "############################################################################################################"
println "Iterating all projects"
println ""

projects.each {
  println "############################################################################################################"
  println ""
  println "Creating Jenkins Jobs for Git Project: ${it.gitProjectName}"
  println ""
  println "- gitRepositoryUrl  = ${it.gitRepositoryUrl}"
  println "- rootWorkDirectory = ${it.rootWorkDirectory}"
  println "- dockerPort        = ${it.dockerPort}"
  println ""

  def jobNamePrefix = "${it.gitProjectName}"
  if( it.rootWorkDirectory != null ) {
    jobNamePrefix = "${it.gitProjectName}-${it.rootWorkDirectory}"
  }

  createCIJob(jobNamePrefix, it.gitProjectName, it.gitRepositoryUrl, it.rootWorkDirectory)
  createSonarJob(jobNamePrefix, it.gitProjectName, it.gitRepositoryUrl, it.rootWorkDirectory)
  createDockerBuildJob(jobNamePrefix, it.gitProjectName, it.dockerPort)

}

def createBuildPipelineView(def viewName, def viewTitle, def startJob) {
  println "############################################################################################################"
  println "Create buildPipelineView:"
  println "- viewName   = ${viewName}"
  println "- viewTitle  = ${viewTitle}"
  println "- startJob   = ${startJob}"
  println "############################################################################################################"

  buildPipelineView(viewName) {
    filterBuildQueue()
    filterExecutors()
    title("${viewTitle}")
    displayedBuilds(5)
    selectedJob(startJob)
    alwaysAllowManualTrigger()
    showPipelineParameters()
    refreshFrequency(60)
  }
}

def createListViews(def title, def jobDescription, def reqularExpression) {

  println "############################################################################################################"
  println "Create ListView:"
  println "- title             = ${title}"
  println "- description       = ${jobDescription}"
  println "- reqularExpression = ${reqularExpression}"
  println "############################################################################################################"

  listView(title) {
      description(jobDescription)
      filterBuildQueue()
      filterExecutors()
      jobs {
          regex(reqularExpression)
      }
      columns {
          buildButton()
          weather()
          status()
          name()
          lastSuccess()
          lastFailure()
          lastDuration()
      }
  }
}

def createCIJob(def jobNamePrefix, def gitProjectName, def gitRepositoryUrl, def rootWorkDirectory) {

  println "############################################################################################################"
  println "Creating CI Job:"
  println "- jobNamePrefix      = ${jobNamePrefix}"
  println "- gitProjectName     = ${gitProjectName}"
  println "- gitRepositoryUrl   = ${gitRepositoryUrl}"
  println "- rootWorkDirectory  = ${rootWorkDirectory}"
  println "############################################################################################################"

  job("${jobNamePrefix}-1-ci") {
    logRotator {
        numToKeep(10)
    }
    parameters {
      stringParam("BRANCH", "master", "Define TAG or BRANCH to build from")
      stringParam("REPOSITORY_URL", "http://nexus:8081/nexus/content/repositories/releases/", "Nexus Release Repository URL")
    }
    scm {
      git {
        remote {
          url(gitRepositoryUrl)
        }
        createTag(false)
        clean()
      }
    }
    wrappers {
      colorizeOutput()
      preBuildCleanup()
    }
    triggers {
      scm('* * * * *')
      githubPush()
    }
    steps {
      maven {
          goals('clean versions:set -DnewVersion=\${BUILD_NUMBER} -U')
          mavenInstallation('Maven 3.3.3')
          if( "${rootWorkDirectory}" != null ) {
            rootPOM("${rootWorkDirectory}/pom.xml")
          } else {
            rootPOM("pom.xml")
          }
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings('MyGlobalSettings')
      }
      maven {
        goals('clean deploy -U -DaltDeploymentRepository=nexus-release-repository::default::$REPOSITORY_URL')
        mavenInstallation('Maven 3.3.3')
        if( "${rootWorkDirectory}" != null ) {
          rootPOM("${rootWorkDirectory}/pom.xml")
        } else {
          rootPOM("pom.xml")
        }
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('MyGlobalSettings')
      }
    }
    publishers {
      chucknorris()
      archiveXUnit {
        jUnit {
          pattern('**/surefire-reports/*.xml')
          skipNoTestFiles(true)
          stopProcessingIfError(false)
        }
      }
      publishCloneWorkspace('**', '', 'Any', 'TAR', true, null)
      downstreamParameterized {
        trigger("${jobNamePrefix}-2-sonar") {
          currentBuild()
        }
      }
    }
  }
}

def createSonarJob(def jobNamePrefix, def gitProjectName, def gitRepositoryUrl, def rootWorkDirectory) {

  println "############################################################################################################"
  println "Creating Sonar Job:"
  println "- jobNamePrefix      = ${jobNamePrefix}"
  println "- gitProjectName     = ${gitProjectName}"
  println "- gitRepositoryUrl   = ${gitRepositoryUrl}"
  println "- rootWorkDirectory  = ${rootWorkDirectory}"
  println "############################################################################################################"

  job("${jobNamePrefix}-2-sonar") {
    logRotator {
        numToKeep(10)
    }
    parameters {
      stringParam("BRANCH", "master", "Define TAG or BRANCH to build from")
    }
    scm {
      cloneWorkspace("${jobNamePrefix}-1-ci")
    }
    wrappers {
      colorizeOutput()
      preBuildCleanup()
    }
    steps {
      maven {
        goals('org.jacoco:jacoco-maven-plugin:0.7.4.201502262128:prepare-agent install -Psonar -U')
        mavenInstallation('Maven 3.3.3')
        if( "${rootWorkDirectory}" != null ) {
          rootPOM("${rootWorkDirectory}/pom.xml")
        } else {
          rootPOM("pom.xml")
        }
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('MyGlobalSettings')
      }
      maven {
        goals('sonar:sonar -Psonar -U')
        mavenInstallation('Maven 3.3.3')
        if( "${rootWorkDirectory}" != null ) {
          rootPOM("${rootWorkDirectory}/pom.xml")
        } else {
          rootPOM("pom.xml")
        }
        mavenOpts('-Xms512m -Xmx1024m')
        providedGlobalSettings('MyGlobalSettings')
      }
    }
    publishers {
      chucknorris()
      downstreamParameterized {
        trigger("${jobNamePrefix}-3-docker-build") {
          currentBuild()
        }
      }
    }
  }
}

def createDockerBuildJob(def jobNamePrefix, def gitProjectName, def dockerPort) {
  println "############################################################################################################"
  println "Creating Docker Build Job ${jobNamePrefix} for gitProjectName=${gitProjectName}"
  println "############################################################################################################"

  job("${jobNamePrefix}-3-docker-build") {
    logRotator {
        numToKeep(10)
    }
    scm {
      cloneWorkspace("${jobNamePrefix}-1-ci")
    }
    steps {
      steps {
        shell("sudo /usr/bin/docker build -t ${gitProjectName} .")
        shell("sudo /usr/bin/docker stop \$(sudo /usr/bin/docker ps -a -q --filter='name=${gitProjectName}') | true")
        shell("sudo /usr/bin/docker rm \$(sudo /usr/bin/docker ps -a -q --filter='name=${gitProjectName}') | true")
        shell("sudo /usr/bin/docker run -d --name ${gitProjectName} --net=${globalProdNetwork} -p=${dockerPort} ${gitProjectName}")
      }
    }
    publishers {
      chucknorris()
    }
  }
}

def createDockerJob(def jobName, def workspaceDir, def shellCommand, def gitRepository) {

  println "############################################################################################################"
  println "Creating Docker Job ${jobName} for gitRepository=${gitRepository} and workspaceDir=${workspaceDir}"
  println "############################################################################################################"

  job(jobName) {
    logRotator {
        numToKeep(10)
    }
    scm {
      if( "${workspaceDir}".size() > 0 ) {
        cloneWorkspace("${workspaceDir}")
      } else {
        if( "${gitRepository}".size() > 0 ) {
          git {
            remote {
              url(gitRepository)
            }
            createTag(false)
            clean()
          }
        }
      }
    }
    steps {
      steps {
        shell(shellCommand)
      }
    }
    publishers {
      chucknorris()
    }
  }
}
