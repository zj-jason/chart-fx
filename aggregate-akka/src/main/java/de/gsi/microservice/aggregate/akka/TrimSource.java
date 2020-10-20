package de.gsi.microservice.aggregate.akka;

import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.stream.*;
import akka.stream.alpakka.sse.javadsl.EventSource;
import akka.stream.javadsl.Source;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import akka.stream.stage.GraphStageLogicWithLogging;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Subscribe to the LSA REST SSE Endpoint and create Updates for each new/changed/removed Resident Pattern.
 */
public class TrimSource extends GraphStage<SourceShape<String>> {
    public final Outlet<String> out = Outlet.create("TrimSource.out");
    private final SourceShape<String> shape = SourceShape.of(out);

    @Override
    public SourceShape<String> shape() {
        return shape;
    }


    // public static Source<String> create() {

    // }

    public TrimSource () {
        //final Http http = Http.get(system.classicSystem());
        final Uri targetUri = Uri.create("https://restpro00a.acc.gsi.de/lsa/client/v2/resident_patterns_change");

    }

    //ActorSystem<Trim.TheTrim> system = ActorSystem.create(Trim.create(), "LSA-Trims");
    //Materializer materializer = Materializer.createMaterializer(system.classicSystem());
    //java.util.function.Function<HttpRequest, CompletionStage<HttpResponse>> send = (request) -> http.singleRequest(request);
    //Source<ServerSentEvent, NotUsed> eventSource = EventSource.create(targetUri, send, Optional.empty(), materializer);

    //int elements = 1;
    //Duration per = Duration.ofMillis(500);
    //int maximumBurst = 1;

    //    eventSource.throttle(elements, per, maximumBurst, ThrottleMode.shaping())
    //        .take(5)
    //            .runForeach(e -> System.out.println(e), materializer);
    @Override
    public GraphStageLogic createLogic(final Attributes inheritedAttributes) throws Exception {
        return new GraphStageLogicWithLogging(shape()) {

        };
    }
}
