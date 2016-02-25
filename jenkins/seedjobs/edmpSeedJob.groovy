import groovy.json.JsonSlurper
import hudson.FilePath
import hudson.*

println "############################################################################################################"
println "Reading project configuration from json"

hudson.FilePath workspace = hudson.model.Executor.currentExecutor().getCurrentWorkspace()
File file = new File("${workspace}/jenkins/seedjobs/edmp-project-configuration.json")
def slurper = new JsonSlurper()
def jsonText = file.getText()
projects = slurper.parseText( jsonText )

println "############################################################################################################"
println "Create Default Views and Admin Jobs"
println ""

def edmpGitUrl="https://github.com/codecentric/event-driven-microservices-platform"
createDockerJob("docker-admin-version", "", "sudo /usr/bin/docker version", edmpGitUrl)
createDockerJob("docker-admin-list-running-container", "", "sudo /usr/bin/docker ps", edmpGitUrl)
createDockerJob("docker-admin-list-images", "", "sudo /usr/bin/docker images", edmpGitUrl)
createDockerJob("docker-admin-build-jenkins-container", "", "cd jenkins && sudo /usr/bin/docker build -t jenkins .", edmpGitUrl)
createDockerJob("docker-admin-start-jenkins-container", "", "sudo /usr/bin/docker run -d --name edmp_jenkins -p=28080:8080 jenkins", edmpGitUrl)
createDockerJob("docker-admin-stop-jenkins-container", "", 'sudo /usr/bin/docker stop \$(sudo /usr/bin/docker ps -a -q --filter="name=edmp_jenkins") && sudo /usr/bin/docker rm \$(sudo /usr/bin/docker ps -a -q --filter="name=edmp_jenkins")', edmpGitUrl)

createEdmpDockerJob("edmp-config-server", edmpGitUrl)
createEdmpDockerJob("edmp-monitoring", edmpGitUrl)

def conferenceAppGitUrl="https://github.com/codecentric/conference-app"
def workspaceDirectory="conference-app-app-1-ci"
createDockerJob("docker-conference-app-build-container", workspaceDirectory, "cd app && sudo /usr/bin/docker build -t conferenceapp .", conferenceAppGitUrl)
createDockerJob("docker-conference-app-start-container", workspaceDirectory, "sudo /usr/bin/docker run -d --name conferenceapp -p=48080:8080 conferenceapp", conferenceAppGitUrl)
createDockerJob("docker-conference-app-stop-container", workspaceDirectory, 'sudo /usr/bin/docker stop \$(sudo /usr/bin/docker ps -a -q --filter="name=conferenceapp") && sudo /usr/bin/docker rm \$(sudo /usr/bin/docker ps -a -q --filter="name=conferenceapp")', conferenceAppGitUrl)

createListViews("Admin", "Contains all admin jobs", "admin-.*")
createListViews("Docker Admin", "Contains all docker admin jobs", "docker-admin-.*")
createListViews("Conference App", "Contains all Conference App Docker jobs", ".*conference-app-.*")
createListViews("Seed", "Contains all seed jobs", ".*-seed-job")
createListViews("EDMP", "Contains all Event Driven Microservices Platform jobs", "edmp-.*")

println "############################################################################################################"
println "Iterating all projects"
println ""

projects.each {
  println "############################################################################################################"
  println ""
  println "Creating Jenkins Jobs for Git Project: ${it.gitProjectName}"
  println "- gitRepositoryUrl=${it.gitRepositoryUrl}"
  println "- rootWorkDirectory=${it.rootWorkDirectory}"
  println ""

  def jobNamePrefix = "${it.gitProjectName}"
  if( it.rootWorkDirectory.size() > 0 ) {
    jobNamePrefix = "${it.gitProjectName}-${it.rootWorkDirectory}"
  }

  createCIJob(jobNamePrefix, it.gitProjectName, it.gitRepositoryUrl, it.rootWorkDirectory)
  createSonarJob(jobNamePrefix, it.gitProjectName, it.gitRepositoryUrl, it.rootWorkDirectory)
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
      stringParam("REPOSITORY_URL", "http://\${EVENTDRIVENMICROSERVICESPLATFORM_NEXUS_1_PORT_8081_TCP_ADDR}:\${EVENTDRIVENMICROSERVICESPLATFORM_NEXUS_1_PORT_8081_TCP_PORT}/nexus/content/repositories/releases/", "Nexus Release Repository URL")
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
      scm('30/H * * * *')
      githubPush()
    }
    steps {
      maven {
          goals('clean versions:set -DnewVersion=\${BUILD_NUMBER} -U')
          mavenInstallation('Maven 3.3.3')
          if( "${rootWorkDirectory}".size() > 0 ) {
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
        if( "${rootWorkDirectory}".size() > 0 ) {
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
        if( "${rootWorkDirectory}".size() > 0 ) {
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
        if( "${rootWorkDirectory}".size() > 0 ) {
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
    }
  }
}

def createEdmpDockerJob(def projectName, def edmpGitUrl) {
  println "############################################################################################################"
  println "Creating Docker Job ${projectName} for edmpGitUrl=${edmpGitUrl}"
  println "############################################################################################################"

  job(jobName) {
    logRotator {
        numToKeep(10)
    }
    scm {
      git {
        remote {
          url(edmpGitUrl)
        }
        createTag(false)
        clean()
      }
    }
    steps {
      steps {
        shell("sh jenkins/jobs/dockerscripts/${projectName}.sh")
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
        git {
          remote {
            url(gitRepository)
          }
          createTag(false)
          clean()
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
