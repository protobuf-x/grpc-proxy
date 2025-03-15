package com.github.protobufx.spring.gateway.grpc.filter;

import com.google.api.AnnotationsProto;
import com.google.common.io.ByteStreams;
import com.google.protobuf.*;
import com.google.protobuf.util.JsonFormat;
import io.grpc.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.*;

class HttpRuleJsonToGrpcGatewayFilterFactoryTest {

    static FileDescriptorIndex index;
    static GatewayFilterChain chain = e -> Mono.empty();

    @BeforeAll
    static void beforeAll() throws Exception {
        byte[] protoBinary;
        Resource resource = new DefaultResourceLoader().getResource("classpath:descriptors.pb");
        try (InputStream inputStream = resource.getInputStream()) {
            protoBinary = ByteStreams.toByteArray(inputStream);
        }
        ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
        extensionRegistry.add(AnnotationsProto.http);
        DescriptorProtos.FileDescriptorSet descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(protoBinary, extensionRegistry);
        index = new FileDescriptorIndex(descriptorSet);
    }

    @Test
    @DisplayName("Mapping success - default mapping + metadata")
    void testMappingDefault() {
        MockServerWebExchange exchange = ObjectMother.createRequestExchange(POST, "/example.echo.v1.EchoService/CreateSound",
                "{ \"sound\": { \"soundId\": \"123\", \"type\": \"SONG\", \"waves\": [{\"waveId\": 10}] } }");
        String responseBody = "{\n  \"soundId\": \"123\",\n  \"waves\": [{\n    \"waveId\": \"10\",\n    \"value\": \"\"\n  }],\n  \"type\": \"SONG\"\n}";
        exchange.getRequest().mutate().header("x-user-id", "user-123");
        exchange.getRequest().mutate().header("x-api-key", "my-password");
        MockChannel<DynamicMessage> channel = ObjectMother.createResponseChannel(exchange, responseBody);
        GatewayFilter filter = ObjectMother.createHttpRuleJsonToGrpcFilter(channel);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("example.echo.v1.EchoService/CreateSound", channel.requestMethodName());
        assertEquals("sound {\n  sound_id: \"123\"\n  waves {\n    wave_id: \"10\"\n  }\n  type: SONG\n}\n", channel.requestMessage());
        assertEquals("Metadata(x-api-key=my-password)", channel.requestHeaders());
        assertEquals(responseBody, exchange.getResponse().getBodyAsString().block());
    }

    @Test
    @DisplayName("Mapping success - POST + Http rule")
    void testMappingPostHttpRule() {
        MockServerWebExchange exchange = ObjectMother.createRequestExchange(POST, "/sounds",
                "{ \"sound\": { \"soundId\": \"123\", \"waves\": [{\"waveId\": 10}] } }");
        String responseBody = "{\n  \"soundId\": \"123\",\n  \"waves\": [{\n    \"waveId\": \"10\",\n    \"value\": \"\"\n  }],\n  \"type\": \"SOUND_TYPE_UNSPECIFIED\"\n}";
        MockChannel<DynamicMessage> channel = ObjectMother.createResponseChannel(exchange, responseBody);
        GatewayFilter filter = ObjectMother.createHttpRuleJsonToGrpcFilter(channel);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("example.echo.v1.EchoService/CreateSound", channel.requestMethodName());
        assertEquals("sound {\n  sound_id: \"123\"\n  waves {\n    wave_id: \"10\"\n  }\n}\n", channel.requestMessage());
        assertEquals(responseBody, exchange.getResponse().getBodyAsString().block());
    }

    @Test
    @DisplayName("Mapping success - GET + query parameter + path variable")
    void testMappingGetPathVariableAndParameter() {
        MockServerWebExchange exchange = ObjectMother.createRequestExchange(GET, "/sounds/123?waveIds=10&waveIds=20&type=VOICE");
        String responseBody = "{\n  \"soundId\": \"123\",\n  \"waves\": [{\n    \"waveId\": \"10\",\n    \"value\": \"\"\n  }],\n  \"type\": \"SOUND_TYPE_UNSPECIFIED\"\n}";
        MockChannel<DynamicMessage> channel = ObjectMother.createResponseChannel(exchange, responseBody);
        GatewayFilter filter = ObjectMother.createHttpRuleJsonToGrpcFilter(channel);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("example.echo.v1.EchoService/GetSound", channel.requestMethodName());
        assertEquals("sound_id: \"123\"\nwave_ids: \"10\"\nwave_ids: \"20\"\ntype: VOICE\n", channel.requestMessage());
        assertEquals(responseBody, exchange.getResponse().getBodyAsString().block());
    }

    @Test
    @DisplayName("Mapping success - GET + nest path variable + longest match")
    void testMappingNestPathVariables() {
        MockServerWebExchange exchange = ObjectMother.createRequestExchange(GET, "/sounds/123/waves/456");
        String responseBody = "{\n  \"waveId\": \"10\",\n  \"value\": \"\"\n}";
        MockChannel<DynamicMessage> channel = ObjectMother.createResponseChannel(exchange, responseBody);
        GatewayFilter filter = ObjectMother.createHttpRuleJsonToGrpcFilter(channel);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("example.echo.v1.EchoService/GetWave", channel.requestMethodName());
        assertEquals("sound_id: \"123\"\nwave_id: \"456\"\n", channel.requestMessage());
        assertEquals(responseBody, exchange.getResponse().getBodyAsString().block());
    }

    @Test
    @DisplayName("Mapping success - GET + nest filed body + field path variable")
    void testMappingFiledBodyMapping() {
        MockServerWebExchange exchange = ObjectMother.createRequestExchange(PATCH, "/sounds/123", "{\"sound\": {\"waves\": [{\"wave_id\": \"456\", \"value\": \"v1\"}]}}");
        String responseBody = "{\n  \"soundId\": \"123\",\n  \"waves\": [{\n    \"waveId\": \"10\",\n    \"value\": \"\"\n  }],\n  \"type\": \"SOUND_TYPE_UNSPECIFIED\"\n}";
        MockChannel<DynamicMessage> channel = ObjectMother.createResponseChannel(exchange, responseBody);
        GatewayFilter filter = ObjectMother.createHttpRuleJsonToGrpcFilter(channel);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("example.echo.v1.EchoService/UpdateSound", channel.requestMethodName());
        assertEquals("sound {\n  sound_id: \"123\"\n  waves {\n    wave_id: \"456\"\n    value: \"v1\"\n  }\n}\n", channel.requestMessage());
        assertEquals(responseBody, exchange.getResponse().getBodyAsString().block());
    }

    @Test
    @DisplayName("Mapping success - Custom method")
    void testMappingCustomMethod() {
        MockServerWebExchange exchange = ObjectMother.createRequestExchange(POST, "/sounds/123:play", "{\n  \"soundName\": \"my music\"\n}");
        String responseBody = "{\n  \"message\": \"now playing...\"\n}";
        MockChannel<DynamicMessage> channel = ObjectMother.createResponseChannel(exchange, responseBody);
        GatewayFilter filter = ObjectMother.createHttpRuleJsonToGrpcFilter(channel);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("example.echo.v1.EchoService/PlaySound", channel.requestMethodName());
        assertEquals("sound_id: \"123\"\nsound_name: \"my music\"\n", channel.requestMessage());
        assertEquals(responseBody, exchange.getResponse().getBodyAsString().block());
    }

    @ParameterizedTest(name = "Mapping success - GetSoundCustomType with {0}")
    @CsvSource({
        "body,'/example.echo.v1.EchoService/GetSoundCustomType','{\"soundType\":\"VOICE\",\"fieldMask\":\"f1,f2\",\"createTime\":\"2020-10-05T12:34:56Z\",\"playTime\":\"186s\"}'",
        "query parameter,'/soundCustomTypes?soundType=VOICE&fieldMask=f1,f2&createTime=2020-10-05T12:34:56Z&playTime=186s',",
        "path variable,'/soundCustomTypes/VOICE/2020-10-05T12:34:56Z/186s/f1,f2',"
    })
    void testGetSoundCustomType(String testCase, String path, String body) {
        MockServerWebExchange exchange = body == null
            ? ObjectMother.createRequestExchange(GET, path)
            : ObjectMother.createRequestExchange(POST, path, body);
        String responseBody = "{\n  \"soundType\": \"SOUND_TYPE_UNSPECIFIED\"\n}";
        MockChannel<DynamicMessage> channel = ObjectMother.createResponseChannel(exchange, responseBody);
        GatewayFilter filter = ObjectMother.createHttpRuleJsonToGrpcFilter(channel);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("example.echo.v1.EchoService/GetSoundCustomType", channel.requestMethodName());
        assertEquals("sound_type: VOICE\nfield_mask {\n  paths: \"f1\"\n  paths: \"f2\"\n}\ncreate_time {\n  seconds: 1601901296\n}\nplay_time {\n  seconds: 186\n}\n", channel.requestMessage());
        assertEquals(responseBody, exchange.getResponse().getBodyAsString().block());
    }

    @ParameterizedTest(name = "Mapping fail - {0}")
    @CsvSource({
        "Broken JSON,'/example.echo.v1.EchoService/GetSoundCustomType','{\"soundType\":\"VOICE\",\"fieldMask\":\"f1,f2\",\"createTime\":\"2020-10-05T12:34:56Z\",\"playTime\":\"186s'",
        "Invalid parameter type,'/soundCustomTypes?soundType=VOICE&fieldMask=f1,f2&createTime=invalid&playTime=186s',",
        "Non-existent enum value,'/soundCustomTypes?soundType=INVALID_TYPE&fieldMask=f1,f2&createTime=2020-10-05T12:34:56Z&playTime=186s',",
        "Empty path variable,'/soundCustomTypes/VOICE/2020-10-05T12:34:56Z',",
        "Not found url,'/example.echo.v1.EchoService/CreateEcho',"
    })
    void testMappingErrors(String testCase, String path, String body) {
        MockServerWebExchange exchange = body == null
            ? ObjectMother.createRequestExchange(GET, path)
            : ObjectMother.createRequestExchange(POST, path, body);
        String responseBody = "{}";
        MockChannel<DynamicMessage> channel = ObjectMother.createResponseChannel(exchange, responseBody);
        GatewayFilter filter = ObjectMother.createHttpRuleJsonToGrpcFilter(channel);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(e -> {
                    switch (testCase) {
                        case "Broken JSON":
                        case "Non-existent enum value":
                        case "Invalid parameter type":
                            return e instanceof StatusRuntimeException && ((StatusRuntimeException) e).getStatus().getCode() == Status.Code.INVALID_ARGUMENT;
                        case "Empty path variable":
                        case "Not found url":
                            return e instanceof StatusRuntimeException && ((StatusRuntimeException) e).getStatus().getCode() == Status.Code.NOT_FOUND;
                        default:
                            return false;
                    }
                })
                .verify();
    }

    @Test
    @DisplayName("When gRPC server returns error code, it should be handled with ResponseStatusException")
    void testGrpcErrorHandling() {
        MockServerWebExchange exchange = ObjectMother.createRequestExchange(POST, "/example.echo.v1.EchoService/CreateSound", "{}");
        Channel channel = ObjectMother.createInvalidErrorResponseChannel();
        GatewayFilter filter = ObjectMother.createHttpRuleJsonToGrpcFilter(channel);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(e -> e instanceof StatusRuntimeException && 
                                         ((StatusRuntimeException) e).getStatus().getCode() == Status.Code.INVALID_ARGUMENT)
                .verify();
    }

    static class ObjectMother {
        static MockServerWebExchange createRequestExchange(HttpMethod method, String path, String... body) {
            String host = "http://localhost:8080";
            MockServerHttpRequest.BodyBuilder requestBuilder = MockServerHttpRequest.method(method, host + path);
            MockServerHttpRequest request = body.length > 0 ? requestBuilder.body(body[0]) : requestBuilder.build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            Route route = Route.async()
                    .id("r1")
                    .uri(host)
                    .predicate(e -> true)
                    .build();
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
            return exchange;
        }

        static GatewayFilter createHttpRuleJsonToGrpcFilter(Channel channel) {
            HttpRuleJsonToGrpcGatewayFilterFactory.Config config = new HttpRuleJsonToGrpcGatewayFilterFactory.Config();
            config.setMappingAllowedHeaders(Collections.singletonList("x-api-key"));
            return new HttpRuleJsonToGrpcGatewayFilterFactory(
                    target -> channel,
                    (uri, method, path) -> Optional.ofNullable(index.get(method, path))
            ).apply(config);
        }

        static MockChannel<DynamicMessage> createResponseChannel(MockServerWebExchange exchange, String responseBody) {
            org.springframework.http.server.reactive.ServerHttpRequest request = exchange.getRequest();
            HttpRuleMethodDescriptor methodDescriptor = index.get(request.getMethodValue(), request.getPath().value());
            if (methodDescriptor == null) {
                return new MockChannel<>(DynamicMessage.getDefaultInstance(Empty.getDescriptor()));
            }
            Descriptors.MethodDescriptor descriptor = methodDescriptor.getMethodDescriptor();
            Descriptors.Descriptor responseType = descriptor.getOutputType();
            DynamicMessage.Builder builder = DynamicMessage.newBuilder(responseType);
            try {
                JsonFormat.parser().merge(responseBody, builder);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
            return new MockChannel<>(builder.build());
        }

        public static Channel createInvalidErrorResponseChannel() {
            return new Channel() {
                @Override
                public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(io.grpc.MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
                    return new ClientCall<ReqT, RespT>() {
                        private Listener<RespT> responseListener;

                        @Override
                        public void start(Listener<RespT> responseListener, Metadata headers) {
                            this.responseListener = responseListener;
                        }

                        @Override
                        public void request(int numMessages) {
                            responseListener.onClose(Status.INVALID_ARGUMENT.withDescription("invalid argument request."), new Metadata());
                        }

                        @Override
                        public void cancel(String message, Throwable cause) {
                        }

                        @Override
                        public void halfClose() {
                        }

                        @Override
                        public void sendMessage(ReqT message) {
                        }
                    };
                }

                @Override
                public String authority() {
                    return "";
                }
            };
        }
    }

    static class MockChannel<RES extends Message> extends Channel {
        MethodDescriptor<?, ?> requestMethodDescriptor;
        DynamicMessage request;
        Metadata requestHeaders;
        RES response;

        public MockChannel(RES response) {
            this.response = response;
        }

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            requestMethodDescriptor = methodDescriptor;
            return new ClientCall<RequestT, ResponseT>() {
                ClientCall.Listener<ResponseT> listener;

                @Override
                public void start(ClientCall.Listener<ResponseT> listener, Metadata headers) {
                    this.listener = listener;
                    requestHeaders = headers;
                }

                @Override
                public void request(int i) {

                }

                @Override
                public void cancel(String message, Throwable cause) {
                    listener.onClose(Status.CANCELLED, new Metadata());
                }

                @Override
                public void halfClose() {
                    listener.onClose(Status.OK, new Metadata());
                }

                @SuppressWarnings("unchecked")
                @Override
                public void sendMessage(RequestT message) {
                    request = (DynamicMessage) message;
                    listener.onMessage((ResponseT) DynamicMessage.newBuilder(response).build());
                }
            };
        }

        @Override
        public String authority() {
            return "";
        }

        String requestMessage() {
            return request.toString();
        }

        String requestHeaders() {
            return requestHeaders.toString();
        }

        public String requestMethodName() {
            return requestMethodDescriptor.getFullMethodName();
        }
    }
}