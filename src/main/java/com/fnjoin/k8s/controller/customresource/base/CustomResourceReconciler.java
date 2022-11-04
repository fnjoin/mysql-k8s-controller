package com.fnjoin.k8s.controller.customresource.base;

import com.fnjoin.k8s.controller.config.KubernetesConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class CustomResourceReconciler<O extends CustomResource<O>, L extends KubernetesListObject> implements Reconciler {

    private final KubernetesConnection connection;
    private final ObjectMapper objectMapper;

    private final String group;
    private final String plural;
    private final String version;

    private final CustomResourceController<O, L> controller;

    @Override
    public Result reconcile(Request request) {
        try {
            Optional<O> obj = controller.find(request.getNamespace(), request.getName());
            if (obj.isPresent() && !isInDesiredState(obj.get())) {

                O original = obj.get();
                O existing = original.deepCopy();

                // apply the changes here
                O changed = controller.applyChanges(original.getMetadata().getUid(), original.deepCopy());
                controller.applyStatusChanges(changed);

                replaceExisting(existing.getMetadata().getResourceVersion(), changed);
                return new Result(true);
            }
        } catch (ConflictingVersionsException e) {
            return new Result(true);
        } catch (Exception e) {
            if (e.getCause() != null) {
                log.warn("Reconciling: CauseMessage={}", e.getCause().getMessage(), e.getCause());
            } else {
                log.warn("Reconciling: Message={}", e.getMessage(), e);
            }
            return new Result(true);
        }
        return new Result(false);
    }


    @SneakyThrows
    public void replaceExisting(String resourceVersion, O applied) {
        try {
            applied.getMetadata().setResourceVersion(resourceVersion);
            connection.getCustomObjectsApi().replaceNamespacedCustomObject(group,
                    version,
                    applied.getMetadata().getNamespace(),
                    plural,
                    applied.getMetadata().getName(),
                    applied,
                    null,
                    null);
            log.info("Replacing: Resource={}", objectMapper.writeValueAsString(applied));
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                throw new ConflictingVersionsException();
            } else {
                log.error("Error replacing: Code={}, Response={}", e.getCode(), e.getResponseBody());
                throw e;
            }
        }
    }


    private boolean isInDesiredState(O resource) {

        String uid = resource.getMetadata().getUid();

        boolean resourceInDesiredState = controller.isResourceInDesiredState(uid, resource);
        boolean statusChangeNeeded = false;

        if (resourceInDesiredState) {
            statusChangeNeeded = controller.isStatusChangeNeeded(uid, resource);
        }
        log.debug("Reconciling: Type={}, Name={}, ResourceInDesiredState={}, StatusChangeNeeded={}", resource.getClass().getSimpleName(), resource.getMetadata().getName(), resourceInDesiredState, statusChangeNeeded);

        return resourceInDesiredState && !statusChangeNeeded;
    }
}
