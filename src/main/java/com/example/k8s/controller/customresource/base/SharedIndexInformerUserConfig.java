package com.example.k8s.controller.customresource.base;

import com.example.k8s.controller.config.KubernetesConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class SharedIndexInformerUserConfig {

    private final KubernetesConnection connection;
    private final List<CustomResourceController> controllers;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    public void collectAllSharedInformerFactoryUsers(List<SharedIndexInformerUser> users) { /* we want spring to initialize all the informers first */ }

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
