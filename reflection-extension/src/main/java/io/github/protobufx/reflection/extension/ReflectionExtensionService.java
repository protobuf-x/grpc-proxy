package io.github.protobufx.reflection.extension;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.github.protobux.reflection.extension.v1alpha.ReflectServerDescriptorRequest;
import io.github.protobux.reflection.extension.v1alpha.ReflectServerDescriptorResponse;
import io.github.protobux.reflection.extension.v1alpha.ServerReflectionExtensionGrpc;
import io.grpc.InternalServer;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.stub.StreamObserver;

import java.util.*;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toList;

public class ReflectionExtensionService extends ServerReflectionExtensionGrpc.ServerReflectionExtensionImplBase {

    @Override
    public void reflectServerDescriptor(ReflectServerDescriptorRequest request, StreamObserver<ReflectServerDescriptorResponse> responseObserver) {
        Server server = InternalServer.SERVER_CONTEXT_KEY.get();
        var index = new FileDescriptorIndex(server.getServices());
        responseObserver.onNext(ReflectServerDescriptorResponse.newBuilder()
                .setFileDescriptorSet(DescriptorProtos.FileDescriptorSet.newBuilder()
                        .addAllFile(index.fileDescriptorsByName.values().stream()
                                .map(Descriptors.FileDescriptor::toProto)
                                .collect(toList()))
                        .build()
                        .toByteString())
                .build());
        responseObserver.onCompleted();
    }

    static class FileDescriptorIndex {
        final Map<String, Descriptors.FileDescriptor> fileDescriptorsByName = new HashMap<>();
        FileDescriptorIndex(List<ServerServiceDefinition> services) {
            Queue<Descriptors.FileDescriptor> fileDescriptorsToProcess = new ArrayDeque<>();
            Set<String> seenFiles = new HashSet<>();
            for (ServerServiceDefinition service : services) {
                io.grpc.ServiceDescriptor serviceDescriptor = service.getServiceDescriptor();
                if (serviceDescriptor.getSchemaDescriptor() instanceof ProtoFileDescriptorSupplier) {
                    Descriptors.FileDescriptor fileDescriptor = ((ProtoFileDescriptorSupplier) serviceDescriptor.getSchemaDescriptor())
                            .getFileDescriptor();
                    if (!seenFiles.contains(fileDescriptor.getName())) {
                        seenFiles.add(fileDescriptor.getName());
                        fileDescriptorsToProcess.add(fileDescriptor);
                    }
                }
            }

            while (!fileDescriptorsToProcess.isEmpty()) {
                Descriptors.FileDescriptor currentFd = fileDescriptorsToProcess.remove();
                processFileDescriptor(currentFd);
                for (Descriptors.FileDescriptor dependencyFd : currentFd.getDependencies()) {
                    if (!seenFiles.contains(dependencyFd.getName())) {
                        seenFiles.add(dependencyFd.getName());
                        fileDescriptorsToProcess.add(dependencyFd);
                    }
                }
            }
        }

        void processFileDescriptor(Descriptors.FileDescriptor fd) {
            String fdName = fd.getName();
            checkState(!fileDescriptorsByName.containsKey(fdName), "File name already used: %s", fdName);
            fileDescriptorsByName.put(fdName, fd);
        }
    }
}
