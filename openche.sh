#!/bin/sh

#### Example of Openshift Setup using container version of OpenShift
#  
## Start OpenShift Origin as a container ##
# docker run -d --name "origin" \
#         --privileged --pid=host --net=host \
#         -v /:/rootfs:ro -v /var/run:/var/run:rw -v /sys:/sys -v /var/lib/docker:/var/lib/docker:rw \
#         -v /var/lib/origin/openshift.local.volumes:/var/lib/origin/openshift.local.volumes \
#         openshift/origin start
#
## Create OpenShift project ##
# oc login -u mario
# oc new-project openche
#
## Create a serviceaccount with privileged scc ##
# oc login -u system:admin
# oc create serviceaccount cheserviceaccount
# oadm policy add-scc-to-user privileged -z cheserviceaccount

## Get the template definition and creates it
# If the template already exists use oc replace instead:
# oc replace -f che-template.json 

set_parameters() {
    DEFAULT_CHE_HOSTNAME=che.openshift.adb
    DEFAULT_CHE_IMAGE=codenvy/che-server:nightly

    CHE_HOSTNAME=${CHE_HOSTNAME:-${DEFAULT_CHE_HOSTNAME}}
    CHE_IMAGE=${CHE_IMAGE:-${DEFAULT_CHE_IMAGE}}
}

check_prerequisites() {
    # oc must be installed
    command -v oc >/dev/null 2>&1 || { echo >&2 "I require oc but it's not installed.  Aborting."; exit 1; }

    # there should be a service account called cheserviceaccount
    oc get serviceaccounts cheserviceaccount >/dev/null 2>&1 || { echo >&2 "Command 'oc get serviceaccounts cheserviceaccount' failed. A serviceaccount named cheserviceaccount should exist. Aborting."; exit 1; }
    
    # docker must be installed
    command -v docker >/dev/null 2>&1 || { echo >&2 "I require docker but it's not installed.  Aborting."; exit 1; }
    
    # interface docker0 should exist
    ip addr show docker0  >/dev/null 2>&1 || { echo >&2 "Bridge docker0 not found.  Aborting."; exit 1; }
    
    # Check if -v /nonexistantfolder:Z works
    # A workaround is to remove --selinux-enabled option in /etc/sysconfig/docker
    docker create --name openchetest -v /tmp/nonexistingfolder:/tmp:Z docker.io/busybox sh >/dev/null 2>&1 || { echo >&2 "Command 'docker create -v /tmp/nonexistingfolder:/tmp:Z busybox sh' failed. Che won't be able to create workspaces in this conditions. A workaround is to deactivate Docker SELinux option. Aborting."; exit 1; }
    docker rm openchetest >/dev/null 2>&1
    rmdir /tmp/nonexistingfolder >/dev/null 2>&1
}

template_already_exists() {
    TEMPLATE_NAME=${1}
    oc get template eclipse-che | grep eclipse-chel | wc -l
}

## Get latest version of the template from git repository and install it
download_and_install_template() {
    TEMPLATE_URL=https://raw.githubusercontent.com/l0rd/openche/master/che.json
    curl -sSL ${TEMPLATE_URL} > che-template.json
    echo "${TEMPLATE_URL} downladed"
    oc create -f che-template.json >/dev/null 2>&1 || oc replace -f che-template.json >/dev/null 2>&1
    echo "Template installed"
}

## Create a new app based on `eclipse_che` template and deploy it
deploy() {
    DOCKER0_IP=$(ip addr show docker0 | grep "inet\b" | awk '{print $2}' | cut -d/ -f1)

    oc new-app --template=eclipse-che --param=HOSTNAME_HTTP=${CHE_HOSTNAME} \
                                    --param=CHE_SERVER_DOCKER_IMAGE=${CHE_IMAGE} \
                                    --param=DOCKER0_BRIDGE_IP=${DOCKER0_IP}
    oc deploy che-server --latest
    echo "OPENCHE: Waiting 5 seconds for the app to start"
    sleep 5
    POD_ID=$(oc get pods | grep che-server | grep -v che-server-deploy | awk '{print $1}')
    echo "Che pod starting (id $POD_ID)..."
}

## Uninstall everything
delete() {
    POD_ID=$(oc get pods | grep che-server | awk '{print $1}')
    oc delete pod/${POD_ID}
    oc delete dc/che-server
    oc delete route/che-server
    oc delete svc/che-server
}

parse_command_line () {
  if [ $# -eq 0 ]; then
    usage
    exit
  fi

  case $1 in
    deploy|delete)
      ACTION=$1
    ;;
    -h|--help)
      usage
      exit
    ;;
    *)
      # unknown option
      echo "ERROR: You passed an unknown command line option."
    ;;
  esac
}

usage () {
  USAGE="Usage: ${0} [COMMAND]
     deploy                             Install and deploys che-server Application on OpenShift
     delete                             Delete che-server related objects from OpenShift
"
  printf "%s" "${USAGE}"
}

set -e
set -u

set_parameters
check_prerequisites
parse_command_line "$@"

case ${ACTION} in
  deploy)
    download_and_install_template
    deploy
  ;;
  delete)
    delete
  ;;
esac
