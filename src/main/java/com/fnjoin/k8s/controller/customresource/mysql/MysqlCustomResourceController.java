package com.fnjoin.k8s.controller.customresource.mysql;

import com.fnjoin.k8s.controller.config.KubernetesConnection;
import com.fnjoin.k8s.controller.customresource.base.ChildResourceListener;
import com.fnjoin.k8s.controller.customresource.base.CustomResourceController;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1Condition;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class MysqlCustomResourceController extends CustomResourceController<MysqlCustomResource, MysqlCustomResource.List> {

    private final StatefulsetListener statefulsetListener;
    private final SecretListener secretListener;
    private final ServiceListener serviceListener;


    public MysqlCustomResourceController(KubernetesConnection connection, ObjectMapper objectMapper, StatefulsetListener statefulsetListener, SecretListener secretListener, ServiceListener serviceListener) {
        super(connection,
                objectMapper,
                "fnjoin.com",
                "mysqls",
                "v1",
                MysqlCustomResource.class,
                MysqlCustomResource.List.class);
        this.statefulsetListener = statefulsetListener;
        this.secretListener = secretListener;
        this.serviceListener = serviceListener;
    }

    @PostConstruct
    public void init() {
        super.init(2, "Mysql", statefulsetListener, secretListener, serviceListener);
    }


    @Override
    public boolean isResourceInDesiredState(String uid, MysqlCustomResource resource) {

        String namespace = resource.getMetadata().getNamespace();
        String name = resource.getMetadata().getName();

        return List.of(statefulsetListener, secretListener, serviceListener)
                .stream()
                .allMatch(listener -> listener.find(namespace, name).isPresent());
    }

    @Override
    public boolean isStatusChangeNeeded(String uid, MysqlCustomResource resource) {
        return applyStatusChanges(resource.deepCopy(true));
    }

    @Override
    public MysqlCustomResource applyChanges(String uid, MysqlCustomResource resource) {

        String namespace = resource.getMetadata().getNamespace();
        String name = resource.getMetadata().getName();
        V1OwnerReference reference = createOwnerReference(uid, resource);

        if (statefulsetListener.find(namespace, name).isEmpty()) {
            statefulsetListener.createStatefulSet(namespace, name, reference, resource.getSpec());
        }

        if (secretListener.find(namespace, name).isEmpty()) {
            secretListener.createSecret(namespace, name, reference);
        }

        if (serviceListener.find(namespace, name).isEmpty()) {
            serviceListener.createService(namespace, name, reference);
        }

        return resource;
    }

    @Override
    public boolean applyStatusChanges(MysqlCustomResource resource) {

        String namespace = resource.getMetadata().getNamespace();
        String name = resource.getMetadata().getName();

        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicBoolean isReady = new AtomicBoolean(true);
        List<V1Condition> conditions = resource.getStatus().getConditions();

        statefulsetListener.find(namespace, name)
                .ifPresentOrElse(sts -> {
                    if (sts.getStatus() != null &&
                            Objects.equals(sts.getStatus().getReadyReplicas(), Integer.valueOf(1)) &&
                            Objects.equals(sts.getStatus().getReplicas(), Integer.valueOf(1))) {
                        statefulsetListener.updateCondition(changed, conditions, ChildResourceListener.ConditionStatus.AVAILABLE, "StatefulSet");
                    } else {
                        isReady.set(false);
                        statefulsetListener.updateCondition(changed, conditions, ChildResourceListener.ConditionStatus.CREATING, "StatefulSet");
                    }
                }, () -> {
                    isReady.set(false);
                    statefulsetListener.updateCondition(changed, conditions, ChildResourceListener.ConditionStatus.MISSING, "StatefulSet");
                });

        serviceListener.find(namespace, name)
                .ifPresentOrElse(service -> {
                    statefulsetListener.updateCondition(changed, conditions, ChildResourceListener.ConditionStatus.AVAILABLE, "Service");
                }, () -> {
                    isReady.set(false);
                    statefulsetListener.updateCondition(changed, conditions, ChildResourceListener.ConditionStatus.MISSING, "Service");
                });

        secretListener.find(namespace, name)
                .ifPresentOrElse(secret -> {
                    statefulsetListener.updateCondition(changed, conditions, ChildResourceListener.ConditionStatus.AVAILABLE, "Secret");
                }, () -> {
                    isReady.set(false);
                    statefulsetListener.updateCondition(changed, conditions, ChildResourceListener.ConditionStatus.MISSING, "Secret");
                });

        // change the ready flag if needed
        if (resource.getStatus().isReady() != isReady.get()) {
            resource.getStatus().setReady(isReady.get());
            changed.set(true);
        }

        return changed.get();
    }
}
