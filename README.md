# mysql-k8s-controller

A sample Kubernetes controller written in Java with spring-boot. Most controllers are written in golang but the purpose of this repo is to show that it is possible to write a Kubernetes controller in Java as well. Writing a controller in Java instead of golang enables Java developers to learn about Kubernetes internals quicker since the language is not something they have to learn at the same time.

This controller defines and handles the lifecycle of `Mysql` type custom resources. The custom resource is defined by a [CRD registered with the cluster](k8s/crd.yaml). This controller relies on built-in Kubernetes resources to compose a `Mysql` resource. It creates the following child resources:

- A `Secret` which contains the login credentials to connect to the MySql instance.
- A `StatefulSet` which runs a single Pod MySql instance. The pod is assigned cpu, memory, and disk resources according to the `Mysql` resource spec.
- A `Service` which fronts the MySql pod with a consistent name. This way the clients connect to the stable name provided by the service instead of figuring out which pod to connect to directly.

In addition to creating the above child resources, the controller also makes sure that these resources continue to exist and are ready to use. The controller also keeps the `status` section of `Mysql` resource updated with the current state of the child resources.

## Pre-requisites

- Running Kubernetes cluster - a single node *minikube* instance will do
- `kubectl` and/or `k9s` installed
- Access to the cluster via `kubectl` 
- JDK 17

## Running

To run the sample, we need to register the CRD with cluster and then run this application so that it acts as the controller for these CRD objects. Apply the [CRD](k8s/crd.yaml) so the cluster understands the `Mysql` types of resources:

```
kubectl apply -f k8s/crd.yaml
```

### Locally

```
./gradlew clean bootrun
``` 

### In the cluster

First, we need to bundle the code into an OCI image. The [following script](k8s/create-java-image.sh) assumes we want to run in a local minikube instance. It builds the image using the docker daemon running inside minikube. Run it to create an image called (`fnjoin.com/mysql-controller-java:1.0`):

```
k8s/create-java-image.sh
```

Create the `mysql-controller` [namespace](k8s/namespace.yaml), where the controller will run:

```
kubectl apply -f k8s/namespace.yaml
```

Setup [*RBAC*](k8s/rbac.yaml) so the controller has permissions to run inside the cluster:

```
kubectl apply -f k8s/rbac.yaml
```

Deploy the controller as a [`Deployment`](k8s/deployment-java.yaml) resource:

```
kubectl apply -f k8s/deployment-java.yaml
```

## Testing

Once, the controller is running, we can test its functionality.

### Basics

Apply one of the [sample resource YAMLs](k8s/sample1.yaml) to create a `Mysql` instance with 256MB of storage, 128MB of memory, and 1/4 of a CPU:

```
kubectl apply -f k8s/sample1.yaml
```

This creates a `db-1` resource. Verify it by running:

```
kubectl get mysqls
```

Which will return a list like the following:

```
NAME   STORAGE   MEMORY   CPU    READY
db-1   256Mi     128Mi    250m   false
```

Notice the `READY` column, it is an attribute set by the controller to indicate if the `Mysql` resource is ready to be used or not. 

### Details of a `Mysql` resource

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
      status: CREATING
      type: StatefulSet
  ready: false
```

The `status` section is reserved for controllers like ours to update so that it informs us of inner details of what is happening with the resource. Notice that *StatefulSet*' condition is in the status of *CREATING* and hence the MySql resource itself is not ready.

After some time, the *StatefulSet* created by this controller for `db-1` will become ready and the controller will detect that and update this status.


### Other namesapces

The controller pays attention to all namespaces. If you create the `Mysql` resource in any other namespace, the child resources will be created in that namespace. 

### Chaos Testing

For a bit of *chaos testing*, if you delete the corresponding `StatefulSet` object, notice how the controller re-creates that child resource and how it keeps the `status` object updated.

Also, notice what happens when you delete a `Mysql` resource. You can do that by running `kubectl delete mysql db-1`. Spoiler alert! the child resources will get cleaned up as well.