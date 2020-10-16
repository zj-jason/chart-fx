import akka.*;
import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import akka.http.javadsl.*;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.sse.*;
import akka.stream.*;
import akka.stream.alpakka.sse.javadsl.*;
import akka.stream.javadsl.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Test the Alpakka SSE Source with the LSA REST SSE interface.
 */
public class AkkaTest {

    static class Trim extends AbstractBehavior<Trim.TheTrim> {
        private final ActorRef<Trim.TheTrim> greeter;
        String name = "";

        static class TheTrim {
            public final String name;

            public TheTrim (String name) {
                this.name = name;
            }

        }

        public static Behavior<TheTrim> create() {
            return Behaviors.setup(Trim::new);
        }

        @Override
        public Receive<TheTrim> createReceive() {
            return null;
        }

        public Trim (ActorContext<Trim.TheTrim> context) {
            super(context);
            greeter = context.spawn(Trim.create(), "LSA-TrimActor");
        }
    }

    public static void main(String[] args) {
        ActorSystem<Trim.TheTrim> system = ActorSystem.create(Trim.create(), "LSA-Trims");
        Materializer materializer = Materializer.createMaterializer(system.classicSystem());
        final Http http = Http.get(system.classicSystem());
        java.util.function.Function<HttpRequest, CompletionStage<HttpResponse>> send = (request) -> http.singleRequest(request);
        final Uri targetUri = Uri.create("https://restpro00a.acc.gsi.de/lsa/client/v2/resident_patterns_change");
        Source<ServerSentEvent, NotUsed> eventSource = EventSource.create(targetUri, send, Optional.empty(), materializer);

        int elements = 1;
        Duration per = Duration.ofMillis(500);
        int maximumBurst = 1;

        eventSource.throttle(elements, per, maximumBurst, ThrottleMode.shaping())
                .take(5)
                .runForeach(e -> System.out.println(e), materializer);
    }
}
