package com.xdima;

import com.xdima.grps.customer.CustomerServiceGrpc;
import com.xdima.grps.customer.GetCustomersRequest;
import com.xdima.model.Customer;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CustomerServer {
    private static final int PORT = 50052;

    private io.grpc.Server server;

    public void start ()     throws IOException {
        server = ServerBuilder.forPort(PORT).addService(new CustomerService()).build().start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                stopServer();
                System.err.println("*** server shut down");
            }
        });
    }
    private void stopServer() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    public  void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CustomerServer customerServer = new CustomerServer();
        customerServer.start();
        customerServer.blockUntilShutdown();
    }

    static class CustomerService extends CustomerServiceGrpc.CustomerServiceImplBase {
        private final static List<Customer> customers = new ArrayList<>();

        static {
            for (int i = 1; i< 100; i++){
                customers.add(new Customer(i, "grpsCustomer"+i, String.format("tkachuk%s@gmail.com", i)));
            }
        }

        @Override
        public void getCustomers(GetCustomersRequest request, StreamObserver<com.xdima.grps.customer.Customer> responseObserver) {

            customers.forEach(customer -> responseObserver.onNext(
                    com.xdima.grps.customer.Customer.newBuilder()
                            .setId(customer.getId())
                            .setName(customer.getName())
                            .setEmail(customer.getEmail())
                            .build()));
            responseObserver.onCompleted();
        }
    }

}
