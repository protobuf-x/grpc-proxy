package com.github.protobufx.spring.gateway.grpc.filter;

import java.util.Optional;

/**
 * Repository interface providing access to Protocol Buffer descriptors and services.
 */
public interface ProtobufRepository {

    Optional<HttpRuleMethodDescriptor> findMethodDescriptor(String serviceId, String method, String path);
}