package com.fnjoin.k8s.controller.customresource.mysql;

import com.fnjoin.k8s.controller.customresource.base.ChildResourceListener;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatefulsetListener extends ChildResourceListener<V1StatefulSet> {

    @SneakyThrows
    public void createStatefulSet(String name, V1OwnerReference reference, MysqlCustomResource.Spec spec) {

        Map<String, String> labels = Map.of(
                "type", "mysql",
                "db", name,
                "managed-by", "fnjoin.com");

        getConnection().getAppsV1Api().createNamespacedStatefulSet(getConnection().getSpace(),
                new V1StatefulSet()
                        .apiVersion("apps/v1")
                        .kind("StatefulSet")
                        .metadata(new V1ObjectMeta()
                                .name(name)
                                .ownerReferences(List.of(reference))
                                .labels(labels))
                        .spec(new V1StatefulSetSpec()
                                .selector(new V1LabelSelector()
                                        .matchLabels(labels))
                                .serviceName(name)
                                .replicas(1)
                                .volumeClaimTemplates(List.of(new V1PersistentVolumeClaim()
                                        .metadata(new V1ObjectMeta()
                                                .name("data")
                                                .namespace(getConnection().getSpace())
                                                .labels(labels))
                                        .spec(new V1PersistentVolumeClaimSpec()
                                                .accessModes(List.of("ReadWriteOnce"))
                                                .resources(new V1ResourceRequirements()
                                                        .requests(Map.of("storage", new Quantity(spec.getStorage())))))))
                                .template(new V1PodTemplateSpec()
                                        .metadata(new V1ObjectMeta()
                                                .labels(labels))
                                        .spec(new V1PodSpec()
                                                .containers(List.of(new V1Container()
                                                        .name("mysql")
                                                        .image("mysql:8")
                                                        .ports(List.of(new V1ContainerPort()
                                                                .name("mysql")
                                                                .containerPort(3306)
                                                                .protocol("TCP")))
                                                        .volumeMounts(List.of(new V1VolumeMount()
                                                                .name("data")
                                                                .mountPath("/var/lib/mysql")
                                                                .subPath("mysql")))
                                                        .resources(new V1ResourceRequirements()
                                                                .requests(Map.of(
                                                                        "cpu", new Quantity(spec.getCpu()),
                                                                        "memory", new Quantity(spec.getMemory()))))
                                                        .env(List.of(
                                                                new V1EnvVar()
                                                                        .name("MYSQL_DATABASE")
                                                                        .value("db"),
                                                                new V1EnvVar()
                                                                        .name("MYSQL_ROOT_PASSWORD")
                                                                        .valueFrom(new V1EnvVarSource()
                                                                                .secretKeyRef(new V1SecretKeySelector()
                                                                                        .key("MYSQL_ROOT_PASSWORD")
                                                                                        .name(name)))))
                                                        .livenessProbe(new V1Probe()
                                                                .exec(new V1ExecAction()
                                                                        .command(Arrays.asList("mysqladmin", "-p${MYSQL_ROOT_PASSWORD}", "ping")))
                                                                .initialDelaySeconds(15)
                                                                .periodSeconds(15)
                                                                .timeoutSeconds(5))
                                                        .readinessProbe(new V1Probe()
                                                                .tcpSocket(new V1TCPSocketAction()
                                                                        .port(new IntOrString(3306)))
                                                                .initialDelaySeconds(5)
                                                                .periodSeconds(2)
                                                                .timeoutSeconds(1))))))),
                null,
                null,
                null,
                null);
        log.info("Created StatefulSet: Name={}", name);
    }

    @Override
    public SharedIndexInformer<V1StatefulSet> createInformer(String space, SharedInformerFactory factory) {
        return getConnection().getSharedInformerFactory().sharedIndexInformerFor(params -> getConnection().getAppsV1Api().listNamespacedStatefulSetCall(
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
                V1StatefulSet.class,
                V1StatefulSetList.class);
    }
}
