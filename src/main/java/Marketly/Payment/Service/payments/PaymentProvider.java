package Marketly.Payment.Service.payments;

import java.time.Instant;

public interface PaymentProvider {
     InitResult initializeTransaction(InitRequest initRequest);
     VerifyTransaction verify(String reference);
     RefundResult refund(String reference, long amountKobo);

    //note to self:the callback url is what we give paystack
     record InitRequest(String reference,String email,long amount,String currency,String returnUrl){}
     record InitResult(String reference,String callbackUrl,String accessCode){}
     record VerifyTransaction(String reference, String status, long amount, String currency, String channel,
                                    Instant paidAt){
        public boolean succeded(){
            return "success".equals(status);
        }
    }
    record RefundResult(String providerRefundId, String status, long amount) {}


}
