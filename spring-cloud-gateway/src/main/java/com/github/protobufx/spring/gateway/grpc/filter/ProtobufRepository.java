package com.github.protobufx.spring.gateway.grpc.filter;

import java.util.Optional;

/**
 * Repository interface providing access to Protocol Buffer descriptors and services.
 */
public interface ProtobufRepository {

    /**
     * Find the method descriptor for a given service ID, method, and path.
     *
     * @param serviceId The ID of the service
     * @param method The method name
     * @param path The path of the request
     * @return An optional containing the method descriptor if found, or empty if not found
     */
    Optional<HttpRuleMethodDescriptor> findMethodDescriptor(String serviceId, String method, String path);
}