# openche
Scripts, patchs and templates to run Eclipse Che on OpenShift

## Deploy Che on che.ci.centos.org

1\. Build openshift-connector branch of Che (optional)

```sh
# Get the source code 
git clone https://github.com/l0rd/che.git
cd che
git checkout openshift-connector

# Install pre-requisites (CentOS)
yum -y update
yum -y install centos-release-scl  java-1.8.0-openjdk-devel  git  patch
yum -y install rh-maven33 rh-nodejs4

source scl_source enable rh-maven33 rh-nodejs4
 
# Build Che 
export NPM_CONFIG_PREFIX=~/.node_modules
export PATH=$NPM_CONFIG_PREFIX/bin:$PATH
npm install -g bower gulp typings
mvn clean install -Pfast

# Build docker image and push it to a registry
docker build -t mariolet/che-server:openshiftconnector .
docke push mariolet/che-server:openshiftconnector
```

2\. Configure OpenShift

```sh
# Create OpenShift project
oc login -u openshift-dev che.ci.centos.org
oc new-project eclipse-che

# Create a serviceaccount with privileged scc
oc login -u system:admin -n eclipse-che
oc create serviceaccount cheserviceaccount
oc adm policy add-scc-to-user privileged -z cheserviceaccount
```

3\. Deploy Che

```sh
# Get the script from github
git clone https://github.com/l0rd/openche
# Prepare the environment
oc login -u openshift-dev che.ci.centos.org
export CHE_HOSTNAME=che.ci.centos.org
export CHE_IMAGE=mariolet/che-server:openshiftconnector
export DOCKER0_IP=10.1.0.1
export CHE_LOG_LEVEL=DEBUG
# Run the script
cd openche
./openche.sh deploy
```
Once the pod is successfully started Che dashboard should be now available at http://che.ci.centos.org/


## Deployment Che on Minishift

1\. Get [minishift](https://github.com/minishift/minishift#installation), create an OpenShift cluster (`minishift start`), open the web console (`minishift console`) and read the instructions to install the OpenShift CLI (help->Command Line Tools).

2\. Use that command to use minishift docker daemon: `eval $(minishift docker-env)`

3\. Configure OpenShift

```sh
# Create a serviceaccount with privileged scc
oc login -u admin -p admin
oc create serviceaccount cheserviceaccount
oc adm policy add-scc-to-user privileged -z cheserviceaccount

# Enable OpenShift router
docker pull openshift/origin-haproxy-router:`oc version | awk '{ print $2; exit }'`
oc adm policy add-scc-to-user hostnetwork -z router
oc adm router --create --service-account=router --expose-metrics --subdomain="openshift.mini"

# Create OpenShift project
oc login -u openshift-dev -p devel
oc new-project eclipse-che
```

4\. Update `/etc/hosts` with a line that associates minishift IP address (`minishift ip`) and the hostname `che.openshift.mini`

5\. Deploy Che

```sh
# Get the script from github
git clone https://github.com/l0rd/openche
# Prepare the environment
oc login -u openshift-dev -p devel
export CHE_HOSTNAME=che.openshift.mini
export CHE_IMAGE=codenvy/che-server:5.0.0-latest
export DOCKER0_IP=$(docker run -ti --rm --net=host alpine ip addr show docker0 | grep "inet\b" | awk '{print $2}' | cut -d/ -f1)
docker pull $CHE_IMAGE
# Run the script
cd openche
./openche.sh deploy
```
Once the pod is successfully started Che dashboard should be now available on the minishift console.

## Deployment of Che on ADB 

1\. Get the [atomic developer bundle](https://github.com/projectatomic/adb-atomic-developer-bundle#how-do-i-install-and-run-adb)

2\. Start a shell in ADB VM

```sh
vagrant ssh
```

3\. Get docker-latest (v.1.12.1) and disable older docker (v1.10.3)

```sh
# Stop Docker and OpenShift
sudo systemctl stop openshift
sudo systemctl stop docker
sudo systemctl disable docker
# Get docker-latest
sudo yum install -y docker-latest
# Update OpenShift service config
sudo sed -i.orig -e "s/^After=.*/After=docker-latest.service/g" /usr/lib/systemd/system/openshift.service
sudo sed -i.orig -e "s/^Requires=.*/Requires=docker-latest.service/g" /usr/lib/systemd/system/openshift.service
# Change docker-latest config to use docker-current configuration
sudo sed -i.orig -e "s/docker-latest/docker/g" /usr/lib/systemd/system/docker-latest.service
sudo sed -i.orig -e "s/Wants=docker-storage-setup.service/Wants=docker-latest-storage-setup.service/g" /usr/lib/systemd/system/docker-latest.service
# Restart Docker and OpenShift
sudo systemctl start docker-latest
sudo systemctl enable docker-latest
sudo systemctl start openshift
```

4\. Configure OpenShift

```sh
# Create OpenShift project
oc login -u openshift-dev -p devel
oc new-project eclipse-che

# Create a serviceaccount with privileged scc
oc login -u admin -p admin
oc create serviceaccount cheserviceaccount
oc adm policy add-scc-to-user privileged -z cheserviceaccount
```

5\. Deploy Che

```sh
# Get the script from github
git clone https://github.com/l0rd/openche
# Prepare the environment
oc login -u openshift-dev -p devel
export CHE_HOSTNAME=che.openshift.adb
export CHE_IMAGE=codenvy/che-server:5.0.0-latest
docker pull $CHE_IMAGE
# Run the script
cd openche
./openche.sh deploy
```
Once the pod is successfully started Che dashboard should be now available at http://che.openshift.adb/

