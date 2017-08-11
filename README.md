[![Build Status][ci-img]][ci]

# Jaeger Kubernetes Templates

## Development setup
This template uses an in-memory storage with a limited functionality for local testing and development.
Do not use this template in production environments.

Install everything in the current namespace:
```bash
kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/all-in-one/jaeger-all-in-one-template.yml
```

Once everything is ready, `kubectl get service jaeger-query` tells you where to find Jaeger URL.
If you are using `minikube` to setup your Kubernetes cluster, the command `minikube service jaeger-query --url`
can be used instead.

## Production setup
This template deploys the Collector, Query Service (with UI) and Cassandra storage (StatefulSet) as separate individually scalable services.

```bash
kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/production/jaeger-production-template.yml
```

Note that it's OK to have the Query and Collector pods to be in an error state for the first minute or so. This is
because these components attempt to connect to Cassandra right away and hard fail if they can't after N attempts.

Once everything is ready, `kubectl get service jaeger-query` tells you where to find Jaeger URL.

### Deploying the agent as sidecar
The Jaeger Agent is designed to be deployed local to your service, so that it can receive traces via UDP keeping your
application's load minimal. As such, it's ideal to have the Agent to be deployed as a sidecar to your application's component,
just add it as a container within any struct that supports `spec.containers`, like a `Pod`, `Deployment` and so on.

For instance, assuming that your application is named `myapp` and the image is for it is `mynamespace/hello-myimage`, your
`Deployment` descriptor would be something like:

```yaml
- apiVersion: extensions/v1beta1
  kind: Deployment
  metadata:
    name: myapp
  spec:
    template:
      metadata:
        labels:
          app: myapp
      spec:
        containers:
        - image: mynamespace/hello-myimage
          name: myapp
          ports:
          - containerPort: 8080
        - image: jaegertracing/jaeger-agent
          name: jaeger-agent
          ports:
          - containerPort: 5775
            protocol: UDP
          - containerPort: 5778
          - containerPort: 6831
            protocol: UDP
          - containerPort: 6832
            protocol: UDP
          command:
          - "/go/bin/agent-linux"
          - "--collector.host-port=jaeger-collector.jaeger-infra.svc:14267"
```

The Jaeger Agent will then be available to your application at `localhost:5775`/`localhost:6831`/`localhost:6832`.
In most cases, you don't need to specify a hostname or port to your Jaeger Tracer, as it will default to the right
values already.

### Persistent storage
Even though this template uses a stateful Cassandra, backing storage is set to `emptyDir`. It's more
appropriate to create a `PersistentVolumeClaim`/`PersistentVolume` and use it instead. Note that this
Cassandra deployment does not support deleting pods or scaling down, as this might require 
administrative tasks that are dependent on the final deployment architecture.

## Uninstalling
If you need to remove the Jaeger components created by this template, run:

```bash
kubectl delete all,daemonset -l jaeger-infra
```

## Testing
Tests are based on [Arquillian Cube](http://arquillian.org/arquillian-cube/) which require an active connection to
kubernetes cluster (via `kubectl`). When executing tests from IDE make sure that template is copied to
`target/test-classes`.

```bash
minikube start
./mvnw clean verify -Pe2e
```

   [ci-img]: https://travis-ci.org/jaegertracing/jaeger-kubernetes.svg?branch=master
   [ci]: https://travis-ci.org/jaegertracing/jaeger-kubernetes
