package Marketly.Payment.Service.exception;


import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;


@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String,String>> handle(ApiException exception){
        return ResponseEntity.status(exception.status()).body(Map.
                of("error",exception.code(),"message",exception.getMessage()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,String>>unReadable(){
        return ResponseEntity.badRequest().body(Map.of("error","bad request","body","malformed JSON"));
    }
}
