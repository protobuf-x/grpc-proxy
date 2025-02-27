# grpc-proxy

A gRPC proxy implementation that allows proxying gRPC requests through HTTP/JSON rules using Spring Cloud Gateway.

## Overview

This project provides a proxy solution for gRPC services that:

- Converts gRPC requests/responses to JSON format
- Uses Spring Cloud Gateway for routing and filtering
- Supports dynamic service discovery through gRPC reflection
- Allows configuration via HTTP routing rules

## Project Structure

- `reflection-extension`: gRPC reflection support for service discovery
- `spring-cloud-gateway`: Core proxy implementation using Spring Cloud Gateway
- `examples`:
  - `example-server`: Sample gRPC server implementation
  - `example-spring-cloud-gateway`: Example proxy configuration

## Key Features

- **Protocol Translation**: Seamlessly converts between gRPC and JSON formats
- **HTTP-based Routing**: Define routing rules using familiar HTTP patterns
- **Service Discovery**: Automatic service and method discovery via gRPC reflection

## Getting Started

See the examples directory for sample implementations showing how to:
- Set up a gRPC server
- Configure the proxy using Spring Cloud Gateway
- Define routing rules for JSON/gRPC translation

## Dependencies

### JitPack Repository
Add the JitPack repository to your build file:
```groovy
repositories {
    maven { url "https://jitpack.io" }
}
```

### Reflection Extension
```groovy
implementation 'io.github.protobuf-x.grpc-proxy:reflection-extension:${project.version}'
```

### Spring Cloud Gateway Extension
```groovy
implementation 'io.github.protobuf-x.grpc-proxy:spring-cloud-gateway:${project.version}'
```

### Requirements for Using Reflection Extension
- Your gRPC server must have reflection service enabled
- Server must be accessible from the proxy
- Protocol buffers must be properly configured in your project

### Requirements for Using Spring Cloud Gateway
- Spring Cloud Gateway dependencies must be properly configured
- Your application must be configured as a Spring Boot application

## Configuration

### Enabling gRPC Reflection in Your Server
```java
Server server = ServerBuilder.forPort(9090)
    .addService(new YourServiceImpl())
    .addService(ProtoReflectionService.newInstance()) // Add this line
    .build();
```

### Basic Spring Cloud Gateway Configuration

You can configure the gateway either through Java configuration or YAML configuration.

#### Java Configuration
```java
@SpringBootApplication
public class ExampleGateway {
    @Bean
    RouteLocator routeLocator(RouteLocatorBuilder builder, GatewayFilter httpRuleJsonToGrpcGatewayFilter) {
        return builder.routes()
                .route("json-to-grpc", r -> r
                        .path("/example.echo.v1.EchoService/**")
                        .filters(f -> f.filter(httpRuleJsonToGrpcGatewayFilter))
                        .uri("http://localhost:6565")
                ).build();
    }

    @Bean
    GatewayFilter httpRuleJsonToGrpcGatewayFilter(ChannelRepository channelRepository,
                                                  ProtobufRepository protobufRepository) {
        return new HttpRuleJsonToGrpcGatewayFilterFactory(channelRepository, protobufRepository)
                .apply(new HttpRuleJsonToGrpcGatewayFilterFactory.Config());
    }

    @Bean
    ProtobufRepository protoRepository(ChannelRepository channelRepository) {
        return new CacheableServerProtobufRepository(channelRepository, 60);
    }

    @Bean
    ChannelRepository channelRepository() {
        return new InmemoryChannelRepository();
    }
}
```

For more detailed configuration examples, please refer to the examples directory.

## License

This project is licensed under the MIT License
