package com.xdima.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brave.grpc.GrpcTracing;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.xdima.dto.OwnerDTO;
import com.xdima.grps.owner.GetOwnersRequest;
import com.xdima.grps.owner.Owner;
import com.xdima.grps.owner.OwnerServiceGrpc;
import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.reactivex.BackpressureStrategy;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.Observable;

public class OwnerClient {
    static private Logger log = LoggerFactory.getLogger(OwnerClient.class);
    static private final List<Owner> fallBack = new ArrayList<>(
            Arrays.asList(com.xdima.grps.owner.Owner.newBuilder()
                    .setId(-1)
                    .setName("fallBack")
                    .setPhone("0505555555")
                    .build())
    );

    private final OwnerServiceGrpc.OwnerServiceStub asyncStub;
    private final OwnerServiceGrpc.OwnerServiceBlockingStub blockingStub;

    public OwnerClient(String host, int port, GrpcTracing grpcTracing) {
        this(ManagedChannelBuilder.forAddress(host, port)
                .intercept(grpcTracing.newClientInterceptor())
                .usePlaintext(true)
                .build());
    }

    private OwnerClient(ManagedChannel channel) {
        this.asyncStub = OwnerServiceGrpc.newStub(channel);
        this.blockingStub = OwnerServiceGrpc.newBlockingStub(channel);
    }

    /*
        // blocking server-streaming implementation
        public Flux<OwnerDTO> getOwners() {
            GetOwnersRequest request = GetOwnersRequest.newBuilder().build();
            Iterator<Owner> response = blockingStub.getOwners(request);
            List<OwnerDTO> ownerDTOS = new ArrayList<>();
            while (response.hasNext()) {
                Owner ownerProto = response.next();
                ownerDTOS.add(new OwnerDTO(ownerProto.getId(), ownerProto.getName(), ownerProto.getPhone()));
            }
            return Flux.fromIterable(ownerDTOS) ;
        }
    */
    // async server-streaming implementation
/*    public Flux<OwnerDTO> getOwners() {
        GetOwnersRequest request = GetOwnersRequest.newBuilder().build();
        GetOwnersResponse ownersResponse = new GetOwnersResponse();
        Flux<Owner> flux = Flux.from(ownersResponse);
        flux.subscribe();
        asyncStub.getOwners(request, ownersResponse);
        return flux.flatMap(ownerProto -> Mono.defer(() -> Mono.just(new OwnerDTO(ownerProto.getId(), ownerProto.getName(), ownerProto.getPhone()))));
    }*/

    public Flux<OwnerDTO> getOwners() {
        log.debug("getOwners");
        OwnerHystrix ownerHystrix = new OwnerHystrix(HystrixCommandGroupKey.Factory.asKey("owner"));
        Observable<Owner> ownerObservable = ownerHystrix.toObservable();
        io.reactivex.Observable<Owner> source = RxJavaInterop.toV2Observable(ownerObservable);
        Flux<Owner> flux = RxJava2Adapter.observableToFlux(source, BackpressureStrategy.BUFFER);
        return flux.flatMap(ownerProto -> Mono.defer(() -> Mono.just(new OwnerDTO(ownerProto.getId(), ownerProto.getName(), ownerProto.getPhone()))));
    }

    class GetOwnersResponse implements Publisher<Owner>, StreamObserver<Owner> {

        private Subscriber<? super Owner> subscriber;

        @Override
        public void onNext(Owner owner) {
            subscriber.onNext(owner);
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
        public void subscribe(Subscriber<? super Owner> subscriber) {
            this.subscriber = subscriber;
            this.subscriber.onSubscribe(new BaseSubscriber() {
            });
        }
    }

    class OwnerHystrix extends HystrixObservableCommand<Owner> {

        private OwnerHystrix(HystrixCommandGroupKey group) {
            super(group);
        }

        @Override
        protected Observable<Owner> construct() {
            GetOwnersRequest request = GetOwnersRequest.newBuilder().build();
            GetOwnersResponse ownersResponse = new GetOwnersResponse();
            Flux<Owner> flux = Flux.from(ownersResponse);
            flux.subscribe();
            asyncStub.getOwners(request, ownersResponse);
            return RxJavaInterop.toV1Observable(flux);
        }

        @Override
        protected Observable<Owner> resumeWithFallback() {
            return Observable.from(fallBack);
        }
    }


}
