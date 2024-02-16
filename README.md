# Introduction

`jsonl-streamer` is a simple Spring Boot based HTTP server for streaming content of a JSONL file either as
[NDJSON](https://en.wikipedia.org/wiki/JSON_streaming#Newline-delimited_JSON), or as
[SSE](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events) stream.

# Requirements

Java SDK version 17 (or higher) is required to build and run the server.

# Bulding executable jar

```bash
$ ./gradlew clean bootJar
```

After running above command the executable jar file will be located at `build/libs/jsonl-streamer-0.0.1-SNAPSHOT.jar` 

# Running server

The following command starts `jsonl-streamer` on default `8080` port (can be changed using `--server.port=...` option):

```bash
$ java -jar jsonl-streamer-0.0.1-SNAPSHOT.jar --basePath="<path to directory with JSONL files>"
```

* `--basePath` option configures path to directory that contains JSONL files (`.jsonl` extension is assumed)

# Requesting a stream

For HTTP client to get a stream for `<filename>.jsonl` file located in the directory specified  by `--basePath`,
it has to request the following URL for NDJSON stream:

```bash
$ curl "http://localhost:8080/ndjson/<filename>"
```

or, for SSE stream:

```bash
$ curl "http://localhost:8080/sse/<filename>"
```

There are optional query parameters:

* `rate` - sets stream's rate in "frames per second" (by default `rate=1`)

* `offset` - `jsonl-streamer` injects extra `offset` field to the streamed data frames, so when you don't want to stream a file
from the beginning (e.g. when stream has been disconnected, and you need to re-connect and continue from where you stopped),
you can set specific `offset`

Example of streaming `<filename>.jsonl` at 25 frames/s starting at `offset=12345`:

```bash
$ curl "http://localhost:8080/sse/<filename>?rate=25&offset=12345"
```
