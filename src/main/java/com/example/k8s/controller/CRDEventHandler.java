package com.example.k8s.controller;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class CRDEventHandler<O extends CRDObject<O>, L extends KubernetesListObject> implements ResourceEventHandler<O> {
    
    private final WorkQueue workQueue;
    private final Class objectClass;
    private final CRDController<O, L> controller;

    @Override
    public void onAdd(O obj) {
        log.debug("{} added: Name={}", objectClass.getSimpleName(), obj.getMetadata().getName());
        workQueue.add(new Request(obj.getMetadata().getNamespace(), obj.getMetadata().getName()));
    }

    @Override
    public void onUpdate(O oldObj, O newObj) {
        log.debug("{} updated: Name={}", objectClass.getSimpleName(), oldObj.getMetadata().getName());
        workQueue.add(new Request(oldObj.getMetadata().getNamespace(), oldObj.getMetadata().getName()));
    }

    @Override
    public void onDelete(O obj, boolean deletedFinalStateUnknown) {
        log.debug("{} deleted: Name={}", objectClass.getSimpleName(), obj.getMetadata().getName());
        controller.objectDeleted(obj, deletedFinalStateUnknown);
    }
}
