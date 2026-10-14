package Marketly.Payment.Service.payments;

import java.util.Map;

public interface PaymentInterface {
    public Map<String,String> initializeTransaction(String email,int amount,String currency,String callbackUrl);
}
