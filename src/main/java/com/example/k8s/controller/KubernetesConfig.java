package com.example.k8s.controller;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.File;
import java.io.FileReader;

@Slf4j
@Configuration
public class KubernetesConfig {

    @Bean
    @Profile("!embedded")
    public ApiClient remoteApiClient() throws Exception {

        File configFile = null;
        String kubeConfigVar = System.getenv("KUBECONFIG");
        if (StringUtils.isNotBlank(kubeConfigVar)) {
            configFile = new File(kubeConfigVar);
            log.info("Using KUBECONFIG variable: Value='{}'", kubeConfigVar);
        } else {
            File configDir = new File(System.getProperty("user.home"), ".kube");
            configFile = new File(configDir, "config");
            log.info("Using home file: Path='{}'", configFile.getPath());
        }

        KubeConfig config = KubeConfig.loadKubeConfig(new FileReader(configFile));
        return ClientBuilder.kubeconfig(config).build();
    }

    @Bean
    @Profile("embedded")
    public ApiClient embeddedApiClient() throws Exception {
        // loading the in-cluster config, including:
        //   1. service-account CA
        //   2. service-account bearer-token
        //   3. service-account namespace
        //   4. master endpoints(ip, port) from pre-set environment variables
        return ClientBuilder.cluster().build();
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
