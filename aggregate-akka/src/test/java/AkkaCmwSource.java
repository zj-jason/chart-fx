import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.stream.Materializer;
import akka.stream.ThrottleMode;
import akka.stream.alpakka.sse.javadsl.EventSource;
import akka.stream.javadsl.Source;
import de.gsi.microservice.aggregate.akka.CmwLightSource;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class AkkaCmwSource {

    static class Trim extends AbstractBehavior<Trim.TheTrim> {
        private final ActorRef<TheTrim> greeter;
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

        public Trim (ActorContext<TheTrim> context) {
            super(context);
            greeter = context.spawn(Trim.create(), "LSA-TrimActor");
        }
    }

    public static void main(String[] args) {
        ActorSystem<Trim.TheTrim> system = ActorSystem.create(Trim.create(), "LSA-Trims");
        Materializer materializer = Materializer.createMaterializer(system.classicSystem());
        Source<String, NotUsed> eventSource = CmwLightSource.create("GSCD001", "SnoopTriggerEvents", "FAIR.SELECTOR.ALL");

        int elements = 1;
        Duration per = Duration.ofMillis(500);
        int maximumBurst = 1;

        eventSource.throttle(elements, per, maximumBurst, ThrottleMode.shaping())
                .take(5)
                .runForeach(e -> System.out.println(e), materializer);
    }
}
