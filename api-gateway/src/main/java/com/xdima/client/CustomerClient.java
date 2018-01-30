package com.xdima.client;

import brave.grpc.GrpcTracing;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.xdima.dto.CustomerDTO;
import com.xdima.grps.customer.Customer;
import com.xdima.grps.customer.CustomerServiceGrpc;
import com.xdima.grps.customer.GetCustomersRequest;
import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.reactivex.BackpressureStrategy;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomerClient {
    static private final List<Customer> fallBack = new ArrayList<>(
            Arrays.asList(com.xdima.grps.customer.Customer.newBuilder()
                    .setId(-1)
                    .setName("fallBack")
                    .setEmail("tkachuk.dm@gmail.com")
                    .build())
            );

    private final CustomerServiceGrpc.CustomerServiceStub asyncStub;

    public CustomerClient(String host, int port, GrpcTracing grpcTracing) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .intercept(grpcTracing.newClientInterceptor())
                .usePlaintext(true)
                .build());
    }

    private CustomerClient(ManagedChannel channel) {
        this.asyncStub = CustomerServiceGrpc.newStub(channel);
        ;
    }

/*
    public Flux<CustomerDTO> getCustomers() {
        GetCustomersRequest request = GetCustomersRequest.newBuilder().build();
        GetCustomersResponse customersResponse = new GetCustomersResponse();
        Flux<Customer> flux = Flux.from(customersResponse);
        flux.subscribe();
        asyncStub.getCustomers(request, customersResponse);
//        return flux.map(customerProto -> new CustomerDTO(customerProto.getId(), customerProto.getName(), customerProto.getEmail())) ;
        return flux.flatMap(customerProto -> Mono.defer(() -> Mono.just(new CustomerDTO(customerProto.getId(), customerProto.getName(), customerProto.getEmail()))));
    }
*/

    // async server-streaming implementation
    public Flux<CustomerDTO> getCustomers() {
        final CustomerHystrix customerHystrix = new CustomerHystrix(HystrixCommandGroupKey.Factory.asKey("customer"));
        Observable<Customer> customerObservable = customerHystrix.toObservable();
        io.reactivex.Observable<Customer> source = RxJavaInterop.toV2Observable(customerObservable);
        Flux<Customer> flux = RxJava2Adapter.observableToFlux(source, BackpressureStrategy.BUFFER);
        return flux.flatMap(customerProto -> Mono.defer(() -> Mono.just(new CustomerDTO(customerProto.getId(), customerProto.getName(), customerProto.getEmail()))));
    }

    class GetCustomersResponse implements Publisher<Customer>, StreamObserver<Customer> {

        private Subscriber<? super Customer> subscriber;

        @Override
        public void onNext(Customer customer) {
            subscriber.onNext(customer);
        }

        @Override
        public void onError(Throwable throwable) {
            subscriber.onError(throwable);
        }

        @Override
        public void onCompleted() {
            subscriber.onComplete();
        }

        @Override
        public void subscribe(Subscriber<? super Customer> subscriber) {
            this.subscriber = subscriber;
            this.subscriber.onSubscribe(new BaseSubscriber() {
            });
        }
    }


    class CustomerHystrix extends HystrixObservableCommand<Customer> {

        private CustomerHystrix(HystrixCommandGroupKey group) {
            super(group);
        }

        @Override
        protected Observable<Customer> construct() {
            GetCustomersRequest request = GetCustomersRequest.newBuilder().build();
            GetCustomersResponse customersResponse = new GetCustomersResponse();
            Flux<Customer> flux = Flux.from(customersResponse);
            flux.subscribe();
            asyncStub.getCustomers(request, customersResponse);
            return RxJavaInterop.toV1Observable(flux);
        }

        @Override
        protected Observable<Customer> resumeWithFallback() {
            return Observable.from(fallBack);
        }
    }
}
