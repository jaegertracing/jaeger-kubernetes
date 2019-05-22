[![Build Status][ci-img]][ci]

# Jaeger Kubernetes Templates

## Consider using the Jaeger Operator!

The current recommended way of installing and managing Jaeger in a production Kubernetes cluster is via the [Jaeger Operator](https://github.com/jaegertracing/jaeger-operator).

You can still use, report issues and send pull-requests against this repository, but not all features from the Operator are possible or will be backported to the templates from this repository here.

Use the templates from this repository if you need a quick start and don't want to install the Operator.

## How to contribute

Please see [CONTRIBUTING.md](https://github.com/jaegertracing/jaeger-kubernetes/blob/master/CONTRIBUTING.md)

## Development setup
This template uses an in-memory storage with a limited functionality for local testing and development. The image used defaults to the latest version [released](https://github.com/jaegertracing/jaeger/releases).
Do not use this template in production environments. Note that functionality may differ from the pinned docker versions for production.

Install everything in the current namespace:
```bash
kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/all-in-one/jaeger-all-in-one-template.yml
```

Once everything is ready, `kubectl get service jaeger-query` tells you where to find Jaeger URL.
If you are using `minikube` to setup your Kubernetes cluster, the command `minikube service jaeger-query --url`
can be used instead.

## Production setup

### Pinned Production Version
The docker image tags are manually pinned and manually updated. You should use the current pinned version for production.

### Backing storage

The Jaeger Collector and Query require a backing storage to exist before being started up. As a starting point for your own
templates, we provide basic templates deploying Cassandra and Elasticsearch. None of them are ready for production and should
be adapted before any real usage.

To use our Cassandra template:

    kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/production/configmap.yml
    kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/production/cassandra.yml

For Elasticsearch, use:

    kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/production-elasticsearch/configmap.yml
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

The main production template deploys the Collector and the Query Service (with UI) as separate individually scalable services,
as well as the Agent as `DaemonSet`.

    kubectl create -f https://raw.githubusercontent.com/jaegertracing/jaeger-kubernetes/master/jaeger-production-template.yml

If the backing storage is not ready by the time the Collector/Agent start, they will fail and Kubernetes will reschedule the
pod. It's advisable to either wait for the backing storage to stabilize, or to ignore such failures for the first few minutes.

Once everything is ready, `kubectl get service jaeger-query` tells you where to find Jaeger URL, or
`minikube service jaeger-query --url` when using `minikube`.

As the agent is deployed as a `DaemonSet`, the node's IP address can be stored as an environment variable and passed down
to the application as:

```yaml
env:
- name: JAEGER_AGENT_HOST
  valueFrom:
    fieldRef:
      fieldPath: status.hostIP
```

### Deploying the agent as sidecar
The Jaeger Agent is designed to be deployed local to your service, so that it can receive traces via UDP keeping your
application's load minimal. By default, the template above installs the agent as a `DaemonSet`, but this means that all
pods running on a given node will send data to the same agent. If that's not suitable for your workload, an alternative
is to deploy the agent as a sidecar. To accomplish that, just add it as a container within any struct that supports
`spec.containers`, like a `Pod`, `Deployment` and so on. More about this be found on the blog post
[Deployment strategies for the Jaeger Agent](https://medium.com/jaegertracing/deployment-strategies-for-the-jaeger-agent-1d6f91796d09).

Assuming that your application is named `myapp` and the image is for it is `mynamespace/hello-myimage`, your
`Deployment` descriptor would be something like:

```yaml
- apiVersion: apps/v1
  kind: Deployment
  metadata:
    name: myapp
  spec:
    selector:
      matchLabels:
        app.kubernetes.io/name: myapp
    template:
      metadata:
        labels:
          app.kubernetes.io/name: myapp
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
          - containerPort: 6831
            protocol: UDP
          - containerPort: 6832
            protocol: UDP
          - containerPort: 5778
            protocol: TCP
          args: ["--collector.host-port=jaeger-collector.jaeger-infra.svc:14267"]
```

The Jaeger Agent will then be available to your application at `localhost:5775`/`localhost:6831`/`localhost:6832`/`localhost:5778`.
In most cases, you don't need to specify a hostname or port to your Jaeger Tracer, as it will default to the right
values already.

###  Configure UDP/HTTP Senders
As the Jaeger Agent is deployed with the other components, your application needs to tell the Jaeger Client where to find the agent. Refer to your client's documentation for the appropriate mechanism, but most clients allow this to be set via the environment variable `JAEGER_AGENT_HOST` in environment variable like so:

```yaml
env:
    - name: JAEGER_SERVICE_NAME
    value: <YOUR SERVICE NAME>
    - name: JAEGER_AGENT_HOST
    value: jaeger-agent
    - name: JAEGER_SAMPLER_TYPE
    value: const
    - name: JAEGER_SAMPLER_PARAM
    value: "1"
```
The following service names are supported by HTTP sender:

| Service Name       | Port |
|--------------------|------|
| `jaeger-collector` |     14268 |
| `zipkin`           |  9411    |

The following service names are supported by UDP sender:

- jaeger-agent


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

For Cassandra, use:
```bash
kubectl run jaeger-spark-dependencies --schedule="55 23 * * *" --env="STORAGE=cassandra" --env="CASSANDRA_CONTACT_POINTS=cassandra:9042" --restart=Never --image=jaegertracing/spark-dependencies
```

For Elasticsearch, use:
```bash
kubectl run jaeger-spark-dependencies --schedule="55 23 * * *" --env="STORAGE=elasticsearch" --env="ES_NODES=elasticsearch:9200" --env="ES_USERNAME=changeme" --env="ES_PASSWORD=changeme" --restart=Never --image=jaegertracing/spark-dependencies
```

If you want to run the job only once and immediately then remove scheduled flag.

## Deploying Docker Tags
The Jaeger project automatically creates new Docker images with tags that mirror the release number. The production manifests uses pinned versions as to not accidentally break people on new releases.
> A general tip for deploying docker images (i.e. on kubernetes): it's recommended that you do not use the tag `:latest` in production but rather pin the latest version. See the [kubernetes best practices](https://kubernetes.io/docs/concepts/configuration/overview/#container-images) for more details.

## Helm support
A curated [Chart for Kubernetes Helm](https://github.com/kubernetes/charts/tree/master/incubator/jaeger) that adds all components required to run Jaeger.

## Uninstalling
If you need to remove the Jaeger components created by this template, run:

```bash
kubectl delete all,daemonset,configmap -l jaeger-infra
```

## Testing
Tests are based on [Arquillian Cube](http://arquillian.org/arquillian-cube/) which require an active connection to
kubernetes cluster (via `kubectl`). When executing tests from IDE make sure that template is copied to
`target/test-classes`.

```bash
minikube start
./mvnw clean verify -Pcassandra,elasticsearch,all-in-one
```

## License

[Apache 2.0 License](./LICENSE).


   [ci-img]: https://travis-ci.org/jaegertracing/jaeger-kubernetes.svg?branch=master
   [ci]: https://travis-ci.org/jaegertracing/jaeger-kubernetes
