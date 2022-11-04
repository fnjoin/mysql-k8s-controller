package com.fnjoin.k8s.controller.customresource.mysql;

import com.fnjoin.k8s.controller.customresource.base.ChildResourceListener;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceListener extends ChildResourceListener<V1Service> {

    @Override
    public SharedIndexInformer<V1Service> createInformer(SharedInformerFactory factory) {
        return getConnection().getSharedInformerFactory().sharedIndexInformerFor(params -> getConnection().getCoreV1Api().listServiceForAllNamespacesCall(
                        null,
                        null,
                        null,
                        "managed-by=fnjoin.com",
                        null,
                        null,
                        params.resourceVersion,
                        null,
                        params.timeoutSeconds,
                        params.watch,
                        null),
                V1Service.class,
                V1ServiceList.class);
    }

    @SneakyThrows
    public void createService(String namespace, String name, V1OwnerReference reference) {

        Map<String, String> labels = Map.of(
                "type", "mysql",
                "db", name,
                "managed-by", "fnjoin.com");

        // create the mysql service
        getConnection().getCoreV1Api().createNamespacedService(namespace,
                new V1Service()
                        .apiVersion("v1")
                        .kind("Service")
                        .metadata(new V1ObjectMeta()
                                .name(name)
                                .ownerReferences(Arrays.asList(reference))
                                .labels(labels))
                        .spec(new V1ServiceSpec()
                                .selector(labels)
                                .clusterIP("None")
                                .ports(Arrays.asList(new V1ServicePort()
                                        .protocol("TCP")
                                        .port(3306)
                                        .name("mysql")
                                        .targetPort(new IntOrString("mysql"))))),
                null,
                null,
                null,
                null);
        log.info("Created Service: Name={}", name);
    }
}
