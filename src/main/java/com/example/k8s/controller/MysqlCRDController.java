package com.example.k8s.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class MysqlCRDController extends CRDController<Mysql, Mysql.List> {

    public MysqlCRDController(KubernetesConnection connection, ObjectMapper objectMapper) {
        super(connection,
                objectMapper,
                "fnjoin.com",
                "mysqls",
                "v1",
                Mysql.class,
                Mysql.List.class);
    }

    @PostConstruct
    public void init() {
        super.init(2, (uid, mysql) -> {

            Mysql.Status status = mysql.getStatus();
            if (status == null || !status.isReady()) {
                createResources(uid, mysql);
            }

            return mysql;
        });
    }

    @SneakyThrows
    private void createResources(String uid, Mysql mysql) {

        String name = mysql.getMetadata().getName();
        V1OwnerReference reference = new V1OwnerReference()
                .uid(uid)
                .apiVersion(mysql.getApiVersion())
                .kind(mysql.getKind())
                .name(name);

        Map<String, String> labels = Map.of(
                "type", "mysql",
                "db", name);

        // create the mysql service
        getConnection().getCoreV1Api().createNamespacedService(getConnection().getSpace(),
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

        // create the mysql stateful-set
        getConnection().getAppsV1Api().createNamespacedStatefulSet(getConnection().getSpace(),
                new V1StatefulSet()
                        .apiVersion("apps/v1")
                        .kind("StatefulSet")
                        .metadata(new V1ObjectMeta()
                                .name(name)
                                .ownerReferences(Arrays.asList(reference))
                                .labels(labels))
                        .spec(new V1StatefulSetSpec()
                                .selector(new V1LabelSelector()
                                        .matchLabels(labels))
                                .serviceName(name)
                                .replicas(1)
                                .volumeClaimTemplates(Arrays.asList(new V1PersistentVolumeClaim()
                                        .metadata(new V1ObjectMeta()
                                                .name("data")
                                                .namespace(getConnection().getSpace())
                                                .labels(labels))
                                        .spec(new V1PersistentVolumeClaimSpec()
                                                .accessModes(Arrays.asList("ReadWriteOnce"))
                                                .resources(new V1ResourceRequirements()
                                                        .requests(Map.of("storage", new Quantity(mysql.getSpec().getStorage())))))))
                                .template(new V1PodTemplateSpec()
                                        .metadata(new V1ObjectMeta()
                                                .labels(labels))
                                        .spec(new V1PodSpec()
                                                .containers(Arrays.asList(new V1Container()
                                                        .name("mysql")
                                                        .image("mysql:8")
                                                        .ports(Arrays.asList(new V1ContainerPort()
                                                                .name("mysql")
                                                                .containerPort(3306)
                                                                .protocol("TCP")))
                                                        .volumeMounts(Arrays.asList(new V1VolumeMount()
                                                                .name("data")
                                                                .mountPath("/var/lib/mysql")
                                                                .subPath("mysql")))
                                                        .resources(new V1ResourceRequirements()
                                                                .requests(Map.of(
                                                                        "cpu", new Quantity(mysql.getSpec().getCpu()),
                                                                        "memory", new Quantity(mysql.getSpec().getMemory()))))
                                                        .env(Arrays.asList(
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
}
