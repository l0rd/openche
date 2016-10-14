# openche
Scripts, patchs and templates to run Eclipse Che on OpenShift

## Deployment of Che on ADB 

1\. Get the [atomic developer bundle](https://github.com/projectatomic/adb-atomic-developer-bundle#how-do-i-install-and-run-adb)

2\. Start ADB and open a shell

```sh
vagrant up
vagrant ssh
```

3\. Get docker-latest (v.1.12.1) and disable older docker (v1.10.3)

```sh
# Stop Docker and OpenShift
sudo systemctl stop openshift
sudo systemctl stop docker
sudo systemctl disable docker
# Get docker-latest
sudo yum install docker-latest
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
oc login -u openshift-dev
oc new-project eclipse-che

# Create a serviceaccount with privileged scc
oc login -u admin
oc create serviceaccount cheserviceaccount
oadm policy add-scc-to-user privileged -z cheserviceaccount
```

5\. Deploy Che

```sh
# Get the script from github
git clone https://github.com/l0rd/openche
# Run the script
cd openche
./openche.sh deploy
```
Once the pod is successfully started Che dashboard should be now available at http://che.openshift.adb/