package com.github.protobufx.spring.gateway.grpc.filter;

import io.grpc.Channel;

/**
 * repository interface for managing grpc channels.
 * this interface provides a method to get a grpc channel based on a target string.
 */
public interface ChannelRepository {

    /**
     * Creates a channel with a target string, which can be either a valid
     * {@link io.grpc.NameResolver}-compliant URI, or an authority string.
     *
     * @param channelTarget the target string for the channel.
     * @return the channel for the target string.
     */
    Channel findChannel(String channelTarget);
}
