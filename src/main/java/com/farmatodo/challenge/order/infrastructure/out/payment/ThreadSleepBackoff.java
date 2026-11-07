package com.farmatodo.challenge.order.infrastructure.out.payment;

import java.time.Duration;

import com.farmatodo.challenge.order.domain.OrderPorts.Backoff;

import org.springframework.stereotype.Component;

/**
 * Waits between payment retries. Requests run on virtual threads, so sleeping parks the virtual thread and
 * frees the carrier (OS) thread: thousands of concurrent checkouts can wait without exhausting a thread pool.
 * No transaction or DB connection is held while waiting (see PlaceOrderService).
 */
@Component
class ThreadSleepBackoff implements Backoff {

    @Override
    public void pause(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry the payment", e);
        }
    }
}
