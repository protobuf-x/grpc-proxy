package com.example.gateway;

import io.github.protobufx.spring.gateway.grpc.filter.*;
import io.github.protobufx.spring.gateway.grpc.filter.datasource.CacheableServerProtobufRepository;
import io.github.protobufx.spring.gateway.grpc.filter.datasource.InmemoryChannelRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ExampleGateway {
    public static void main(String[] args) {
        SpringApplication.run(ExampleGateway.class, args);
    }

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
