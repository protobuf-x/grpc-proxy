package com.github.protobufx.spring.gateway.grpc.filter;

import io.grpc.Channel;

public interface ChannelRepository {
    Channel get(String target);
}
