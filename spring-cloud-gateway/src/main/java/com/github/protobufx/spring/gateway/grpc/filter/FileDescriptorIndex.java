package com.github.protobufx.spring.gateway.grpc.filter;

import com.google.api.AnnotationsProto;
import com.google.api.HttpRule;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;
import org.springframework.web.util.pattern.PathPatternParser;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * A class that indexes and provides access to HTTP rule mappings for gRPC methods.
 */
public class FileDescriptorIndex {
    Map<String, DescriptorProtos.FileDescriptorProto> fileMap;
    Map<HttpRuleDefinition, HttpRuleMethodDescriptor> httpRuleMap;
    Map<HttpRuleDefinition, HttpRuleMethodDescriptor> pathVariableOnlyHttpRuleMap;

    public FileDescriptorIndex(DescriptorProtos.FileDescriptorSet descriptorSet) {
        List<DescriptorProtos.FileDescriptorProto> protos = descriptorSet.getFileList();
        this.fileMap = protos.stream()
                .collect(toMap(DescriptorProtos.FileDescriptorProto::getName, identity()));

        this.httpRuleMap = new HashMap<>();
        for (DescriptorProtos.FileDescriptorProto proto : protos) {
            Descriptors.FileDescriptor fileDescriptor = descriptorFromProto(proto);
            for (Descriptors.ServiceDescriptor serviceDescriptor : fileDescriptor.getServices()) {
                for (Descriptors.MethodDescriptor methodDescriptor : serviceDescriptor.getMethods()) {
                    if (methodDescriptor.getOptions().hasExtension(AnnotationsProto.http)) {
                        HttpRule httpRule = methodDescriptor.getOptions().getExtension(AnnotationsProto.http);
                        indexHttpRuleMap(methodDescriptor, httpRule);
                        for (HttpRule additionalBinding : httpRule.getAdditionalBindingsList()) {
                            indexHttpRuleMap(methodDescriptor, additionalBinding);
                        }
                    } else {
                        // if no http rule is defined, use the default path
                        indexHttpRuleMap(methodDescriptor, null);
                    }
                }
            }
        }

        this.pathVariableOnlyHttpRuleMap = httpRuleMap.entrySet().stream()
                .filter(e -> e.getValue().containsPathVariable())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    void indexHttpRuleMap(Descriptors.MethodDescriptor methodDescriptor, HttpRule httpRule) {
        HttpRuleMethodDescriptor descriptor = new HttpRuleMethodDescriptor(methodDescriptor, httpRule);
        httpRuleMap.put(new HttpRuleDefinition(descriptor.getMethod(), descriptor.getPathPattern()), descriptor);
    }

    @Nullable
    public HttpRuleMethodDescriptor get(String method, String path) {
        HttpRuleMethodDescriptor exactMatch = httpRuleMap.get(new HttpRuleDefinition(method, path));
        if (exactMatch != null) {
            return exactMatch;
        }

        for (HttpRuleMethodDescriptor methodDescriptor : pathVariableOnlyHttpRuleMap.values()) {
            if (methodDescriptor.matches(method, path)) {
                return methodDescriptor;
            }
        }
        return null;
    }

    @SneakyThrows
    Descriptors.FileDescriptor descriptorFromProto(DescriptorProtos.FileDescriptorProto descriptorProto) {
        Descriptors.FileDescriptor[] fileDescriptors = descriptorProto.getDependencyList().stream()
                .map(dependencyName -> {
                    DescriptorProtos.FileDescriptorProto fileDescriptorProto = fileMap.get(dependencyName);
                    if (fileDescriptorProto == null) {
                        throw new IllegalArgumentException("Could not find dependency: " + dependencyName);
                    }
                    return descriptorFromProto(fileDescriptorProto);
                })
                .toArray(Descriptors.FileDescriptor[]::new);
        return Descriptors.FileDescriptor.buildFrom(descriptorProto, fileDescriptors);
    }


    @Value
    @Accessors(fluent = true)
    static class HttpRuleDefinition implements Comparable<HttpRuleDefinition> {
        static final PathPatternParser pathParser = new PathPatternParser();
        String method;
        String path;

        public HttpRuleDefinition(String method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override
        public int compareTo(HttpRuleDefinition o) {
            return -concat().compareTo(o.concat());
        }

        String concat() {
            return method + ": " + path;
        }
    }
}
