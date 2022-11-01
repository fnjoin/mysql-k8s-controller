# mysql-k8s-controller

A sample Kubernetes controller written in Java with spring-boot. Most controllers are written in golang but the purpose of this repo is to show that it is possible to write a Kubernetes controller in Java as well. Writing a controller in Java instead of golang enables Java developers to learn about Kubernetes internals quicker since the language is not something they have to learn at the same time.

This controller handles the lifecycle of `mysql` type custom-resources. The custom-resource is defined by a CRD registered with the cluster. The controller relies on built-in Kubernetes resources to compose a `mysql` resource. It creates the following child-resources:

- A `Secret` which contains the login credentials to connect to the MySql instance.
- A `StatefulSet` which runs a single Pod MySql instance. The pod is assigned cpu, memory, and disk resources according to the `mysql` resource spec.
- A `Service` which fronts the MySql pod with a consistent name. This way the clients connect to the stable name provided by the service instead of figuring out which pod to connect to directly.

In addition to creating the above child resources, the controller also makes sure that these resource exist and are ready to use. The controller also keeps the `status` section of `mysql` resources updated with the current state of the child-resources

## Pre-requisites

- Running Kubernetes cluster - a single node *minikube* instance will do
- `kubectl` and/or `k9s` installed
- Access to the cluster via `kubectl` 
- JDK 17

## Running

To run the sample, we need to register the CRD with cluster and then run the controller application locally so that it acts as the controller for these CRD objects

### Apply the CRD

Apply the CRD so the cluster understands the `mysql` types of resources 

```
kubectl apply -f k8s/crd.yaml
```

### Run the application

```
./gradlew clean bootrun
``` 

## Testing


### Basics
Apply one of the sample `mysql` resource YAMLs

```
kubectl apply -f k8s/sample1.yaml
```

This creates a `db-1` resource. Verify it by running:

```
kubectl get mysql
```

Which will return a list like, notice the `READY` column:

```
NAME   STORAGE   MEMORY   CPU    READY
db-1   256Mi     128Mi    250m   false
```

### Details of a `mysql` resource

If you want to see the details of the `db-1` object, running `kubectl get mysql db-1 -o yaml` will return something like:

```yaml
apiVersion: fnjoin.com/v1
kind: Mysql
metadata:
  creationTimestamp: "2022-11-01T21:41:32Z"
  generation: 3
  name: db-1
  namespace: default
  resourceVersion: "97078"
  uid: 52a2abef-bfe8-433c-a2bf-37a40c6f70e4
spec:
  cpu: 250m
  memory: 128Mi
  storage: 256Mi
status:
  conditions:
    - lastTransitionTime: "2022-11-01T17:41:31.957460-04:00"
      status: AVAILABLE
      type: Service
    - lastTransitionTime: "2022-11-01T17:41:31.957475-04:00"
      status: AVAILABLE
      type: Secret
    - lastTransitionTime: "2022-11-01T17:41:38.431363-04:00"
      status: AVAILABLE
      type: StatefulSet
  ready: true
```

Notice the `status` section ... the `conditions` and `ready` flag are also maintained by the controller.

### Chaos Testing

For a bit of *chaos testing*, if you delete the corresponding `StatefulSet` object, notice how the controller re-creates that child-resource and how it keeps the `status` object updated.

Also, notice what happens when you delete a `mysql` resource. You can do that by running `kubectl delete mysql db-1`. Spoiler alert! the child resources will get cleaned up as well.