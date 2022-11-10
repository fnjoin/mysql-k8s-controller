package com.fnjoin.k8s.controller.customresource.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class CustomResourceReconciler<O extends CustomResource<O>, L extends KubernetesListObject> implements Reconciler {

    private final ObjectMapper objectMapper;

    private final GenericKubernetesApi<O, L> genericApi;

    private final CustomResourceController<O, L> controller;

    @Override
    public Result reconcile(Request request) {
        try {
            Optional<O> obj = controller.find(request.getNamespace(), request.getName());
            if (obj.isPresent()) {

                O original = obj.get();
                String uid = original.getMetadata().getUid();

                boolean resourceInDesiredState = controller.isResourceInDesiredState(uid, original);
                boolean statusChangeNeeded = false;

                if (resourceInDesiredState) {
                    statusChangeNeeded = controller.isStatusChangeNeeded(uid, original);
                }
                log.debug("Reconciling: Type={}, Namespace={}, Name={}, ResourceInDesiredState={}, StatusChangeNeeded={}",
                        original.getClass().getSimpleName(),
                        original.getMetadata().getNamespace(),
                        original.getMetadata().getName(),
                        resourceInDesiredState,
                        statusChangeNeeded);

                if (!resourceInDesiredState || statusChangeNeeded) {
                    O changed = controller.applyChanges(uid, original.deepCopy());
                    if (controller.applyStatusChanges(changed)) {
                        String resourceVersion = original.getMetadata().getResourceVersion();
                        updateStatus(resourceVersion, changed);
                        return new Result(true);
                    }
                }
            }
        } catch (ConflictingVersionsException e) {
            return new Result(true);
        } catch (Throwable e) {
            if (e instanceof ApiException apiException) {
                log.warn("Reconciling: Code={}, Response={}", apiException.getCode(), apiException.getResponseBody());
            } else if (e.getCause() != null) {
                log.warn("Reconciling: CauseMessage={}", e.getCause().getMessage(), e.getCause());
            } else {
                log.warn("Reconciling: Message={}", e.getMessage(), e);
            }
            return new Result(true);
        }
        return new Result(false);
    }


    public void updateStatus(String resourceVersion, O resource) throws ApiException {
        resource.getMetadata().setResourceVersion(resourceVersion);

        KubernetesApiResponse<O> response = genericApi.updateStatus(resource, res -> res.getStatus());
        if (!response.isSuccess()) {
            if (response.getHttpStatusCode() == 409) {
                throw new ConflictingVersionsException();
            } else {
                log.error("Error replacing: Code={}, Response={}", response.getHttpStatusCode(), response.getStatus());
                response.throwsApiException();
            }
        } else {
            log.info("Updated: Status={}", writeValueAsString(resource.getStatus()));
        }
    }

    @SneakyThrows
    private String writeValueAsString(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}
