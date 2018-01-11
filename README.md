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

### Backing storage

The Jaeger Collector and Query require a backing storage to exist before being started up. As a starting point for your own 
templates, we provide basic templates deploying Cassandra and Elasticsearch. None of them are ready for production and should
be adapted before any real usage.

To use our Cassandra template:

    kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/production/cassandra.yml

For Elasticsearch, use:

    kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/production-elasticsearch/elasticsearch.yml

The Cassandra template includes also a Kubernetes `Job` that creates the schema required by the Jaeger components. It's advisable
to wait for this job to finish before deploying the Jaeger components. To check the status of the job, run:

    kubectl get job jaeger-cassandra-schema-job

The job should have `1` in the `SUCCESSFUL` column.

### Jaeger configuration

The Jaeger Collector, Query and Agent require a `ConfigMap` to exist on the same namespace, named `jaeger-configuration`.
This `ConfigMap` is included in the storage templates, as each backing storage have their own specific configuration entries,
but in your environment, you'll probably manage it differently.

If changes are required for the configuration, the `edit` command can be used:

    kubectl edit configmap jaeger-configuration

### Jaeger components

The main production template deploys the Collector and the Query Service (with UI) as separate individually scalable services.

    kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/jaeger-production-template.yml

If the backing storage is not ready by the time the Collector/Agent start, they will fail and Kubernetes will reschedule the
pod. It's advisable to either wait for the backing storage to stabilize, or to ignore such failures for the first few minutes.

Once everything is ready, `kubectl get service jaeger-query` tells you where to find Jaeger URL, or 
`minikube service jaeger-query --url` when using `minikube`

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

### Service Dependencies
Jaeger production deployment needs an external process to derive dependency links between
services. Project [spark-dependencies](https://github.com/jaegertracing/spark-dependencies) provides
this functionality.

This job should be periodically run before end of a day. The following command creates `CronJob`
scheduled 5 minutes before the midnight.

```bash
kubectl run jaeger-spark-dependencies --schedule="55 23 * * *" --env="STORAGE=cassandra" --env="CASSANDRA_CONTACT_POINTS=cassandra:9042"  --restart=Never --image=jaegertracing/spark-dependencies
```

If you want to run the job only once and immediately then remove scheduled flag.

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
./mvnw clean verify -Pcassandra,elasticsearch,all-in-one
```

   [ci-img]: https://travis-ci.org/jaegertracing/jaeger-kubernetes.svg?branch=master
   [ci]: https://travis-ci.org/jaegertracing/jaeger-kubernetes
