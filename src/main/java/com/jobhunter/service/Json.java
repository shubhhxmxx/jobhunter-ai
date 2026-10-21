package com.jobhunter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Json {
    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialization failed");
        }
    }

    public <T> T read(String s, TypeReference<T> t) {
        try {
            return mapper.readValue(s, t);
        } catch (Exception e) {
            throw new IllegalStateException("JSON parse failed");
        }
    }

    public List<String> list(String s) {
        return read(s, new TypeReference<List<String>>() {});
    }
}
