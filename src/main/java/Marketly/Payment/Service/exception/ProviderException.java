package Marketly.Payment.Service.exception;

public class ProviderException extends RuntimeException {
    public final boolean retryable;
    public final int httpStatus;

    public ProviderException(String message, boolean retryable, Throwable cause) {
                this(message, retryable, cause, 0);
           }
    public ProviderException(String message, boolean retryable, Throwable cause, int httpStatus) {
        super(message, cause);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

 boolean isRetryable() {
        return retryable;
    }
}
