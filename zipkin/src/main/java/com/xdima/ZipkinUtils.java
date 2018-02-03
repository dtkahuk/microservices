package com.xdima;

import brave.Tracing;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.okhttp3.OkHttpSender;

public class ZipkinUtils {
    private static final int ZIPKIN_PORT = 9411;
    private static final String ZIPKIN_SERVER = "zipkin";
    private static final String LOCALHOST = "localhost";
    private static final String ZIPKIN_ADDRESS = "http://%s:%d/api/v2/spans";

    public static Sender createHttpSender(){
        return OkHttpSender.create(String.format(ZIPKIN_ADDRESS, LOCALHOST, ZIPKIN_PORT));
    }


    public static Tracing createTracing(Sender sender, String serviceName){
        AsyncReporter<Span> reporter = AsyncReporter.create(sender);
        return Tracing.newBuilder()
                .localServiceName(serviceName)
                .spanReporter(reporter)
                .build();
    }
}
