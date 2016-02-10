import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import hudson.FilePath
import hudson.*

println "############################################################################################################"
println "Setting up global variables"

gitCredentials = "jenkins"
gitBaseUrl = "git@git.dfl-digital-sports.de:intern/"
globalSettingsFile = "DFLGlobalMavenSettings"
mavenVersion = "Maven 3.3.3"
mailRecipients = "marcel.birkner@codecentric.de, matthias.schmidt@bundesliga.de"
jobPrefix = "DF-"
confluenceSiteName="wiki.dfl-digital-sports.de"
confluenceSpace="DEVTOOLS"

println "############################################################################################################"
println "Reading project configuration from json"

hudson.FilePath workspace = hudson.model.Executor.currentExecutor().getCurrentWorkspace()
File f = new File("${workspace}/src/main/groovy/project-configuration.json")
def slurper = new JsonSlurper()
def jsonText = f.getText()
projects = slurper.parseText( jsonText )

println "############################################################################################################"
println "Iterating all projects"
println ""
projects.each {

  println "############################################################################################################"
  println "############################################################################################################"
  println ""
  println "Creating Jenkins Jobs for Git Project: ${it.projectname}"
  println "-> ci=${it.ci}, deployment=${it.deployment}"
  println ""

  def gitProjectName=it.projectname
  def jobName = "${jobPrefix}${gitProjectName}"
  def ciJobName = "${jobName}-ci-1-build"
  def releaseJobName = "${jobName}-release-1-build"
  def gitProjectUrl = "${gitBaseUrl}${gitProjectName}.git"

  if( it.ci && it.deployment == false ) {
    println "Creating CI and RELEASE Job for ${gitProjectName}"
    createCIJob(gitProjectName, ciJobName, "", gitProjectUrl)
    createReleaseJob(gitProjectName, releaseJobName, gitProjectUrl)
  }

  if( it.ci && it.deployment ) {
    println "Creating CI, DEPLOYMENT and RELEASE Job for ${gitProjectName}"
    println "-> groupid=${it.groupid}, artifactid=${it.artifactid}"
    groupid = it.groupid
    artifactid = it.artifactid

    def deploymentJobName = "${jobName}-ci-2-deployment"
    def smoketestJobName = "${jobName}-ci-3-smoketest"
    def releaseDeploymentJobName = "${jobName}-release-2-deployment"
    def releaseDeploymentProdJobName = "${jobName}-release-2-deployment-prod"
    def releaseSmoketestJobName = "${jobName}-release-3-smoketest"
    createCIJob(gitProjectName, ciJobName, deploymentJobName, gitProjectUrl)
    createDeploymentJob(gitProjectName, deploymentJobName, ciJobName, gitProjectUrl, smoketestJobName, groupid, artifactid)
    createSmoketestJob(gitProjectName, smoketestJobName, ciJobName, gitProjectUrl)
    createReleaseJob(gitProjectName, releaseJobName, gitProjectUrl)
    createDeploymentJob(gitProjectName, releaseDeploymentJobName, "", gitProjectUrl, releaseSmoketestJobName, groupid, artifactid)
    createDeploymentJobProd(gitProjectName, releaseDeploymentProdJobName, "", gitProjectUrl, releaseSmoketestJobName, groupid, artifactid)
    createSmoketestJob(gitProjectName, releaseSmoketestJobName, releaseDeploymentJobName, gitProjectUrl)
  }

}

def createCIJob(def gitProjectName, def ciJobName, def deploymentJobName, def gitProjectUrl) {

  println "############################################################################################################"
  println "Creating CI Job:"
  println "- gitProjectName     = ${gitProjectName}"
  println "- ciJobName          = ${ciJobName}"
  println "- deploymentJobName  = ${deploymentJobName}"
  println "- gitProjectUrl      = ${gitProjectUrl}"
  println "############################################################################################################"

  job(ciJobName) {
    logRotator {
        daysToKeep(-1)
        numToKeep(10)
    }
    parameters {
      stringParam("BRANCH", "master", "Please provide Git Branch/Tag that should be build.")
      stringParam("RELEASE_VERSION", "DEV", "During CI this Version is always DEV.")
    }
    label("linux-slave")
    jdk("JDK 7u80")
    triggers {
      cron('H 6,12 * * *')
      scm('H/10 * * * *') {
        ignorePostCommitHooks()
      }
    }
    scm {
      git {
        remote {
          url(gitProjectUrl)
          credentials(gitCredentials)
        }
        createTag(false)
        branch('\${BRANCH}')
        clean()
      }
    }
    steps {
      maven {
          goals('clean versions:set -DnewVersion=${RELEASE_VERSION}-${BUILD_NUMBER} -P artifactory -U')
          mavenInstallation(mavenVersion)
          rootPOM('pom.xml')
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings(globalSettingsFile)
      }
      maven {
          goals('clean org.jacoco:jacoco-maven-plugin:0.7.4.201502262128:prepare-agent deploy -DaltDeploymentRepository=central::default::http://172.30.143.140:8081/artifactory/libs-release-local -Dmaven.test.failure.ignore=true -P artifactory,sonar -U')
          mavenInstallation(mavenVersion)
          rootPOM('pom.xml')
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings(globalSettingsFile)
      }
      maven {
          goals('sonar:sonar -P artifactory,sonar -U')
          mavenInstallation(mavenVersion)
          rootPOM('pom.xml')
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings(globalSettingsFile)
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
      if( deploymentJobName != "" ) {
          publishCloneWorkspace('**', '', 'Any', 'TAR', true, null)
          downstreamParameterized {
            trigger(deploymentJobName) {
              parameters {
                currentBuild()
                predefinedProp("VERSION", "\${RELEASE_VERSION}-\${BUILD_NUMBER}")
              }
            }
          }
      }
      mailer(mailRecipients, true, true)
    }
  }
  println ""
}

def createDeploymentJob(def gitProjectName, def deploymentJobName, def ciJobName, def gitProjectUrl, def smoketestJobName, def projectGroupId, def projectArtifactId) {

  println "############################################################################################################"
  println "Creating Deployment Job:"
  println "- gitProjectName    = ${gitProjectName}"
  println "- deploymentJobName = ${deploymentJobName}"
  println "- ciJobName         = ${ciJobName}"
  println "- gitProjectUrl     = ${gitProjectUrl}"
  println "- smoketestJobName  = ${smoketestJobName}"
  println "- projectGroupId    = ${projectGroupId}"
  println "- projectArtifactId = ${projectArtifactId}"
  println "############################################################################################################"

  job(deploymentJobName) {
    logRotator {
        daysToKeep(-1)
        numToKeep(10)
    }
    if( ciJobName == "" ) {
      configure { project ->
        def matrix = project / 'properties' / 'hudson.model.ParametersDefinitionProperty' {
          parameterDefinitions {
            'org.jvnet.hudson.plugins.repositoryconnector.VersionParameterDefinition' {
              name "${projectGroupId}.${projectArtifactId}"
              groupid projectGroupId
              repoid 'artifactory'
              artifactid projectArtifactId
              propertyName 'VERSION'
            }
          }
        }
      }
    }
    parameters {
      if( ciJobName != "" ) {
        stringParam("VERSION", "DEV", "During CI Deployments this is always DEV")
      }
      stringParam("APPLICATION_NAME", gitProjectName, "")
      choiceParam("TARGET_ENVIRONMENT", ['test', 'appr'], 'Select environment to deploy to')
      choiceParam("DEPLOYMENT_TYPE", ['application-and-configuration', 'application-only', 'configuration-only'], '')
    }
    label("slave-1")
    jdk("JDK 7u80")
    multiscm {
        if( ciJobName != "" ) {
          cloneWorkspace(ciJobName)
        }
        git {
            remote {
              url("git@git.dfl-digital-sports.de:jenkins-infrastructure/jenkins-deployment.git")
              credentials(gitCredentials)
              relativeTargetDir("jenkins-deployment")
            }
            createTag(false)
            branch('master')
            clean()
        }
    }
    steps {
      shell('if [[ "RELEASE LATEST" =~ $VERSION ]]; then echo "RELEASE and LATEST are not allowed as VERSION"; exit 1; fi')
      shell('cd jenkins-deployment && sh deployment.sh')
    }
    wrappers {
      buildUserVars()
    }
    publishers {
      chucknorris()
      slackNotifications {
            integrationToken('ChzlJLq09yATHllik0osRYqz')
            teamDomain('dflds-technology')
            projectChannel('#ci')
            notifyBuildStart()
            notifySuccess()
            notifyFailure()
      }
      publishCloneWorkspace('**', '', 'Any', 'TAR', true, null)
      downstreamParameterized {
        trigger(smoketestJobName) {
          parameters {
            currentBuild()
            predefinedProp("CONFIG_FILENAME", "\${APPLICATION_NAME}-config-\${BUILD_NUMBER}.txt")
          }
        }
      }
      mailer(mailRecipients, true, true)
    }
    configure { project ->
      project / publishers << 'com.myyearbook.hudson.plugins.confluence.ConfluencePublisher' {
        siteName confluenceSiteName
        attachArchivedArtifacts 'false'
        buildIfUnstable 'false'
			  spaceName confluenceSpace
        pageName '${APPLICATION_NAME} - ${TARGET_ENVIRONMENT}'
        editors {
          'com.myyearbook.hudson.plugins.confluence.wiki.editors.PrependEditor' {
            generator(class: "com.myyearbook.hudson.plugins.confluence.wiki.generators.PlainTextGenerator") {
              text '<table><td>${APPLICATION_NAME}</td><td>${VERSION}</td><td>${TARGET_ENVIRONMENT}</td><td>Started by: ${BUILD_USER}</td><td>${BUILD_TIMESTAMP}</td><td><a href="${BUILD_URL}">Build Job</a></td></table>'
            }
          }
        }
      }
    }
  }
  println ""
}

def createDeploymentJobProd(def gitProjectName, def deploymentJobName, def ciJobName, def gitProjectUrl, def smoketestJobName, def projectGroupId, def projectArtifactId) {

  println "############################################################################################################"
  println "Creating PROD Deployment Job:"
  println "- gitProjectName    = ${gitProjectName}"
  println "- deploymentJobName = ${deploymentJobName}"
  println "- ciJobName         = ${ciJobName}"
  println "- gitProjectUrl     = ${gitProjectUrl}"
  println "- smoketestJobName  = ${smoketestJobName}"
  println "- projectGroupId    = ${projectGroupId}"
  println "- projectArtifactId = ${projectArtifactId}"
  println "############################################################################################################"

  job(deploymentJobName) {
    logRotator {
        daysToKeep(-1)
        numToKeep(10)
    }
    configure { project ->
      def matrix = project / 'properties' / 'hudson.model.ParametersDefinitionProperty' {
        parameterDefinitions {
          'org.jvnet.hudson.plugins.repositoryconnector.VersionParameterDefinition' {
            name "${projectGroupId}.${projectArtifactId}"
            groupid projectGroupId
            repoid 'artifactory'
            artifactid projectArtifactId
            propertyName 'VERSION'
          }
        }
      }
    }
    parameters {
      stringParam("APPLICATION_NAME", gitProjectName, "")
      choiceParam("TARGET_ENVIRONMENT", ['prod'], 'Select environment to deploy to')
      choiceParam("DEPLOYMENT_TYPE", ['application-and-configuration', 'application-only', 'configuration-only'], '')
    }
    authorization {
        permissionAll('JENKINS_PROD_DEPLOYMENT')
        blocksInheritance()
    }
    label("slave-1")
    jdk("JDK 7u80")
    multiscm {
        if( ciJobName != "" ) {
          cloneWorkspace(ciJobName)
        }
        git {
            remote {
              url("git@git.dfl-digital-sports.de:jenkins-infrastructure/jenkins-deployment.git")
              credentials(gitCredentials)
              relativeTargetDir("jenkins-deployment")
            }
            createTag(false)
            branch('master')
            clean()
        }
    }
    steps {
      shell('if [[ "RELEASE LATEST" =~ $VERSION ]]; then echo "RELEASE and LATEST are not allowed as VERSION"; exit 1; fi')
      shell('cd jenkins-deployment && sh deployment.sh')
    }
    wrappers {
      buildUserVars()
    }
    publishers {
      chucknorris()
      slackNotifications {
            integrationToken('ChzlJLq09yATHllik0osRYqz')
            teamDomain('dflds-technology')
            projectChannel('#deployment')
            notifyBuildStart()
            notifySuccess()
            notifyFailure()
      }
      publishCloneWorkspace('**', '', 'Any', 'TAR', true, null)
      downstreamParameterized {
        trigger(smoketestJobName) {
          parameters {
            currentBuild()
            predefinedProp("CONFIG_FILENAME", "\${APPLICATION_NAME}-config-\${BUILD_NUMBER}.txt")
          }
        }
      }
      mailer(mailRecipients, true, true)
    }
    configure { project ->
      project / publishers << 'com.myyearbook.hudson.plugins.confluence.ConfluencePublisher' {
        siteName confluenceSiteName
        attachArchivedArtifacts 'false'
        buildIfUnstable 'false'
			  spaceName confluenceSpace
        pageName '${APPLICATION_NAME} - ${TARGET_ENVIRONMENT}'
        editors {
          'com.myyearbook.hudson.plugins.confluence.wiki.editors.PrependEditor' {
            generator(class: "com.myyearbook.hudson.plugins.confluence.wiki.generators.PlainTextGenerator") {
              text '<table><td>${APPLICATION_NAME}</td><td>${VERSION}</td><td>${TARGET_ENVIRONMENT}</td><td>Started by: ${BUILD_USER}</td><td>${BUILD_TIMESTAMP}</td><td><a href="${BUILD_URL}">Build Job</a></td></table>'
            }
          }
        }
      }
    }
  }
  println ""
}

def createSmoketestJob(def gitProjectName, def smoketestJobName, def ciJobName, def gitProjectUrl) {

  println "############################################################################################################"
  println "Creating Smoketest Job:"
  println "- smoketestJobName = ${smoketestJobName}"
  println "- gitProjectUrl    = ${gitProjectUrl}"
  println "############################################################################################################"

  job(smoketestJobName) {
    logRotator {
        daysToKeep(-1)
        numToKeep(10)
    }
    parameters {
      stringParam("VERSION", "DEV", "Will be provided by CI Job")
      stringParam("APPLICATION_NAME", gitProjectName, "")
      choiceParam("TARGET_ENVIRONMENT", ['test', 'appr', 'prod-inactive-until-security-setup'], 'Select environment to deploy to')
      choiceParam("DEPLOYMENT_TYPE", ['application-and-configuration', 'application-only', 'configuration-only'], '')
      stringParam("CONFIG_FILENAME", "", "")
    }
    label("slave-1")
    jdk("JDK 7u80")
    multiscm {
        cloneWorkspace(ciJobName)
        git {
            remote {
              url("git@git.dfl-digital-sports.de:jenkins-infrastructure/jenkins-deployment.git")
              credentials(gitCredentials)
              relativeTargetDir("jenkins-deployment")
            }
            createTag(false)
            branch('master')
            clean()
        }
    }
    steps {
      shell('cd jenkins-deployment && sh deploymentDone.sh')
    }
    publishers {
      chucknorris()
      slackNotifications {
            integrationToken('ChzlJLq09yATHllik0osRYqz')
            teamDomain('dflds-technology')
            projectChannel('#ci')
            notifyBuildStart()
            notifySuccess()
            notifyFailure()
      }
      mailer(mailRecipients, true, true)
    }
  }
  println ""
}


def createReleaseJob(def gitProjectName, def releaseJobName, def gitProjectUrl) {

  println "############################################################################################################"
  println "Creating RELEASE Job:"
  println "- gitProjectName = ${gitProjectName}"
  println "- releaseJobName = ${releaseJobName}"
  println "- gitProjectUrl  = ${gitProjectUrl}"
  println "############################################################################################################"

  job(releaseJobName) {
    logRotator {
        daysToKeep(-1)
        numToKeep(10)
    }
    parameters {
      stringParam("BRANCH", "master", "Please provide Git Branch/Tag that should be build.")
      stringParam("RELEASE_VERSION", "", "Please set the Release Version you want to build.")
      stringParam("GIT_PROJECT_NAME", gitProjectName, "Git Project Name")
    }
    label("linux-slave")
    jdk("JDK 7u80")
    scm {
      git {
        remote {
          url(gitProjectUrl)
          credentials(gitCredentials)
        }
        createTag(false)
        branch('\${BRANCH}')
        clean()
      }
    }
    triggers {
      scm('5/H * * * *')
    }
    steps {
      maven {
          goals('clean versions:set -DnewVersion=${RELEASE_VERSION}-${BUILD_NUMBER} -P artifactory -U')
          mavenInstallation(mavenVersion)
          rootPOM('pom.xml')
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings(globalSettingsFile)
      }
      maven {
          goals('clean org.jacoco:jacoco-maven-plugin:0.7.4.201502262128:prepare-agent deploy -DaltDeploymentRepository=central::default::http://172.30.143.140:8081/artifactory/libs-release-local -Dmaven.test.failure.ignore=true -P artifactory,sonar -U')
          mavenInstallation(mavenVersion)
          rootPOM('pom.xml')
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings(globalSettingsFile)
      }
      maven {
          goals('sonar:sonar -P artifactory,sonar -U')
          mavenInstallation(mavenVersion)
          rootPOM('pom.xml')
          mavenOpts('-Xms512m -Xmx1024m')
          providedGlobalSettings(globalSettingsFile)
      }
    }
    wrappers {
      buildUserVars()
    }
    publishers {
      chucknorris()
      mailer(mailRecipients, true, true)
    }
    configure { project ->
      project / publishers << 'com.myyearbook.hudson.plugins.confluence.ConfluencePublisher' {
        siteName confluenceSiteName
        attachArchivedArtifacts 'false'
        buildIfUnstable 'false'
			  spaceName confluenceSpace
        pageName "Versions - ${gitProjectName}"
        editors {
          'com.myyearbook.hudson.plugins.confluence.wiki.editors.PrependEditor' {
            generator(class: "com.myyearbook.hudson.plugins.confluence.wiki.generators.PlainTextGenerator") {
              text '<table><td>${GIT_PROJECT_NAME}</td><td>${RELEASE_VERSION}-${BUILD_NUMBER}</td><td>Started by: ${BUILD_USER}</td><td>${BUILD_TIMESTAMP}</td></table>'
            }
          }
        }
      }
    }
  }
  println ""
}

println "######################################################################"
println "Creating Nested Views"

def projectlist = []
projects.each {
  projectlist.add(it.projectname)
}
projectlist.sort()

println "######################################################################"
println "Create Job admin-create-confluence-pages-for-git-projects"
println projectlist

job("admin-create-confluence-pages-for-git-projects") {
  logRotator {
      daysToKeep(-1)
      numToKeep(10)
  }
  parameters {
    choiceParam("APPLICATION_NAME", projectlist, '')
  }
  label("master")
  configure { project ->
    project / publishers << 'com.myyearbook.hudson.plugins.confluence.ConfluencePublisher' {
      siteName confluenceSiteName
      attachArchivedArtifacts 'false'
      buildIfUnstable 'false'
      spaceName confluenceSpace
      pageName '${APPLICATION_NAME} - test'
      editors {
        'com.myyearbook.hudson.plugins.confluence.wiki.editors.PrependEditor' {
          generator(class: "com.myyearbook.hudson.plugins.confluence.wiki.generators.PlainTextGenerator") {
            text ''
          }
        }
      }
    }
    project / publishers << 'com.myyearbook.hudson.plugins.confluence.ConfluencePublisher' {
      siteName confluenceSiteName
      attachArchivedArtifacts 'false'
      buildIfUnstable 'false'
      spaceName confluenceSpace
      pageName '${APPLICATION_NAME} - appr'
      editors {
        'com.myyearbook.hudson.plugins.confluence.wiki.editors.PrependEditor' {
          generator(class: "com.myyearbook.hudson.plugins.confluence.wiki.generators.PlainTextGenerator") {
            text ''
          }
        }
      }
    }
    project / publishers << 'com.myyearbook.hudson.plugins.confluence.ConfluencePublisher' {
      siteName confluenceSiteName
      attachArchivedArtifacts 'false'
      buildIfUnstable 'false'
      spaceName confluenceSpace
      pageName '${APPLICATION_NAME} - prod'
      editors {
        'com.myyearbook.hudson.plugins.confluence.wiki.editors.PrependEditor' {
          generator(class: "com.myyearbook.hudson.plugins.confluence.wiki.generators.PlainTextGenerator") {
            text ''
          }
        }
      }
    }
    project / publishers << 'com.myyearbook.hudson.plugins.confluence.ConfluencePublisher' {
      siteName confluenceSiteName
      attachArchivedArtifacts 'false'
      buildIfUnstable 'false'
      spaceName confluenceSpace
      pageName 'Versions - ${APPLICATION_NAME}'
      editors {
        'com.myyearbook.hudson.plugins.confluence.wiki.editors.PrependEditor' {
          generator(class: "com.myyearbook.hudson.plugins.confluence.wiki.generators.PlainTextGenerator") {
            text ''
          }
        }
      }
    }
  }
}

nestedView('DFL Digital Framework') {
    views {
        for (project in projectlist) {
          nestedView(project) {
            views {
              listView("CI") {
                  jobs {
                    regex(/.*-${project}-ci-.*/)
                  }
                  columns {
                      status()
                      buildButton()
                      weather()
                      name()
                      lastSuccess()
                      lastFailure()
                  }
              }
              buildPipelineView("CI Build Pipeline") {
                selectedJob("DF-${project}-ci-1-build")
              }
              listView("Release & Deployment") {
                  jobs {
                    regex(/.*-${project}-release-.*|.*-${project}-deployment-.*/)
                  }
                  columns {
                      status()
                      buildButton()
                      weather()
                      name()
                      lastSuccess()
                      lastFailure()
                  }
              }
            }
          }
        }
    }
}
