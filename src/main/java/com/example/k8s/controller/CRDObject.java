package com.example.k8s.controller;

import io.kubernetes.client.common.KubernetesObject;

public interface CRDObject<T> extends KubernetesObject {
    T prunedCopy(boolean keepStatus);
}
