package com.example.k8s.controller.customresource.base;

import io.kubernetes.client.common.KubernetesObject;

public interface CustomResource<T> extends KubernetesObject {
    T deepCopy(boolean keepStatus);
}
