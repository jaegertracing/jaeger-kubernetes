# Jaeger Kubernetes Templates

Support for deploying Jaeger into Kubernetes.

## All-in-one template

Template with in-memory storage with a limited functionality for local testing and development. Do not use this template in production environments.

To directly install everything you need:
```bash
kubectl create -f TODO link to Github
kubectl delete pod,service,deployment -l jaeger-infra # to remove everything
```

## Testing
This templates are tested on K8s provided by Google Cloud Engine. Tests are written in Java using 
fabric8-arquillian framework.
```bash
// connect kubectl to k8s cluster
KUBERNETES_NAMESPACE=test mvn clean test 
```
