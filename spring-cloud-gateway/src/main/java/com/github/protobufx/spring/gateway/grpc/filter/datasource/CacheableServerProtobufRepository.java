package com.github.protobufx.spring.gateway.grpc.filter.datasource;

import com.google.api.AnnotationsProto;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.github.protobufx.spring.gateway.grpc.filter.ChannelRepository;
import com.github.protobufx.spring.gateway.grpc.filter.FileDescriptorIndex;
import com.github.protobufx.spring.gateway.grpc.filter.HttpRuleMethodDescriptor;
import com.github.protobufx.spring.gateway.grpc.filter.ProtobufRepository;
import com.github.protobufx.reflection.extension.v1alpha.ReflectServerDescriptorRequest;
import com.github.protobufx.reflection.extension.v1alpha.ServerReflectionExtensionGrpc;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CacheableServerProtobufRepository implements ProtobufRepository {

    ChannelRepository channelRepository;
    Cache<String, FileDescriptorIndex> cache;

    public CacheableServerProtobufRepository(ChannelRepository channelRepository, long duration) {
        this.channelRepository = channelRepository;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(duration, TimeUnit.SECONDS)
                .build();
    }

    @SneakyThrows
    @Override
    public Optional<HttpRuleMethodDescriptor> findMethodDescriptor(String serviceId, String method, String path) {
        var index = cache.get(serviceId, () -> {
            var channel = channelRepository.findChannel(serviceId);
            var stub = ServerReflectionExtensionGrpc.newBlockingStub(channel);
            var response = stub.reflectServerDescriptor(ReflectServerDescriptorRequest.getDefaultInstance());
            var output = ByteString.newOutput();
            while (response.hasNext()) {
                var next = response.next();
                try {
                    next.getFileDescriptorSet().writeTo(output);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            var bytes = output.toByteString().toByteArray();
            var extensionRegistry = ExtensionRegistry.newInstance();
            extensionRegistry.add(AnnotationsProto.http);
            try {
                var fileDescriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(bytes, extensionRegistry);
                return new FileDescriptorIndex(fileDescriptorSet);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        });
        return Optional.ofNullable(index.get(method, path));
    }

}
