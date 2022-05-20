package com.example.k8s.controller;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.*;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KubernetesConnection {

    ApiClient apiClient;
    CustomObjectsApi customObjectsApi;
    AppsV1Api appsV1Api;
    CoreV1Api coreV1Api;
    SharedInformerFactory sharedInformerFactory;

    String space;
    String instanceIdentity;
}
