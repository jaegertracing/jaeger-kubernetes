[![Build Status][ci-img]][ci]

# Jaeger Kubernetes Templates

## Development setup
This template uses an in-memory storage with a limited functionality for local testing and development.
Do not use this template in production environments.

Install everything in the current namespace:
```bash
kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/all-in-one/jaeger-all-in-one-template.yml
```

Once everything is ready, `kubectl get service jaeger-all-in-one` tells you where to find Jaeger URL.
If you are using `minikube` to setup your Kubernetes cluster, the command `minikube service jaeger-all-in-one --url`
can be used instead.

## Production setup
This template deploys all Jaeger components as a separate services: StatefulSet Cassandra storage, agent as DaemonSet,
collector and query service with UI as Deployments. Each one of those can be managed and scaled individually.

```bash
kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/production/jaeger-production-template.yml
```

Note that it's OK to have the Query and Collector pods to be in an error state for the first minute or so. This is
because these components attempt to connect to Cassandra right away and hard fail if they can't after N attempts.

Your Agent hostname is `jaeger-agent.${NAMESPACE}.svc.cluster.local`

Once everything is ready, `kubectl get service jaeger-query` tells you where to find Jaeger URL.
If you are using `minikube` to setup your Kubernetes cluster, the command `minikube service jaeger-query --url`
can be used instead.

### Persistent storage
Even though this template uses a stateful Cassandra, backing storage is set to `emptyDir`. It's more
appropriate to create a `PersistentVolumeClaim`/`PersistentVolume` and use it instead.

Additionally, the Cassandra image is not any officially supported image. This will be changed soon.

## Uninstalling

If you need to remove the Jaeger components created by this template, run:

```bash
kubectl delete all,daemonset -l jaeger-infra
```

## Testing
Tests are based on [Arquillian Cube](http://arquillian.org/arquillian-cube/) which require an active connection to
kubernetes cluster (via `kubectl`).

```bash
minikube start
./mvnw clean verify -Pe2e
```

   [ci-img]: https://travis-ci.org/jaegertracing/jaeger-kubernetes.svg?branch=master
   [ci]: https://travis-ci.org/jaegertracing/jaeger-kubernetes
