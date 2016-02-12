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

createAdminDockerJob()
createAdminNexusSpringRepoJob()
createListViews("Admin", "Contains all admin jobs", "admin-.*")
createListViews("Seed Jobs", "Contains all seed jobs", ".*-seed-job")
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
  createAdminDockerJob()
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

def createAdminDockerJob() {

  println "############################################################################################################"
  println "Creating Admin Docker Test Job:"
  println "############################################################################################################"

  job("admin-docker-test") {
    logRotator {
        numToKeep(10)
    }
    steps {
      steps {
        shell('sudo /usr/bin/docker version')
      }
    }
    publishers {
      chucknorris()
    }
  }
}

def createAdminNexusSpringRepoJob() {

  println "############################################################################################################"
  println "Creating Admin Job to configure Spring Milestone Repositories in Nexus:"
  println "############################################################################################################"

  job("admin-add-spring-repos-to-nexus") {
    logRotator {
        numToKeep(10)
    }
    steps {
      steps {
        shell('sh jenkins/jobs/scripts/configureNexusSpringMilestoneRepositories.sh')
      }
    }
    publishers {
      chucknorris()
    }
  }
}
