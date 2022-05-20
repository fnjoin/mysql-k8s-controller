package com.example.k8s.controller;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.openapi.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatefulsetListener implements SharedInformerFactoryDependant {

    private static final Integer ONE = Integer.valueOf(1);

    private final KubernetesConnection connection;
    private final MysqlCRDController controller;
    private SharedIndexInformer<V1StatefulSet> informer;

    @PostConstruct
    public void init() {
        informer = connection.getSharedInformerFactory().sharedIndexInformerFor(params -> connection.getAppsV1Api().listNamespacedStatefulSetCall(
                        connection.getSpace(),
                        null,
                        null,
                        null,
                        null,
                        "type=mysql",
                        null,
                        params.resourceVersion,
                        null,
                        params.timeoutSeconds,
                        params.watch,
                        null),
                V1StatefulSet.class,
                V1StatefulSetList.class);

        informer.addEventHandler(new ResourceEventHandler<V1StatefulSet>() {
            @Override
            public void onAdd(V1StatefulSet obj) {
                dealWithMysql(obj);
            }

            @Override
            public void onUpdate(V1StatefulSet oldObj, V1StatefulSet newObj) {
                dealWithMysql(newObj);
            }

            @Override
            public void onDelete(V1StatefulSet obj, boolean deletedFinalStateUnknown) {
                /* do nothing here */
            }
        });
    }

    private void dealWithMysql(V1StatefulSet sts) {
        V1OwnerReference reference = sts.getMetadata().getOwnerReferences().get(0);
        if (reference.getApiVersion().equals("fnjoin.com/v1") && reference.getKind().equals("Mysql")) {
            V1StatefulSetStatus status = sts.getStatus();
            if (status != null) {
                boolean ready = Objects.equals(status.getReadyReplicas(), ONE) &&
                        Objects.equals(status.getReplicas(), ONE);
                controller.find(reference.getName())
                        .filter(mysql -> mysql.getStatus() == null || !Objects.equals(ready, mysql.getStatus().isReady()))
                        .map(mysql -> {
                            mysql.setStatus(Mysql.Status.builder()
                                    .ready(ready)
                                    .build());

                            log.info("Updating mysql instance: Name={}, Ready={}", reference.getName(), ready);
                            controller.createOrUpdate(mysql, true);

                            return mysql;
                        });
            }
        }
    }
}

