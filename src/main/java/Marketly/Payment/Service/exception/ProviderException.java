package Marketly.Payment.Service.exception;

public class ProviderException extends RuntimeException {
    public final boolean retryable;

    public ProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

 boolean isRetryable() {
        return retryable;
    }
}
