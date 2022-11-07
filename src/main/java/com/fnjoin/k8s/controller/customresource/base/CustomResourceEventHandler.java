package com.fnjoin.k8s.controller.customresource.base;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class CustomResourceEventHandler<O extends CustomResource<O>, L extends KubernetesListObject> implements ResourceEventHandler<O> {
    
    private final WorkQueue workQueue;

    @Override
    public void onAdd(O resource) {
        log.debug("Got resource event: Event=UPDATED, Type={}, Namespace={}, Name={}",
                resource.getClass().getSimpleName(),
                resource.getMetadata().getNamespace(),
                resource.getMetadata().getName());
        workQueue.add(new Request(resource.getMetadata().getNamespace(), resource.getMetadata().getName()));
    }

    @Override
    public void onUpdate(O oldResource, O newResource) {
        log.debug("Got resource event: Event=UPDATED, Type={}, Namespace={}, Name={}",
                newResource.getClass().getSimpleName(),
                newResource.getMetadata().getNamespace(),
                newResource.getMetadata().getName());
        workQueue.add(new Request(oldResource.getMetadata().getNamespace(), oldResource.getMetadata().getName()));
    }

    @Override
    public void onDelete(O resource, boolean deletedFinalStateUnknown) {
        /* ignoring this */
    }
}
