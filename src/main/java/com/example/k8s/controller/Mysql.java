package com.example.k8s.controller;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mysql implements CRDObject<Mysql> {

    String apiVersion;
    String kind;
    V1ObjectMeta metadata;
    Spec spec;
    Status status;

    @Override
    public Mysql prunedCopy(boolean keepStatus) {
        return Mysql.builder()
                .apiVersion(apiVersion)
                .kind(kind)
                .metadata(new V1ObjectMetaBuilder()
                        .withName(metadata.getName())
                        .withNamespace(metadata.getNamespace())
                        .withAnnotations(copyMap(metadata.getAnnotations()))
                        .withLabels(copyMap(metadata.getLabels()))
                        .build())
                .spec(Spec.builder()
                        .storage(spec.getStorage())
                        .memory(spec.getMemory())
                        .cpu(spec.getCpu())
                        .build())
                .status(keepStatus && status != null ? Status.builder()
                        .ready(status.isReady())
                        .build() : null)
                .build();
    }

    private <T> Map<String, T> copyMap(Map<String, T> map) {
        return new HashMap<>(map == null ? Collections.emptyMap() : map);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Spec {
        String storage;
        String memory;
        String cpu;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Status {
        boolean ready;
    }

    @Data
    public static class List implements KubernetesListObject {
        java.util.List<Mysql> items;
        String apiVersion;
        String kind;
        V1ListMeta metadata;
    }
}
