package com.xdima;

import brave.Tracing;
import brave.context.slf4j.MDCCurrentTraceContext;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.kafka11.KafkaSender;

public class ZipkinUtils {
    private static final int ZIPKIN_PORT = 9411;
    private static final String ZIPKIN_SERVER = "zipkin";

    private static final String LOCALHOST = "localhost";
    private static final String ZIPKIN_ADDRESS = "http://%s:%d/api/v2/spans";

    public static final String KAFKA_ADDRESS = "%s:%d";
    public static final String KAFKA_SERVER = "10.136.2.69";
    public static final int KAFKA_PORT = 9092;

    public static Sender createSender() {
//        return OkHttpSender.create(String.format(ZIPKIN_ADDRESS, LOCALHOST, ZIPKIN_PORT));
//        return OkHttpSender.create(String.format(ZIPKIN_ADDRESS, ZIPKIN_SERVER, ZIPKIN_PORT));
        return KafkaSender.create(String.format(ZipkinUtils.KAFKA_ADDRESS, ZipkinUtils.KAFKA_SERVER, ZipkinUtils.KAFKA_PORT)).toBuilder().build();
    }


    public static Tracing createTracing(Sender sender, String serviceName) {
        AsyncReporter<Span> reporter = AsyncReporter.create(sender);
        return Tracing.newBuilder()
                .localServiceName(serviceName)
                .spanReporter(reporter)
                .currentTraceContext(MDCCurrentTraceContext.create())
                .build();
    }
}
