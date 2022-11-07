package com.fnjoin.k8s.controller.config;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileReader;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KubernetesConfig {

    @Bean
    public ApiClient apiClient() throws Exception {

        try {
            // first try to see if we are running inside a k8s cluster
            return ClientBuilder.cluster().build();
        } catch (Exception e) {
            log.warn("Not running inside a cluster, will try other connection methods");
        }

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
