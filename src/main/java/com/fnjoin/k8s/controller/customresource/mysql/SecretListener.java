package com.fnjoin.k8s.controller.customresource.mysql;

import com.fnjoin.k8s.controller.customresource.base.ChildResourceListener;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecretListener extends ChildResourceListener<V1Secret> {

    @Override
    public SharedIndexInformer<V1Secret> createInformer(String space, SharedInformerFactory factory) {
        return getConnection().getSharedInformerFactory().sharedIndexInformerFor(params -> getConnection().getCoreV1Api().listNamespacedSecretCall(
                        getConnection().getSpace(),
                        null,
                        null,
                        null,
                        null,
                        "managed-by=fnjoin.com",
                        null,
                        params.resourceVersion,
                        null,
                        params.timeoutSeconds,
                        params.watch,
                        null),
                V1Secret.class,
                V1SecretList.class);
    }

    @SneakyThrows
    public void createSecret(String name, V1OwnerReference reference) {

        Map<String, String> labels = Map.of(
                "type", "mysql",
                "db", name,
                "managed-by", "fnjoin.com");

        // create the mysql secret
        getConnection().getCoreV1Api().createNamespacedSecret(getConnection().getSpace(),
                new V1Secret()
                        .apiVersion("v1")
                        .kind("Secret")
                        .metadata(new V1ObjectMeta()
                                .name(name)
                                .ownerReferences(Arrays.asList(reference))
                                .labels(labels))
                        .type("Opaque")
                        .stringData(Map.of("MYSQL_ROOT_PASSWORD", UUID.randomUUID().toString())),
                null,
                null,
                null,
                null);
        log.info("Created Secret: Name={}", name);
    }
}

