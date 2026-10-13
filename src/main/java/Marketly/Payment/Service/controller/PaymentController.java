package Marketly.Payment.Service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {
    @GetMapping("/payments/:id")
    public ResponseEntity<Void> getPayment(){
        //return RespinseEntity(OK,PaymentStatus)
        return null;
    }
}
