package Marketly.Payment.Service.controller;

import Marketly.Payment.Service.config.AppProps;
import Marketly.Payment.Service.exception.ApiException;
import Marketly.Payment.Service.payments.Service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {
    private final AppProps props;
    private final AdminService admin;

    public AdminController(AppProps props, AdminService admin) {
        this.props = props;
        this.admin = admin;
    }

    @PostMapping("/admin/reset")
    public ResponseEntity<Void> reset() {
        if (!props.testMode()){ throw new ApiException("Not found", 404, "not_found");}
        admin.reset();
        return ResponseEntity.noContent().build();
    }
}