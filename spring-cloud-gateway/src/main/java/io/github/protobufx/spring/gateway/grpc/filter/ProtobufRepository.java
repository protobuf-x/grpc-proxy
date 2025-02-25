package io.github.protobufx.spring.gateway.grpc.filter;

public interface ProtobufRepository {
    HttpRuleMethodDescriptor findMethod(String targetUri, String method, String path);
}
