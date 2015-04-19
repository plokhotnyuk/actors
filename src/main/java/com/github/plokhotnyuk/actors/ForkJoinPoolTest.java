package com.github.plokhotnyuk.actors;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This test reproduce bug of missed submission in Java FJ pool.
 * It completes successfully on 8u31 or older versions, but hangs up on 8u40 or above
 */
public class ForkJoinPoolTest {
    public static void main(String[] args) {
        ForkJoinPool e = new ForkJoinPool(1);
        AtomicBoolean b = new AtomicBoolean();
        for (int i = 0; i < 100000; i++) {
            b.set(true);
            e.execute(new Runnable() {
                @Override
                public void run() {
                    b.set(false);
                }
            });
            while (b.get());
        }
        e.shutdown();
    }
}
