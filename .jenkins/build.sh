#!/usr/bin/env bash

set -xe

# Builds the components with tests, Docker image and spotBugs enabled
# Also generates the Talend components uispec
# $1: the Jenkinsfile's params.Action
# $2: Execute maven verify or deploy whether the branch is maintenance or master.
# $3: Execute sonar anlaysis or not, from jenkinsfile's params.SONAR_ANALYSIS
# $@: the extra parameters to be used in the maven commands
main() (
    echo "Param 1 : $1"
    echo "Param 2 : $2"
    echo "Param 3 : $3"

  jenkinsAction="${1?Missing Jenkins action}"; shift
  isOnMasterOrMaintenanceBranch="${2?Missing is on master or a maintenance branch}"; shift
  sonar="${3?Missing sonar option}"; shift
  extraBuildParams=("$@")

    mavenPhase='verify'
  if [[ "${isOnMasterOrMaintenanceBranch}" == 'true' ]]; then
    mavenPhase='deploy'
  fi

  # Real task
  # ITs profile is added for jdbc
  mvn "${mavenPhase}" \
      --errors \
      --batch-mode \
      --threads '1C' \
      --activate-profiles "${jenkinsAction},ITs" \
      "${extraBuildParams[@]}"

  if [[ "${sonar}" == 'true' ]]; then
    declare -a LIST_FILE_ARRAY=( $(find $(pwd) -type f -name 'jacoco.xml') )
    LIST_FILE=$(IFS=, ; echo "${LIST_FILE_ARRAY[*]}")
    # Why sonar plugin is not pom.xml: https://blog.sonarsource.com/we-had-a-dream-mvn-sonarsonar
    mvn sonar:sonar \
        --define 'sonar.host.url=https://sonar-eks.datapwn.com' \
        --define "sonar.login='${SONAR_LOGIN}'" \
        --define "sonar.password='${SONAR_PASSWORD}'" \
        --define "sonar.branch.name=${env.BRANCH_NAME}" \
        --define "sonar.coverage.jacoco.xmlReportPaths='${LIST_FILE}'" \
        --activate-profiles SONAR \
        "${extraBuildParams[@]}"
        #-s .jenkins/settings.xml \
        #-Dtalend.maven.decrypter.m2.location=${env.WORKSPACE}/.jenkins/ \
  fi
)

main "$@"
