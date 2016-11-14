#!/bin/bash

set -e

SVC_ID=$(oc get svc| grep che-ws | awk '{print $1}')
oc delete svc/${SVC_ID}

DC_ID=$(oc get dc | grep che-ws | awk '{print $1}')
oc delete dc/${DC_ID}

