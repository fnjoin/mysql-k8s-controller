package com.fnjoin.k8s.controller.customresource.base;

import com.fnjoin.k8s.controller.config.KubernetesConnection;
import com.fnjoin.k8s.controller.customresource.base.CustomResourceController;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class CustomResourceControllerConfig {

    private final KubernetesConnection connection;
    private final List<CustomResourceController> controllers;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostConstruct
    public void init() {
        controllers.forEach(c -> executor.submit(c.getController()));
        connection.getSharedInformerFactory().startAllRegisteredInformers();
    }

    @PreDestroy
    public void destroy() {
        connection.getSharedInformerFactory().stopAllRegisteredInformers();
        executor.shutdown();
    }
}
