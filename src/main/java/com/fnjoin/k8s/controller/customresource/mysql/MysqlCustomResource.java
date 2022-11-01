package com.fnjoin.k8s.controller.customresource.mysql;

import com.fnjoin.k8s.controller.customresource.base.CustomResource;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1Condition;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlCustomResource implements CustomResource<MysqlCustomResource> {

    String apiVersion;
    String kind;
    V1ObjectMeta metadata;
    Spec spec;
    Status status;

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
        java.util.List<V1Condition> conditions;
    }

    @Data
    public static class List implements KubernetesListObject {
        java.util.List<MysqlCustomResource> items;
        String apiVersion;
        String kind;
        V1ListMeta metadata;
    }

    @Override
    public MysqlCustomResource deepCopy(boolean keepStatus) {
        return MysqlCustomResource.builder()
                .apiVersion(apiVersion)
                .kind(kind)
                .metadata(new V1ObjectMetaBuilder()
                        .withName(metadata.getName())
                        .withNamespace(metadata.getNamespace())
                        .withAnnotations(copyMap(metadata.getAnnotations()))
                        .withLabels(copyMap(metadata.getLabels()))
                        .withResourceVersion(metadata.getResourceVersion())
                        .build())
                .spec(Spec.builder()
                        .storage(spec.getStorage())
                        .memory(spec.getMemory())
                        .cpu(spec.getCpu())
                        .build())
                .status(!keepStatus ?
                        null :
                        status != null ?
                                Status.builder()
                                        .ready(status.isReady())
                                        .conditions(new ArrayList<>(status.getConditions() == null ? Collections.emptyList() : status.getConditions()))
                                        .build() :
                                Status.builder()
                                        .ready(false)
                                        .conditions(new ArrayList<>())
                                        .build())
                .build();
    }

    private <T> Map<String, T> copyMap(Map<String, T> map) {
        return new HashMap<>(map == null ? Collections.emptyMap() : map);
    }
}
