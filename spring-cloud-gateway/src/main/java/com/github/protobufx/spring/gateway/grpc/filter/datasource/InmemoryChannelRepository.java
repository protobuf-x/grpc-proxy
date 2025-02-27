package com.github.protobufx.spring.gateway.grpc.filter.datasource;

import com.github.protobufx.spring.gateway.grpc.filter.ChannelRepository;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InmemoryChannelRepository implements ChannelRepository {
    Map<String, Channel> channels = new ConcurrentHashMap<>();

    @Override
    public Channel findChannel(String channelTarget) {
        return channels.computeIfAbsent(channelTarget, t -> ManagedChannelBuilder.forTarget(t)
                .usePlaintext()
                .build());
    }
}