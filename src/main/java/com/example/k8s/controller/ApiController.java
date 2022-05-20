package com.example.k8s.controller;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ApiController {

    private final MysqlCRDController mysqlCRDController;

    @GetMapping("/databases")
    public List<MysqlBean> listInstances() {
        return mysqlCRDController.list().stream()
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
        Mysql.Spec spec;
        Mysql.Status status;
    }
}
