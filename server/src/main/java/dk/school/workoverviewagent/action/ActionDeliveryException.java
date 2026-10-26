package dk.school.workoverviewagent.action;

public class ActionDeliveryException extends RuntimeException {

    private final boolean outcomeUnknown;

    public ActionDeliveryException(String message) {
        this(message, false, null);
    }

    public ActionDeliveryException(String message, Throwable cause) {
        this(message, false, cause);
    }

    public ActionDeliveryException(String message, boolean outcomeUnknown, Throwable cause) {
        super(message, cause);
        this.outcomeUnknown = outcomeUnknown;
    }

    public boolean outcomeUnknown() {
        return outcomeUnknown;
    }
}
