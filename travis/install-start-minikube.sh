#!/usr/bin/env bash
#
# Copyright 2017-2020 The Jaeger Authors
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

export CHANGE_MINIKUBE_NONE_USER=true
export MINIKUBE_WANTUPDATENOTIFICATION=false
export MINIKUBE_WANTREPORTERRORPROMPT=false
export MINIKUBE_HOME=$HOME
export CHANGE_MINIKUBE_NONE_USER=true
export KUBECONFIG=$HOME/.kube/config

curl -Lo minikube https://storage.googleapis.com/minikube/releases/v1.9.2/minikube-linux-amd64 && chmod +x minikube && sudo mv minikube /usr/local/bin/
curl -Lo kubectl https://storage.googleapis.com/kubernetes-release/release/v1.18.2/bin/linux/amd64/kubectl && chmod +x kubectl && sudo mv kubectl /usr/local/bin/

mkdir -p $HOME/.kube $HOME/.minikube
touch $KUBECONFIG

sudo minikube start --profile=minikube --vm-driver=none --kubernetes-version=v1.18.2
minikube update-context --profile=minikube

eval "$(minikube docker-env --profile=minikube)" && export DOCKER_CLI='docker'

# Wait for kube-dns to be ready.
JSONPATH='{range .items[*]}{@.metadata.name}:{range @.status.conditions[*]}{@.type}={@.status};{end}{end}'; until kubectl -n kube-system get pods -lk8s-app=kube-dns -o jsonpath="$JSONPATH" 2>&1 | grep -q "Ready=True"; do sleep 1;echo "waiting for kube-dns to be available"; kubectl get pods --all-namespaces; done
