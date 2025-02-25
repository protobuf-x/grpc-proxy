package io.github.protobufx.spring.gateway.grpc.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.*;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.netty.buffer.PooledByteBufAllocator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;


@AllArgsConstructor
@Slf4j
public class HttpRuleJsonToGrpcGatewayFilterFactory extends AbstractGatewayFilterFactory<HttpRuleJsonToGrpcGatewayFilterFactory.Config> {

    ChannelRepository channelRepository;
    ProtobufRepository protobufRepository;
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            var modifiedResponse = new GrpcResponseDecorator(exchange, config);
            ServerWebExchangeUtils.setAlreadyRouted(exchange);
            return modifiedResponse.writeWith(exchange.getRequest().getBody())
                    .then(chain.filter(exchange.mutate().response(modifiedResponse).build()));
        };
    }

    class GrpcResponseDecorator extends ServerHttpResponseDecorator {
        ServerWebExchange exchange;
        Config config;

        GrpcResponseDecorator(ServerWebExchange exchange, Config config) {
            super(exchange.getResponse());
            this.exchange = exchange;
            this.config = config;
        }

        @Override
        @Nonnull
        public Mono<Void> writeWith(@Nonnull Publisher<? extends DataBuffer> body) {
            exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            var routingUri = ((Route) exchange.getAttributes().get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR))
                    .getUri();

            var exchangeRequest = new ExchangeRequest(exchange.getRequest());
            var routingUriAuthority = routingUri.getAuthority();
            var methodDescriptor = protobufRepository.findMethod(routingUriAuthority, exchangeRequest.method(), exchangeRequest.path());
            if (methodDescriptor == null) {
                return Mono.error(getRuntimeException(Status.NOT_FOUND, String.format("Not found for %s: %s", exchangeRequest.method(), exchangeRequest.path())));
            }
            return getDelegate().writeWith(exchangeRequest.body()
                    .filter(dataBuffer -> dataBuffer.capacity() != 0)
                    .<HttpRuleMethodDescriptor.DynamicMessageBuilder>handle((dataBuffer, sink) -> {
                        try {
                            var builder = new HttpRuleMethodDescriptor.DynamicMessageBuilder(methodDescriptor.getInputType(), config.jsonParser, OBJECT_MAPPER);
                            var bodyFiledName = methodDescriptor.getBodyFiledName();
                            builder.setFields(bodyFiledName, dataBuffer);
                            sink.next(builder);
                        } catch (Exception e) {
                            sink.error(getRuntimeException(Status.INVALID_ARGUMENT.withCause(e), "Unable to parse request body"));
                        }
                    })
                    .defaultIfEmpty(new HttpRuleMethodDescriptor.DynamicMessageBuilder(methodDescriptor.getInputType(), config.jsonParser, OBJECT_MAPPER))
                    .flatMap(builder -> Mono.create(sink -> {
                        try {
                            if (methodDescriptor.isCustomHttpRule(exchangeRequest.method(), exchangeRequest.path())) {
                                if (methodDescriptor.containsPathVariable()) {
                                    var variables = methodDescriptor.getVariables(exchangeRequest.path());
                                    builder.setFields(variables);
                                }
                                builder.setFields(exchangeRequest.queryParams());
                            }
                        } catch (Exception e) {
                            sink.error(getRuntimeException(Status.INVALID_ARGUMENT.withCause(e), "Unable to parse request parameters"));
                            return;
                        }

                        try {
                            var metadata = new Metadata();
                            config.getMappingAllowedHeaders().forEach(header -> exchangeRequest.header(header)
                                    .ifPresent(value -> metadata.put(Metadata.Key.of(header, ASCII_STRING_MARSHALLER), value)));
                            var metadataInterceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
                            var channel = ClientInterceptors.intercept(channelRepository.get(routingUriAuthority), metadataInterceptor);
                            var call = channel.newCall(methodDescriptor.toDynamicMessageMethodDescriptor(), CallOptions.DEFAULT);
                            ClientCalls.asyncUnaryCall(call, builder.build(), new StreamObserver<>() {
                                @Override
                                public void onNext(DynamicMessage value) {
                                    try {
                                        sink.success(new NettyDataBufferFactory(new PooledByteBufAllocator())
                                                .wrap(config.jsonPrinter.print(value).getBytes()));
                                    } catch (InvalidProtocolBufferException e) {
                                        sink.error(getRuntimeException(Status.INTERNAL.withCause(e), "Unable to serialize response"));
                                    }
                                }

                                @Override
                                public void onError(Throwable t) {
                                    sink.error(getRuntimeException(Status.INTERNAL.withCause(t), "Unable to process request"));
                                }

                                @Override
                                public void onCompleted() {
                                    sink.success();
                                }
                            });
                        } catch (Exception e) {
                            sink.error(getRuntimeException(Status.INTERNAL.withCause(e), "Unable to process request"));
                        }
                    }))
                    .cast(DataBuffer.class)
                    .last());
        }

        private StatusRuntimeException getRuntimeException(Status status, String message) {
            return status.withDescription(message).asRuntimeException();
        }
    }

    static class DynamicMessageMarshaller implements MethodDescriptor.Marshaller<DynamicMessage> {
        private final Descriptors.Descriptor messageDescriptor;

        public DynamicMessageMarshaller(Descriptors.Descriptor messageDescriptor) {
            this.messageDescriptor = messageDescriptor;
        }

        @Override
        public DynamicMessage parse(InputStream inputStream) {
            try {
                return DynamicMessage.newBuilder(messageDescriptor)
                        .mergeFrom(inputStream, ExtensionRegistryLite.getEmptyRegistry())
                        .build();
            } catch (IOException e) {
                throw new RuntimeException("Unable to merge from the supplied input stream", e);
            }
        }

        @Override
        public InputStream stream(DynamicMessage abstractMessage) {
            return abstractMessage.toByteString().newInput();
        }
    }

    @Data
    public static class Config {
        List<String> mappingAllowedHeaders;
        JsonFormat.Parser jsonParser;
        JsonFormat.Printer jsonPrinter;

        public Config() {
            mappingAllowedHeaders = List.of();
            jsonParser = JsonFormat.parser().ignoringUnknownFields();
            jsonPrinter = JsonFormat.printer().includingDefaultValueFields();
        }
    }

    @Value
    private static class ExchangeRequest {
        ServerHttpRequest request;

        ExchangeRequest(ServerHttpRequest request) {
            this.request = request;
        }

        String method() {
            return request.getMethodValue();
        }

        String path() {
            return request.getURI().getPath();
        }

        Flux<DataBuffer> body() {
            return request.getBody();
        }

        public MultiValueMap<String, String> queryParams() {
            return request.getQueryParams();
        }

        public Optional<String> header(String header) {
            var headers = request.getHeaders().get(header);
            return (headers == null || headers.isEmpty())
                    ? Optional.empty() : Optional.of(headers.get(0));
        }
    }
}



