import groovy.json.JsonSlurper
import hudson.FilePath
import hudson.*

println "############################################################################################################"
println "Reading project configuration from json"

hudson.FilePath workspace = hudson.model.Executor.currentExecutor().getCurrentWorkspace()
File file = new File("${workspace}/src/main/groovy/6-edmp-project-configuration.json")
def slurper = new JsonSlurper()
def jsonText = file.getText()
projects = slurper.parseText( jsonText )

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
}
