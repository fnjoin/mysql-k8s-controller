package com.example.k8s.controller;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class CRDReconciler<O extends CRDObject<O>, L extends KubernetesListObject> implements Reconciler {

    private final Worker<O> worker;
    private final CRDPatcher<O> patcher;
    private final CRDController<O, L> controller;

    @Override
    public Result reconcile(Request request) {
        try {
            Optional<O> obj = controller.findOriginal(request.getName());
            if (obj.isPresent() && patcher.isDifferentFromLastAppliedConfiguration(obj.get())) {

                // apply the changes here
                O original = obj.get();
                O existing = original.prunedCopy(true);
                O applied = worker.applyChanges(original.getMetadata().getUid(), original.prunedCopy(true));

                // persist the changes
                String jsonPatch = patcher.createJsonPatchForReconciliation(existing, applied);
                patcher.executeJsonPatch(jsonPatch, applied.getMetadata().getName());

                return new Result(true);
            }
        } catch (Exception e) {
            log.warn(e.getCause().getMessage(), e.getCause());
        }
        return new Result(false);
    }

    @FunctionalInterface
    public static interface Worker<O> {
        O applyChanges(String uid, O obj);
    }
}
