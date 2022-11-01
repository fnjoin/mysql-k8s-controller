package com.fnjoin.k8s.controller.customresource.base;

import com.fnjoin.k8s.controller.config.KubernetesConnection;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.RateLimitingQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1Condition;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class ChildResourceListener<O extends KubernetesObject> {

    @Getter
    private SharedIndexInformer<O> informer;
    @Getter
    private KubernetesConnection connection;

    public Optional<O> find(String name) {
        String key = connection.getSpace() + "/" + name;
        O value = informer.getIndexer().getByKey(key);
        return Optional.ofNullable(value);
    }

    public void initInformer(KubernetesConnection connection, RateLimitingQueue<Request> workQueue, String requiredApiVersion, String requiredKind) {
        this.connection = connection;
        this.informer = createInformer(connection.getSpace(), connection.getSharedInformerFactory());

        this.informer.addEventHandler(new ResourceEventHandler() {
            @Override
            public void onAdd(KubernetesObject childResource) {
                addRequestToQueueForParent("ADDED", childResource);
            }

            @Override
            public void onUpdate(KubernetesObject oldChildResource, KubernetesObject newChildResource) {
                addRequestToQueueForParent("UPDATED", newChildResource);
            }

            @Override
            public void onDelete(KubernetesObject childResource, boolean deletedFinalStateUnknown) {
                addRequestToQueueForParent("DELETED", childResource);
            }

            private void addRequestToQueueForParent(String action, KubernetesObject childResource) {
                log.debug("Got child-resource event: Event={}, Type={}, Name={}", action, childResource.getClass().getSimpleName(), childResource.getMetadata().getName());
                Optional.ofNullable(childResource.getMetadata().getOwnerReferences().get(0))
                        .filter(ref -> ref.getApiVersion().equals(requiredApiVersion) && ref.getKind().equals(requiredKind))
                        .map(ref -> new Request(getConnection().getSpace(), ref.getName()))
                        .ifPresent(req -> workQueue.addRateLimited(req));
            }
        });
    }


    public static enum ConditionStatus {
        MISSING,
        CREATING,
        AVAILABLE,
    }

    public void updateCondition(AtomicBoolean changed, List<V1Condition> conditions, ConditionStatus childStatus, String type) {
        V1Condition condition = new V1Condition()
                .status(childStatus.name())
                .type(type)
                .lastTransitionTime(OffsetDateTime.now());

        Optional<V1Condition> existing = conditions.stream()
                .filter(cond -> cond.getType().equals(type))
                .findFirst();

        if (existing.isEmpty()) {
            conditions.add(condition);
            changed.set(true);
        } else {
            if (!condition.getStatus().equals(existing.get().getStatus())) {
                conditions.remove(existing.get());
                conditions.add(condition);
                changed.set(true);
            }
        }
    }


    public abstract SharedIndexInformer<O> createInformer(String space, SharedInformerFactory factory);
}
