package com.example.k8s.controller;

import com.example.k8s.controller.customresource.mysql.MysqlCustomResource;
import com.example.k8s.controller.customresource.mysql.MysqlCustomResourceController;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/databases")
public class ApiController {

    private final MysqlCustomResourceController mysqlController;

    @GetMapping
    public List<MysqlBean> listInstances() {
        return mysqlController.list().stream()
                .map(m -> MysqlBean.builder()
                        .name(m.getMetadata().getName())
                        .spec(m.getSpec())
                        .status(m.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    @Data
    @Builder
    public static class MysqlBean {
        String name;
        MysqlCustomResource.Spec spec;
        MysqlCustomResource.Status status;
    }
}
