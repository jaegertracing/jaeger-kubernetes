#!/bin/bash

set -o nounset
set -o errexit

kubectl create -f production-elasticsearch/configmap.yml
kubectl create -f production-elasticsearch/elasticsearch.yml

sleep 120
kubectl create -f jaeger-production-template.yml
