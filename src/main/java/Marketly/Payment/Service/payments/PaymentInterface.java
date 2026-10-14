package Marketly.Payment.Service.payments;

import java.time.Instant;
import java.util.Map;

public interface PaymentInterface {
    public InitResult initializeTransaction(InitRequest initRequest);
    public VerifyTransaction verify(String reference);
    //note to self:the callback url is what we give paystack
    public record InitRequest(String reference,String email,long amount,String currency,String callbackUrl){}
    public record InitResult(String reference,String callbackUrl,String accessCode){}
    public record VerifyTransaction(String reference, String status, long amount, String currency, String channel,
                                    Instant paidAt){
        public boolean succeded(){
            return "success".equals(status);
        }
    }
    record RefundResult(String providerRefundId, String status, long amount) {}


}
