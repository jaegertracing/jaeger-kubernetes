# Jaeger Kubernetes Templates

Support for deploying Jaeger into Kubernetes.

## All-in-one template

Template with in-memory storage with a limited functionality for local testing and development. Do not use this template in production environments.

To directly install everything you need:
```bash
kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/<version>/all-in-one/jaeger-all-in-one-template.yml
kubectl delete pod,service,deployment -l jaeger-infra # to remove everything
```

## Testing
Tests are based on [Arquillian Cube](http://arquillian.org/arquillian-cube/) which require an active connection to
kubernetes cluster (via `kubectl`). Currently all templates are tested on minikube.

```bash
minikube start
mvn clean verify -Pe2e
```
