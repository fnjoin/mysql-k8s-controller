package com.example.k8s.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.PatchUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class CRDPatcher<O extends CRDObject<O>> {

    private static final String LAST_APPLIED_CONFIGURATION = "fnjoin.com/last-applied-configuration";

    private final KubernetesConnection connection;
    private final ObjectMapper objectMapper;

    private final String group;
    private final String plural;
    private final String version;
    private final Class<O> objectClass;

    @SneakyThrows
    public boolean isDifferentFromLastAppliedConfiguration(O obj) {

        // if there is no last-applied-configuration, then we are different
        if (obj.getMetadata().getAnnotations() == null || !obj.getMetadata().getAnnotations().containsKey(LAST_APPLIED_CONFIGURATION)) {
            return true;
        }

        O newObj = obj.prunedCopy(false);
        String lastAppliedConfigurationJson = newObj.getMetadata().getAnnotations().remove(LAST_APPLIED_CONFIGURATION);

        return !objectMapper
                .readTree(lastAppliedConfigurationJson)
                .equals(objectMapper.valueToTree(newObj));
    }


    @SneakyThrows
    public String createJsonPatchForReconciliation(O oldObj, O newObj) {

        O copyOfNewObj = newObj.prunedCopy(false);
        copyOfNewObj.getMetadata().getAnnotations().remove(LAST_APPLIED_CONFIGURATION);

        String appliedConfiguration = objectMapper.writeValueAsString(copyOfNewObj);
        newObj.getMetadata().getAnnotations().put(LAST_APPLIED_CONFIGURATION, appliedConfiguration);

        return createJsonPatch(oldObj, newObj);
    }

    @SneakyThrows
    public String getJsonPatchForUpdate(O oldObj, O newObj, boolean keepStatus) {

        O prunedOldObj = oldObj.prunedCopy(keepStatus);
        O prunedNewObj = newObj.prunedCopy(keepStatus);

        String lastAppliedConfiguration = prunedOldObj.getMetadata().getAnnotations().get(LAST_APPLIED_CONFIGURATION);
        if (lastAppliedConfiguration != null) {
            prunedNewObj.getMetadata().getAnnotations().put(LAST_APPLIED_CONFIGURATION, lastAppliedConfiguration);
        }

        return createJsonPatch(prunedOldObj, prunedNewObj);
    }

    private String createJsonPatch(O oldObj, O newObj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(JsonDiff.asJson(
                objectMapper.valueToTree(oldObj),
                objectMapper.valueToTree(newObj)));
    }

    @SneakyThrows
    public O executeJsonPatch(String patch, String name) {
        log.debug("Patching object: Type={}, Name={}, Patch={}", objectClass.getSimpleName(), name, patch);
        try {
            return PatchUtils.patch(
                    objectClass,
                    () -> connection.getCustomObjectsApi().patchNamespacedCustomObjectCall(
                            group,
                            version,
                            connection.getSpace(),
                            plural,
                            name,
                            new V1Patch(patch),
                            null,
                            null,
                            null,
                            null
                    ),
                    V1Patch.PATCH_FORMAT_JSON_PATCH,
                    connection.getApiClient());
        } catch (ApiException e) {
            log.error("Error executing patch: Code={}, Response={}", e.getCode(), e.getResponseBody(), e);
            throw e;
        }
    }
}
