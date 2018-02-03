package com.xdima;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import com.xdima.grps.owner.GetOwnersRequest;
import com.xdima.grps.owner.OwnerServiceGrpc;
import com.xdima.model.Owner;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OwnerServer {
    private static final String OWNER_SERVER = "owner";
    private static final int PORT = 50051;

    private Server server;

    private void start ()     throws IOException {
        Tracing tracing = ZipkinUtils.createTracing(ZipkinUtils.createHttpSender(), OWNER_SERVER);
        GrpcTracing grpcTracing = GrpcTracing.create(tracing);
        server = ServerBuilder.forPort(PORT).addService(new OwnerService()).intercept(grpcTracing.newServerInterceptor()).build().start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            stopServer();
            System.err.println("*** server shut down");
        }));
    }
    private void stopServer() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        OwnerServer ownerServer = new OwnerServer();
        ownerServer.start();
        ownerServer.blockUntilShutdown();
    }

    static class  OwnerService extends OwnerServiceGrpc.OwnerServiceImplBase {
        private final static List<Owner> owners = new ArrayList<>();

        static {
            for (int i = 1; i< 100; i++){
                owners.add(new Owner(i, "grpsOwner"+i, "12345"+ i));
            }
        }

        @Override
        public void getOwners(GetOwnersRequest request, StreamObserver<com.xdima.grps.owner.Owner> responseObserver) {
            owners.forEach(owner -> responseObserver.onNext(
                    com.xdima.grps.owner.Owner.newBuilder()
                        .setId(owner.getId())
                        .setName(owner.getName())
                        .setPhone(owner.getPhone())
                    .build()));
            responseObserver.onCompleted();
        }
    }
}
