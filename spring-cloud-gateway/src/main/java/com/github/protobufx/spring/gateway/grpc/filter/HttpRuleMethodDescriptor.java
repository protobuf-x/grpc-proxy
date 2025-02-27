package com.github.protobufx.spring.gateway.grpc.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.HttpRule;
import com.google.common.base.Strings;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.PathContainer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A descriptor class that handles HTTP rule mapping for gRPC methods.
 * This class provides functionality to:
 * - Extract path patterns from HttpRule annotations
 * - Determine HTTP methods (GET, POST, PUT, DELETE, PATCH)
 * - Handle path variables in URL patterns
 * - Generate default paths when HTTP rules are not specified
 * - Parse and convert between HTTP requests and gRPC messages
 *
 * The class works with Google's HttpRule protobuf annotations to enable
 * RESTful interfaces for gRPC services.
 */
@Slf4j
@AllArgsConstructor
public class HttpRuleMethodDescriptor {

    static final PathPatternParser PATTERN_PARSER = new PathPatternParser();
    static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}]+)\\}");
    Descriptors.MethodDescriptor methodDescriptor;
    @Nullable
    HttpRule httpRule;

    public String getPathPattern() {
        return httpRule == null
                ? getDefaultPath() : getHttpPath();
    }

    public String getHttpPath() {
        if (httpRule == null) {
            return getDefaultPath();
        }
        switch (httpRule.getPatternCase()) {
            case GET:
                return httpRule.getGet();
            case PUT:
                return httpRule.getPut();
            case POST:
                return httpRule.getPost();
            case DELETE:
                return httpRule.getDelete();
            case PATCH:
                return httpRule.getPatch();
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + httpRule.getPatternCase());
        }
    }

    public String getMethod() {
        return httpRule == null
                ? "POST" : httpRule.getPatternCase().toString();
    }

    public boolean containsPathVariable() {
        return getPathPattern().contains("{");
    }

    String getDefaultPath() {
        return methodDescriptor.getService().getFullName() + "/" + methodDescriptor.getName();
    }

    Descriptors.Descriptor getInputType() {
        return methodDescriptor.getInputType();
    }

    Descriptors.Descriptor getOutputType() {
        return methodDescriptor.getOutputType();
    }

    Descriptors.MethodDescriptor getMethodDescriptor() {
        return methodDescriptor;
    }

    String getBodyFiledName() {
        return httpRule == null ? "" : httpRule.getBody();
    }

    MultiValueMap<String, String> getVariables(String requestPath) {
        var httpRuleDefinitionPathPattern = getPathPattern();
        var springToHttpRuleVariables = new HashMap<String, String>();
        var matcher = PATH_VARIABLE.matcher(httpRuleDefinitionPathPattern);
        while (matcher.find()) {
            var variable = matcher.group(1);
            var parts = variable.split("\\.");
            var key = parts[parts.length - 1];
            springToHttpRuleVariables.put(key, variable);
        }
        var pathMatchInfo = PATTERN_PARSER.parse(httpRuleDefinitionPathPattern.replaceAll("\\{[^.}]+\\.", "{"))
                .matchAndExtract(getPathContainer(requestPath));
        if (pathMatchInfo == null) {
            return new LinkedMultiValueMap<>();
        }
        var result = new LinkedMultiValueMap<String, String>();
        for (var entry : pathMatchInfo.getUriVariables().entrySet()) {
            result.add(springToHttpRuleVariables.get(entry.getKey()), entry.getValue());
        }
        return result;
    }

    boolean matches(String method, String path) {
        return getMethod().equals(method)
                && convertHttpRuleDefinitoinToSpringPathPattern().matches(getPathContainer(path));
    }

    private PathContainer getPathContainer(String path) {
        return PathContainer.parsePath(path);
    }

    private PathPattern convertHttpRuleDefinitoinToSpringPathPattern() {
        return PATTERN_PARSER.parse(getPathPattern().replaceAll("\\{[^.}]+\\.", "{"));
    }

    boolean isCustomHttpRule(String method, String path) {
        return !(method.equals("POST") && path.equals(getDefaultPath()));
    }

    MethodDescriptor<DynamicMessage, DynamicMessage> toDynamicMessageMethodDescriptor() {
        return MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(getDefaultPath())
                .setRequestMarshaller(new HttpRuleJsonToGrpcGatewayFilterFactory.DynamicMessageMarshaller(getInputType()))
                .setResponseMarshaller(new HttpRuleJsonToGrpcGatewayFilterFactory.DynamicMessageMarshaller(getOutputType()))
                .build();
    }

    static class DynamicMessageBuilder {
        Descriptors.Descriptor descriptor;
        DynamicMessage.Builder builder;
        JsonFormat.Parser parser;
        ObjectMapper objectMapper;

        DynamicMessageBuilder(Descriptors.Descriptor descriptor, JsonFormat.Parser parser, ObjectMapper objectMapper) {
            this.descriptor = descriptor;
            this.builder = DynamicMessage.newBuilder(descriptor);
            this.parser = parser;
            this.objectMapper = objectMapper;
        }

        DynamicMessage build() {
            return builder.build();
        }

        void setFields(String filedName, DataBuffer dataBuffer) {
            try {
                if (filedName.equals("*")) {
                    parser.merge(new InputStreamReader(dataBuffer.asInputStream(), UTF_8), builder);
                } else {
                    // support only the first layer of the field.
                    var messageFiledName = convertCamelToSnake(filedName);
                    var field = descriptor.findFieldByName(messageFiledName);
                    var fieldBuilder = DynamicMessage.newBuilder(field.getMessageType());
                    var jsonNode = objectMapper.readTree(dataBuffer.asInputStream());
                    parser.merge(jsonNode.get(messageFiledName).toString(), fieldBuilder);
                    builder.setField(field, fieldBuilder.build());
                }
            } catch (Exception e) {
                log.error("Unable to merge from the supplied input stream: {}", e.getMessage(), e);
                var status = com.google.rpc.Status.newBuilder()
                        .setCode(Status.INVALID_ARGUMENT.getCode().value())
                        .setMessage(String.format("Unable to merge from the supplied input stream: %s", e.getMessage()))
                        .build();
                throw StatusProto.toStatusRuntimeException(status);
            }
        }

        void setFields(MultiValueMap<String, String> variables) {
            variables.forEach((key, values) -> {
                if (values.stream().allMatch(Strings::isNullOrEmpty)) {
                    return;
                }
                var fieldNames = key.split("\\.");
                if (fieldNames.length != 0) {
                    var fieldDescriptor = descriptor.findFieldByName(convertCamelToSnake(fieldNames[0]));
                    if (fieldDescriptor != null) {
                        setField(builder, fieldDescriptor, fieldNames, values);
                    }
                }
            });
        }

        private void setField(DynamicMessage.Builder builder, Descriptors.FieldDescriptor fieldDescriptor, String[] fieldNames, List<String> values) {
            if (fieldNames.length == 1) {
                if (fieldDescriptor.isRepeated()) {
                    values.forEach(value -> builder.addRepeatedField(fieldDescriptor, convertValue(fieldDescriptor, value)));
                } else {
                    builder.setField(fieldDescriptor, convertValue(fieldDescriptor, values.get(0)));
                }
            } else {
                var childFieldDescriptor = fieldDescriptor.getMessageType().findFieldByName(fieldNames[1]);
                if (childFieldDescriptor != null) {
                    var childDynamicMessage = builder.getField(fieldDescriptor);
                    if (childDynamicMessage == null) {
                        childDynamicMessage = DynamicMessage.newBuilder(fieldDescriptor.getMessageType()).build();
                    }
                    var childBuilder = ((DynamicMessage) childDynamicMessage).toBuilder();
                    setField(childBuilder, childFieldDescriptor, getTail(fieldNames), values);
                    builder.setField(fieldDescriptor, childBuilder.build());
                }
            }
        }

        @SneakyThrows
        private Object convertValue(Descriptors.FieldDescriptor field, String value) {
            if (field.getType().equals(Descriptors.FieldDescriptor.Type.ENUM)) {
                return field.getEnumType().findValueByName(value);
            } else if (field.getType().equals(Descriptors.FieldDescriptor.Type.MESSAGE)) {
                var message = DynamicMessage.newBuilder(field.getMessageType());
                var json = objectMapper.writeValueAsString(value);
                parser.merge(json, message);
                return message.build();
            } else {
                return value;
            }
        }

        private String convertCamelToSnake(String key) {
            return LOWER_CAMEL.to(LOWER_UNDERSCORE, key);
        }

        private String[] getTail(String[] filedNames) {
            return Arrays.copyOfRange(filedNames, 1, filedNames.length);
        }
    }
}
