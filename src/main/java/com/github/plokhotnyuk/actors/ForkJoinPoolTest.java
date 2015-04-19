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
        final boolean[] bs = {false};
        for (int i = 0; i < 100000; i++) {
            bs[0] = true;
            e.execute(new Runnable() {
                @Override
                public void run() {
                    bs[0] = false;
                }
            });
            do {
                b.get();
            } while (bs[0]);
        }
        e.shutdown();
    }
}
