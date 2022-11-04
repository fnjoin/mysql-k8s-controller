package com.fnjoin.k8s.controller.customresource.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnjoin.k8s.controller.config.KubernetesConnection;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.LeaderElectingController;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.kubernetes.client.extended.workqueue.DefaultRateLimitingQueue;
import io.kubernetes.client.extended.workqueue.RateLimitingQueue;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.util.CallGeneratorParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public abstract class CustomResourceController<O extends CustomResource<O>, L extends KubernetesListObject> {

    private Controller controller;
    private Indexer<O> indexer;

    private final KubernetesConnection connection;
    private final ObjectMapper objectMapper;
    private final String group;
    private final String plural;
    private final String version;
    private final Class<O> objectClass;
    private final Class<L> listClass;

    public void init(int workerCount, String kind, ChildResourceListener... childListeners) {

        // Create the informer
        SharedIndexInformer<O> informer =
                connection.getSharedInformerFactory().sharedIndexInformerFor((CallGeneratorParams params) -> connection.getCustomObjectsApi().listClusterCustomObjectCall(group,
                                version,
                                plural,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                params.resourceVersion,
                                null,
                                params.timeoutSeconds,
                                params.watch,
                                null),
                        objectClass,
                        listClass);
        indexer = informer.getIndexer();

        // setup the informer to add requests to work-queue when events are received
        RateLimitingQueue<Request> workQueue = new DefaultRateLimitingQueue<>();
        informer.addEventHandler(new CustomResourceEventHandler<>(workQueue));

        // initialize child listeners to inform work-queue on child-resource events
        for (ChildResourceListener childListener : childListeners) {
            childListener.initInformer(connection, workQueue, group + "/" + version, kind);
        }

        // creating the internal controller
        controller = new LeaderElectingController(getLeaderElector(),
                ControllerBuilder.defaultBuilder(connection.getSharedInformerFactory())
                        .withReconciler(new CustomResourceReconciler(connection,
                                objectMapper,
                                group,
                                plural,
                                version,
                                this))
                        .withWorkQueue(workQueue)
                        .withWorkerCount(workerCount)
                        .withReadyFunc(() -> informer.hasSynced() && Arrays.stream(childListeners)
                                .allMatch(listener -> listener.hasSynced()))
                        .build());

    }

    private LeaderElector getLeaderElector() {

        // setup leader-election so only one controller instance handles reconciliation
        LeaseLock lock = new LeaseLock(connection.getSpace(),
                objectClass.getSimpleName().toLowerCase() + "-controller-leader",
                connection.getInstanceIdentity(),
                connection.getApiClient());

        // See 'LeaderElectionConfig' section at https://github.com/kubernetes/client-go/blob/master/tools/leaderelection/leaderelection.go
        return new LeaderElector(new LeaderElectionConfig(lock,
                Duration.ofSeconds(30),
                Duration.ofSeconds(15),
                Duration.ofSeconds(5)));
    }

    public Controller getController() {
        return controller;
    }

    public Optional<O> find(String namespace, String name) {
        String key = namespace + "/" + name;
        return Optional.ofNullable(indexer.getByKey(key));
    }

    public V1OwnerReference createOwnerReference(String uid, O resource) {
        return new V1OwnerReference()
                .uid(uid)
                .apiVersion(resource.getApiVersion())
                .kind(resource.getKind())
                .controller(true)
                .name(resource.getMetadata().getName());
    }

    public abstract boolean isResourceInDesiredState(String uid, O resource);

    public abstract boolean isStatusChangeNeeded(String uid, O resource);

    public abstract O applyChanges(String uid, O resource);

    /**
     * @return <code>true</code> if there were any changes made to the resource's status, <code>false</code> otherwise.
     */
    public abstract boolean applyStatusChanges(O resource);
}
