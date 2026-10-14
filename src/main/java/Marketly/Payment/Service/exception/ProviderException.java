package Marketly.Payment.Service.exception;

public class ProviderException extends RuntimeException {
    public ProviderException(String message, boolean retryable, Throwable cause) {
        super(message,cause);
        this.retryable = retryable;
    }

        /** true = network error, timeout, 5xx or 429. false = declined or invalid request: never retry. */
        public boolean isRetryable() { return retryable; }

}
