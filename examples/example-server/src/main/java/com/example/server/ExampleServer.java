package com.example.server;

import com.example.echo.v1.*;
import io.github.protobufx.reflection.extension.ReflectionExtensionService;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExampleServer {
    public static void main(String[] args) {
        SpringApplication.run(ExampleServer.class, args);
    }

    @GRpcService
    static class ReflectionExtensionServiceX extends ReflectionExtensionService {
    }

    @GRpcService
    public static class EchoGrpc extends EchoServiceGrpc.EchoServiceImplBase {
        @Override
        public void createEcho(CreateEchoRequest request, StreamObserver<Echo> responseObserver) {
            responseObserver.onNext(Echo.newBuilder()
                    .setMessage("Hello " + request.getMessage())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void createSound(CreateSoundRequest request, StreamObserver<Sound> responseObserver) {
            var sound = request.getSound();
            responseObserver.onNext(Sound.newBuilder()
                    .setSoundId(sound.getSoundId())
                    .addAllWaves(sound.getWavesList())
                    .setType(sound.getType())
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void updateSound(UpdateSoundRequest request, StreamObserver<Sound> responseObserver) {
            super.updateSound(request, responseObserver);
        }

        @Override
        public void getSound(GetSoundRequest request, StreamObserver<Sound> responseObserver) {
            super.getSound(request, responseObserver);
        }

        @Override
        public void listSound(ListSoundRequest request, StreamObserver<ListSoundResponse> responseObserver) {
            super.listSound(request, responseObserver);
        }

        @Override
        public void getWave(GetWaveRequest request, StreamObserver<Wave> responseObserver) {
            super.getWave(request, responseObserver);
        }

        @Override
        public void getSoundCustomType(GetSoundCustomTypeRequest request, StreamObserver<SoundCustomType> responseObserver) {
            responseObserver.onNext(SoundCustomType.newBuilder()
                    .setSoundType(request.getSoundType())
                    .setFieldMask(request.getFieldMask())
                    .setCreateTime(request.getCreateTime())
                    .setPlayTime(request.getPlayTime())
                    .setReleaseDate(request.getReleaseDate())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
