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

## Requirements

- Java 11+
- Spring Boot 2.2.13
- Spring Cloud Hoxton.SR12
- gRPC 1.37.0

## License

This project is licensed under the [MIT License](LICENSE)
