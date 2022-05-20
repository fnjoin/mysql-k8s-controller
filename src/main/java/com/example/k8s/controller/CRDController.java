package com.example.k8s.controller;

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
import io.kubernetes.client.util.CallGeneratorParams;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public abstract class CRDController<O extends CRDObject<O>, L extends KubernetesListObject> implements SharedInformerFactoryDependant {

    private Controller controller;
    private Indexer<O> indexer;
    private CRDPatcher<O> patcher;

    private final KubernetesConnection connection;
    private final ObjectMapper objectMapper;
    private final String group;
    private final String plural;
    private final String version;
    private final Class<O> objectClass;
    private final Class<L> listClass;
    public void init(int workerCount, CRDReconciler.Worker<O> worker) {

        // build informer
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

        RateLimitingQueue<Request> workQueue = new DefaultRateLimitingQueue<>();
        informer.addEventHandler(new CRDEventHandler<>(workQueue,
                objectClass,
                this));

        indexer = informer.getIndexer();
        patcher = initPatcher();

        LeaseLock lock = new LeaseLock(connection.getSpace(), objectClass.getSimpleName().toLowerCase() + "-controller-leader", connection.getInstanceIdentity(), connection.getApiClient());
        LeaderElectionConfig electionConfig = new LeaderElectionConfig(lock, Duration.ofSeconds(60), Duration.ofSeconds(15), Duration.ofSeconds(3));
        controller = new LeaderElectingController(new LeaderElector(electionConfig),
                ControllerBuilder.defaultBuilder(connection.getSharedInformerFactory())
                        .withReconciler(new CRDReconciler<>(worker, patcher, this))
                        .withWorkQueue(workQueue)
                        .withWorkerCount(workerCount)
                        .build());
    }

    protected CRDPatcher<O> initPatcher() {
        return new CRDPatcher<>(connection,
                objectMapper,
                group,
                plural,
                version,
                objectClass);
    }

    public void objectDeleted(O obj, boolean deletedFinalStateUnknown) { }

    public KubernetesConnection getConnection() { return connection; }

    public Controller getController() {
        return controller;
    }

    public Optional<O> findOriginal(String name) {
        return Optional.ofNullable(indexer.getByKey(connection.getSpace() + "/" + name));
    }

    public Optional<O> find(String name) {
        return findOriginal(name)
                .map(item -> item.prunedCopy(true));
    }

    public List<O> list() {
        return indexer.list().stream()
                .map(item -> item.prunedCopy(true))
                .collect(Collectors.toList());
    }

    @SneakyThrows
    public void createOrUpdate(O newObj) {
        createOrUpdate(newObj, false);
    }

    @SneakyThrows
    public void createOrUpdate(O newObj, boolean includeStatusChanges) {
        String name = newObj.getMetadata().getName();
        Optional<O> oldObj = find(name);
        if (oldObj.isEmpty()) {
            connection.getCustomObjectsApi().createNamespacedCustomObject(group,
                    version,
                    connection.getSpace(),
                    plural,
                    newObj,
                    null,
                    null,
                    null);
        } else {
            O obj = oldObj.get();
            String jsonPatch = patcher.getJsonPatchForUpdate(obj, newObj, includeStatusChanges);
            patcher.executeJsonPatch(jsonPatch, obj.getMetadata().getName());
        }
    }

    @SneakyThrows
    public void deleteObject(String name) {
        connection.getCustomObjectsApi().deleteNamespacedCustomObject(
                group,
                version,
                connection.getSpace(),
                plural,
                name,
                null,
                null,
                null,
                null,
                null);
    }
}
