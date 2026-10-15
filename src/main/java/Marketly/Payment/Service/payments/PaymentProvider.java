package Marketly.Payment.Service.payments;

import java.time.Instant;

public interface PaymentProvider {
     InitResult initializeTransaction(InitRequest initRequest);
     VerifyTransaction verify(String reference);
     RefundResult refund(String reference, long amountKobo);


     record InitRequest(String reference,String email,long amount,String currency,String returnUrl){}
     record InitResult(String reference,String checkoutUrl,String accessCode){}
     record VerifyTransaction(String reference, String status, long amount, String currency, String channel,
                                    Instant paidAt){
        public boolean succeeded(){
            return "success".equals(status);
        }
    }
    record RefundResult(String providerRefundId, String status, long amount) {}


}
