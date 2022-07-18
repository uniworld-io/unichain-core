echo "------------------------------    sonar  check    ------------------------------"
export SONAR_SCANNER_VERSION=4.2.0.1873
export SONAR_SCANNER_HOME=/home/unichain-core/sonar-scanner-4.1.0.1829-linux
export PATH=$SONAR_SCANNER_HOME/bin:$PATH
export SONAR_SCANNER_OPTS="-server"
#export PATH=$PATH:/home/unichain-core/sonar-scanner-4.1.0.1829-linux/bin

#BUILDKITE_BRANCH="MiraculousWang:develop"

echo "current branch is : "$BUILDKITE_BRANCH
if [ $BUILDKITE_PULL_REQUEST = "false" ]; then

  sonar-scanner \
  -Dsonar.projectKey=unichain-core \
  -Dsonar.organization=uniworld-io \
  -Dsonar.sources=./actuator/src,./framework/src/main,./consensus/src,./chainbase/src,./common/src,./crypto/src,./protocol/src \
  -Dsonar.java.binaries=./actuator/build/classes,./framework/build/classes,./consensus/build/classes,./chainbase/build/classes,./common/build/classes,./crypto/build/classes,./protocol/build/classes \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.links.homepage=https://github.com/uniworld-io/unichain-core \
  -Dsonar.links.scm=https://github.com/uniworld-io/unichain-core \
  -Dsoanr.links.issue=https://github.com/uniworld-io/unichain-core/issues \
  -Dsonar.branch.name=$BUILDKITE_BRANCH \
  -Dsonar.coverage.jacoco.xmlReportPaths=./common/build/reports/jacoco/test/jacocoTestReport.xml,./consensus/build/reports/jacoco/test/jacocoTestReport.xml,./chainbase/build/reports/jacoco/test/jacocoTestReport.xml,./actuator/build/reports/jacoco/test/jacocoTestReport.xml,./framework/build/reports/jacoco/test/jacocoTestReport.xml \
  -Dsonar.login=1717c3c748ec2e0ea61e501b05458de243c4abcc > /data/checkStyle/sonar.log

  sleep 100


    SonarStatus_Url="https://sonarcloud.io/api/qualitygates/project_status?projectKey=unichain-core&branch="$BUILDKITE_BRANCH
    Status=`curl -s $SonarStatus_Url | jq '.projectStatus.status'`
    echo "current branch sonarcloud status is : "$Status
        if [ $Status = null ]; then
          echo "wait for check finish, 5m ....."
          sleep 300
          SonarStatus_Url="https://sonarcloud.io/api/qualitygates/project_status?projectKey=unichain-core&branch="$BUILDKITE_BRANCH
          Status=`curl -s $SonarStatus_Url | jq '.projectStatus.status'`
    fi

    if [ x"$Status" = x'"OK"' ];then
            echo "Sonar Check Pass"
            exit 0
    else
        echo ">>>>>>>>>>>>>>>>>>>>>>>>>>  Sonar Check Failed  <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<"
        echo ">>>>>>>>>>>> Please visit https://sonarcloud.io/dashboard?branch="$BUILDKITE_BRANCH"&id=unichain-core for more details  <<<<<<<<<<<<<<<<<<"
    touch checkFailTag
        exit 0
    fi
else
    echo "current PR number is : "$BUILDKITE_PULL_REQUEST

    sonar-scanner \
  -Dsonar.projectKey=unichain-core \
  -Dsonar.organization=uniworld-io \
  -Dsonar.sources=./actuator/src,./framework/src/main,./consensus/src,./chainbase/src,./common/src,./crypto/src,./protocol/src \
  -Dsonar.java.binaries=./actuator/build/classes,./framework/build/classes,./consensus/build/classes,./chainbase/build/classes,./common/build/classes,./crypto/build/classes,./protocol/build/classes \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.links.homepage=https://github.com/uniworld-io/unichain-core \
  -Dsonar.links.scm=https://github.com/uniworld-io/unichain-core \
  -Dsoanr.links.issue=https://github.com/uniworld-io/unichain-core/issues \
  -Dsonar.pullrequest.key=$BUILDKITE_PULL_REQUEST \
  -Dsonar.pullrequest.branch=$BUILDKITE_BRANCH \
  -Dsonar.pullrequest.base=$BUILDKITE_PULL_REQUEST_BASE_BRANCH \
  -Dsonar.coverage.jacoco.xmlReportPaths=./common/build/reports/jacoco/test/jacocoTestReport.xml,./consensus/build/reports/jacoco/test/jacocoTestReport.xml,./chainbase/build/reports/jacoco/test/jacocoTestReport.xml,./actuator/build/reports/jacoco/test/jacocoTestReport.xml,./framework/build/reports/jacoco/test/jacocoTestReport.xml \
  -Dsonar.login=1717c3c748ec2e0ea61e501b05458de243c4abcc > /data/checkStyle/sonar.log

  sleep 100

    SonarStatus_Url="https://sonarcloud.io/api/qualitygates/project_status?projectKey=unichain-core&pullRequest="$BUILDKITE_PULL_REQUEST
    Status=`curl -s $SonarStatus_Url | jq '.projectStatus.status'`
    if [ $Status = null ]; then
          echo "wait for check finish, 5m ....."
          sleep 300
          SonarStatus_Url="https://sonarcloud.io/api/qualitygates/project_status?projectKey=unichain-core&pullRequest="$BUILDKITE_PULL_REQUEST
          Status=`curl -s $SonarStatus_Url | jq '.projectStatus.status'`
    fi

    echo "current pullRequest sonarcloud status is : "$Status
    if [ x"$Status" = x'"OK"' ];then
            echo "Sonar Check Pass"
            exit 0
    else
       echo " --------------------------------  sonar check Failed ---------------------------------"
       echo ">>>>>>>>>>>>>>> Please visit https://sonarcloud.io/dashboard?id=unichain-core&pullRequest="$BUILDKITE_PULL_REQUEST" for more details  <<<<<<<<<<<<<<<<<<"
           echo "If this Sonar problem is not caused by your modification，Make sure you local branch is newest, And merge the newest uniworld-io/unichain-core"
           echo ">>>>>>>>>>>>>>>>>>>>>>>>>>   Sonar Check Failed   <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< "
       touch checkFailTag
           exit 0
    fi
fi
