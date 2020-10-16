package de.gsi.microservice.aggregate.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;

public class RdaReceiverSample {
    public static void main(String[] args) throws InterruptedException {
        SparkConf conf = new SparkConf().setAppName("Test").setMaster("local[2]");
        JavaStreamingContext ssc = new JavaStreamingContext(conf, new Duration(1000));
        JavaDStream<Object> customReceiverStream = ssc.receiverStream(new RdaLightReceiver("GSCD001", "SnoopTriggerEvents", "FAIR.SELECTOR.ALL"));
        customReceiverStream.map(u -> u).print();
        ssc.start();
        ssc.awaitTermination();
    }
}