package com.xdima.flux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import com.xdima.ZipkinUtils;
import reactor.core.publisher.Mono;

public class TraceWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceWebFilter.class);
    private static final String HEADER = "X-TRACE_ID";
    private static final String HTTP_COMPONENT = "http";
    private static final String TRACE_REQUEST_ATTR = TraceWebFilter.class.getName()
            + ".TRACE";
    private static final String TRACE_SPAN_WITHOUT_PARENT = TraceWebFilter.class.getName()
            + ".SPAN_WITH_NO_PARENT";

    /**
     * If you register your filter before the {@link TraceWebFilter} then you will not
     * have the tracing context passed for you out of the box. That means that e.g. your
     * logs will not get correlated.
     */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

    static final Propagation.Getter<HttpHeaders, String> GETTER =
            new Propagation.Getter<HttpHeaders, String>() {

                @Override
                public String get(HttpHeaders carrier, String key) {
                    return carrier.getFirst(key);
                }

                @Override
                public String toString() {
                    return "HttpHeaders::getFirst";
                }
            };


    private TraceKeys traceKeys;
    private Tracer tracer;
    private Tracing tracing;
    private HttpTracing httpTracing;
    private HttpServerHandler<ServerHttpRequest, ServerHttpResponse> handler;
    private TraceContext.Extractor<HttpHeaders> extractor;
    private final BeanFactory beanFactory;

    public TraceWebFilter(BeanFactory beanFactory, String serviceName) {
        this.beanFactory = beanFactory;
        tracing = ZipkinUtils.createTracing(ZipkinUtils.createSender(), serviceName);
        httpTracing = HttpTracing.create(tracing);
    }

    @SuppressWarnings("unchecked")
    HttpServerHandler<ServerHttpRequest, ServerHttpResponse> handler() {
        if (this.handler == null) {
            this.handler = HttpServerHandler
                    .create(httpTracing,
                            new TraceWebFilter.HttpAdapter());
        }
        return this.handler;
    }

    private Tracer tracer() {
        if (this.tracer == null) {
            this.tracer = tracing.tracer();
        }
        return this.tracer;
    }

    private TraceKeys traceKeys() {
        if (this.traceKeys == null) {
            this.traceKeys = this.beanFactory.getBean(TraceKeys.class);
        }
        return this.traceKeys;
    }

    private TraceContext.Extractor<HttpHeaders> extractor() {
        if (this.extractor == null) {
            this.extractor = tracing.propagation().extractor(GETTER);
        }
        return this.extractor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String uri = request.getPath().pathWithinApplication().value();
        boolean skip = "0".equals(request.getHeaders().getFirst("X-B3-Sampled"));
        if (log.isDebugEnabled()) {
            log.debug("Received a request to uri [" + uri + "] that should not be sampled [" + skip + "]");
        }
        Span spanFromAttribute = getSpanFromAttribute(exchange);
        String name = HTTP_COMPONENT + ":" + uri;
        final String CONTEXT_ERROR = "sleuth.webfilter.context.error";
        return chain
                .filter(exchange)
                .compose(f -> f.then(Mono.subscriberContext())
                        .onErrorResume(t -> Mono.subscriberContext()
                                .map(c -> c.put(CONTEXT_ERROR, t)))
                        .flatMap(c -> {
                            //reactivate span from context
                            SpanAndScope spanAndScope = c.getOrDefault(SpanAndScope.class, defaultSpanAndScope());
                            Span span = spanAndScope.span;
                            Mono<Void> continuation;
                            Throwable t = null;
                            if (c.hasKey(CONTEXT_ERROR)) {
                                t = c.get(CONTEXT_ERROR);
                                continuation = Mono.error(t);
                            } else {
                                continuation = Mono.empty();
                            }
                            Object attribute = exchange
                                    .getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
                            if (attribute instanceof HandlerMethod) {
                                HandlerMethod handlerMethod = (HandlerMethod) attribute;
                                addClassMethodTag(handlerMethod, span);
                                addClassNameTag(handlerMethod, span);
                            }
                            addResponseTagsForSpanWithoutParent(exchange, response, span);
                            handler().handleSend(response, t, span);
                            spanAndScope.scope.close();
                            return continuation;
                        })
                        .subscriberContext(c -> {
                            Span span;
                            if (c.hasKey(SpanAndScope.class)) {
                                SpanAndScope spanAndScope = c.get(SpanAndScope.class);
                                Span parent = spanAndScope.span;
                                span = tracer()
                                        .nextSpan(TraceContextOrSamplingFlags.create(parent.context()))
                                        .start();
                            } else {
                                try {
                                    if (skip) {
                                        span = unsampledSpan(name);
                                    } else {
                                        if (spanFromAttribute != null) {
                                            span = spanFromAttribute;
                                        } else {
                                            span = handler().handleReceive(extractor(),
                                                    request.getHeaders(), request);
                                        }
                                    }
                                    exchange.getAttributes().put(TRACE_REQUEST_ATTR, span);
                                } catch (Exception e) {
                                    log.error("Exception occurred while trying to parse the request. "
                                            + "Will fallback to manual span setting", e);
                                    if (skip) {
                                        span = unsampledSpan(name);
                                    } else {
                                        span = tracer().nextSpan().name(name).start();
                                        exchange.getAttributes().put(TRACE_SPAN_WITHOUT_PARENT, span);
                                    }
                                }
                            }
                            response.getHeaders().add(HEADER, span.context().traceIdString());
                            return c.put(SpanAndScope.class, new SpanAndScope(span, tracer().withSpanInScope(span)));
                        }));
    }

    private SpanAndScope defaultSpanAndScope() {
        Span defaultSpan = tracer().nextSpan().start();
        return new SpanAndScope(defaultSpan, tracer().withSpanInScope(defaultSpan));
    }

    private void addResponseTagsForSpanWithoutParent(ServerWebExchange exchange,
                                                     ServerHttpResponse response, Span span) {
        if (spanWithoutParent(exchange) && response.getStatusCode() != null
                && span != null) {
            span.tag(traceKeys().getHttp().getStatusCode(),
                    String.valueOf(response.getStatusCode().value()));
        }
    }

    private Span unsampledSpan(String name) {
        return tracer().nextSpan(TraceContextOrSamplingFlags.create(
                SamplingFlags.NOT_SAMPLED)).name(name)
                .kind(Span.Kind.SERVER).start();
    }

    private Span getSpanFromAttribute(ServerWebExchange exchange) {
        return exchange.getAttribute(TRACE_REQUEST_ATTR);
    }

    private boolean spanWithoutParent(ServerWebExchange exchange) {
        return exchange.getAttribute(TRACE_SPAN_WITHOUT_PARENT) != null;
    }

    private void addClassMethodTag(Object handler, Span span) {
        if (handler instanceof HandlerMethod) {
            String methodName = ((HandlerMethod) handler).getMethod().getName();
            span.tag(traceKeys().getMvc().getControllerMethod(), methodName);
            if (log.isDebugEnabled()) {
                log.debug("Adding a method tag with value [" + methodName + "] to a span " + span);
            }
        }
    }

    class SpanAndScope {

        final Span span;
        final Tracer.SpanInScope scope;

        SpanAndScope(Span span, Tracer.SpanInScope scope) {
            this.span = span;
            this.scope = scope;
        }

        SpanAndScope() {
            this.span = null;
            this.scope = null;
        }
    }

    private void addClassNameTag(Object handler, Span span) {
        String className;
        if (handler instanceof HandlerMethod) {
            className = ((HandlerMethod) handler).getBeanType().getSimpleName();
        } else {
            className = handler.getClass().getSimpleName();
        }
        if (log.isDebugEnabled()) {
            log.debug("Adding a class tag with value [" + className + "] to a span " + span);
        }
        span.tag(traceKeys().getMvc().getControllerClass(), className);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    static final class HttpAdapter
            extends brave.http.HttpServerAdapter<ServerHttpRequest, ServerHttpResponse> {

        @Override
        public String method(ServerHttpRequest request) {
            return request.getMethodValue();
        }

        @Override
        public String url(ServerHttpRequest request) {
            return request.getURI().toString();
        }

        @Override
        public String requestHeader(ServerHttpRequest request, String name) {
            Object result = request.getHeaders().getFirst(name);
            return result != null ? result.toString() : null;
        }

        @Override
        public Integer statusCode(ServerHttpResponse response) {
            return response.getStatusCode() != null ?
                    response.getStatusCode().value() : null;
        }
    }
}
