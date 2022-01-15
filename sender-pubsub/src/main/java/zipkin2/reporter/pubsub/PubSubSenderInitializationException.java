package zipkin2.reporter.pubsub;

public class PubSubSenderInitializationException extends RuntimeException {

    public PubSubSenderInitializationException() {
    }

    public PubSubSenderInitializationException(String message) {
        super(message);
    }

    public PubSubSenderInitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public PubSubSenderInitializationException(Throwable cause) {
        super(cause);
    }

}
