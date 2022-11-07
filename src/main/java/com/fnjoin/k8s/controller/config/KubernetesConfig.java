package com.fnjoin.k8s.controller.config;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.ClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KubernetesConfig {

    @Bean
    public ApiClient apiClient() throws Exception {
        return ClientBuilder.defaultClient();
    }

    @Bean
    public KubernetesConnection kubernetesConnection(ApiClient apiClient,
                                                     @Value("${space}") String space,
                                                     @Value("${instance-identity}") String instanceIdentity) {
        return KubernetesConnection.builder()
            .apiClient(apiClient)
            .customObjectsApi(new CustomObjectsApi(apiClient))
            .appsV1Api(new AppsV1Api(apiClient))
            .coreV1Api(new CoreV1Api(apiClient))
            .sharedInformerFactory(new SharedInformerFactory(apiClient))
            .space(space)
            .instanceIdentity(instanceIdentity)
            .build();
    }
}
