!#/bin/bash

#!/bin/bash

set -e

oc delete configmaps/resolvconf
oc delete svc/che-ws-xxxx-service
oc delete dc/che-ws-xxxx-dc
POD_ID=$(oc get pods | grep che-ws | awk '{print $1}')
oc delete pod/${POD_ID}
