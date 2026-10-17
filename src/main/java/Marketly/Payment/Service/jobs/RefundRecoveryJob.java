package Marketly.Payment.Service.jobs;

import Marketly.Payment.Service.refunds.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefundRecoveryJob {
    private static final Logger log = LoggerFactory.getLogger(RefundRecoveryJob.class);
    private final RefundService refunds;

    public RefundRecoveryJob(RefundService refunds) { this.refunds = refunds; }

    @Scheduled(fixedDelay = 5000)
    public void run() {
        try { refunds.recoverStuck(); }
        catch (Exception e) { log.error("Refund recovery loop failed", e); }
    }
}