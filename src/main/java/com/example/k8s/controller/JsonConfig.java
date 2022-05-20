package com.example.k8s.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Configuration
public class JsonConfig {

    @Bean
    public ObjectMapper mapper(Jackson2ObjectMapperBuilder builder) {

        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(OffsetDateTime offsetDateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                OffsetDateTime utcTime = offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC);
                jsonGenerator.writeString(DateTimeFormatter.ISO_DATE_TIME.format(utcTime));
            }
        });
        simpleModule.addDeserializer(OffsetDateTime.class, new JsonDeserializer<OffsetDateTime>() {
            @Override
            public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
                return OffsetDateTime.parse(p.getText());
            }
        });

        return builder
                .modules(simpleModule)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .featuresToEnable(
                        SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,
                        MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
    }
}
