package com.electrit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
public class JsonlStreamer implements WebFluxConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonlStreamer.class);

    @Value("${basePath}")
    private String basePath;

    public static void main(String[] args) {
        SpringApplication.run(JsonlStreamer.class, args);
    }

    @Bean
    RouterFunction<?> router() {
        LOGGER.info("streaming files from basePath={}", basePath);
        return RouterFunctions.route()
                .GET("/{contentType:sse|ndjson}/{file}", this::openStream)
                .build();
    }

    Mono<ServerResponse> openStream(ServerRequest request) {
        String contentType = request.pathVariable("contentType");
        String file = request.pathVariable("file");
        long offset = request.queryParam("offset").map(Long::parseLong).filter(l -> l > 0).orElse(0L);
        int rate = request.queryParam("rate").map(Integer::parseInt).filter(i -> i > 0).orElse(1);
        LOGGER.info("open {} stream for file={} at offset={} and rate={}", contentType, file, offset, rate);

        try {
            var fis = new FileInputStream(basePath + "/" + file + ".jsonl");
            var bis = new BufferedInputStream(fis, 16 * 1024);
            bis.skipNBytes(offset);
            var publisher = createFlux(bis, offset).delayElements(Duration.ofMillis(1000 / rate));

            return ServerResponse
                    .ok()
                    .contentType("ndjson".equals(contentType) ? MediaType.APPLICATION_NDJSON : MediaType.TEXT_EVENT_STREAM)
                    .body(publisher, String.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Flux<String> createFlux(InputStream inputStream, long offset) {
        var position = new AtomicLong(offset);
        return Flux.generate(() -> inputStream,
                (is, sink) -> {
                    try {
                        var baos = new ByteArrayOutputStream(16 * 1024);
                        int result;
                        for (result = is.read(); result != -1 && result != 0x0a; result = is.read()) {
                            baos.write((byte) result);
                        }

                        if (baos.size() > 0) {
                            var sb = new StringBuilder(baos.toString(StandardCharsets.UTF_8));
                            sb.insert(1, "\"offset\": " + position + ", ");
                            sink.next(sb.toString());

                            position.addAndGet(baos.size() + 1); // +1 is for 0x0a
                        }

                        if (result == -1) {
                            sink.complete();
                        }
                    } catch (IOException e) {
                        sink.error(e);
                    }
                    return is;
                },
                is -> {
                    try {
                        LOGGER.debug("close stream");
                        is.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

}
