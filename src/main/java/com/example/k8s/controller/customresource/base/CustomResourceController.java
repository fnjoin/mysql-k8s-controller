package com.example.k8s.controller.customresource.base;

import com.example.k8s.controller.config.KubernetesConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public abstract class CustomResourceController<O extends CustomResource<O>, L extends KubernetesListObject> implements SharedIndexInformerUser {

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
                connection.getSharedInformerFactory().sharedIndexInformerFor((CallGeneratorParams params) -> connection.getCustomObjectsApi().listNamespacedCustomObjectCall(group,
                                version,
                                connection.getSpace(),
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


        // setup leader-election so only one controller instance handles reconciliation
        LeaseLock lock = new LeaseLock(connection.getSpace(), objectClass.getSimpleName().toLowerCase() + "-controller-leader", connection.getInstanceIdentity(), connection.getApiClient());
        LeaderElectionConfig electionConfig = new LeaderElectionConfig(lock, Duration.ofSeconds(60), Duration.ofSeconds(15), Duration.ofSeconds(3));

        // creating the internal controller
        controller = new LeaderElectingController(new LeaderElector(electionConfig),
                ControllerBuilder.defaultBuilder(connection.getSharedInformerFactory())
                        .withReconciler(new CustomResourceReconciler(connection,
                                objectMapper,
                                group,
                                plural,
                                version,
                                this))
                        .withWorkQueue(workQueue)
                        .withWorkerCount(workerCount)
                        .build());

        // initialize child listeners to inform work-queue on child-resource events
        for (ChildResourceListener childListener : childListeners) {
            childListener.initInformer(connection, workQueue, group + "/" + version, kind);
        }
    }

    public Controller getController() {
        return controller;
    }

    public Optional<O> find(String name) {
        String key = connection.getSpace() + "/" + name;
        return Optional.ofNullable(indexer.getByKey(key));
    }

    public List<O> list() {
        return indexer.list().stream()
                .map(item -> item.deepCopy(true))
                .collect(Collectors.toList());
    }

    public V1OwnerReference createOwnerReference(String uid, O resource) {
        return new V1OwnerReference()
                .uid(uid)
                .apiVersion(resource.getApiVersion())
                .kind(resource.getKind())
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
