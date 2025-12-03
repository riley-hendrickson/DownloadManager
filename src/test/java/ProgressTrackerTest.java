import io.rileyhe1.concurrency.Util.ProgressTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ProgressTracker class.
 * Tests concurrent progress updates, progress calculation, and thread safety.
 */
class ProgressTrackerTest
{
    private ProgressTracker tracker;
    private static final long TOTAL_SIZE = 10000L;

    @BeforeEach
    void setUp()
    {
        tracker = new ProgressTracker();
    }

    // ============================================================
    // BASIC FUNCTIONALITY TESTS
    // ============================================================

    @Test
    void testInitialProgress()
    {
        assertEquals(0, tracker.getTotalProgress(), "Initial progress should be 0");
        assertEquals(0.0, tracker.getProgressPercentage(TOTAL_SIZE), 0.01, 
            "Initial percentage should be 0%");
    }

    @Test
    void testSingleChunkUpdate()
    {
        tracker.updateProgress(0, 100);

        assertEquals(100, tracker.getTotalProgress(), "Should track single update");
        assertEquals(1.0, tracker.getProgressPercentage(TOTAL_SIZE), 0.01, 
            "Should be 1% progress");
    }

    @Test
    void testMultipleUpdatesToSameChunk()
    {
        tracker.updateProgress(0, 100);
        tracker.updateProgress(0, 50);
        tracker.updateProgress(0, 25);

        assertEquals(175, tracker.getTotalProgress(), 
            "Should accumulate multiple updates to same chunk");
        assertEquals(1.75, tracker.getProgressPercentage(TOTAL_SIZE), 0.01);
    }

    @Test
    void testMultipleChunks()
    {
        tracker.updateProgress(0, 100);
        tracker.updateProgress(1, 200);
        tracker.updateProgress(2, 300);

        assertEquals(600, tracker.getTotalProgress(), 
            "Should track progress across multiple chunks");
        assertEquals(6.0, tracker.getProgressPercentage(TOTAL_SIZE), 0.01);
    }

    @Test
    void testProgressPercentageCalculation()
    {
        // Test various percentages
        tracker.updateProgress(0, 2500); // 25%
        assertEquals(25.0, tracker.getProgressPercentage(TOTAL_SIZE), 0.01);

        tracker.updateProgress(1, 2500); // 50% total
        assertEquals(50.0, tracker.getProgressPercentage(TOTAL_SIZE), 0.01);

        tracker.updateProgress(2, 5000); // 100% total
        assertEquals(100.0, tracker.getProgressPercentage(TOTAL_SIZE), 0.01);
    }

    @Test
    void testProgressExceedsTotalSize()
    {
        // Download might exceed expected size in some cases
        tracker.updateProgress(0, 12000); // More than total size

        assertEquals(12000, tracker.getTotalProgress());
        assertEquals(120.0, tracker.getProgressPercentage(TOTAL_SIZE), 0.01, 
            "Percentage can exceed 100% if download exceeds expected size");
    }

    @Test
    void testZeroByteUpdate()
    {
        tracker.updateProgress(0, 0);

        assertEquals(0, tracker.getTotalProgress(), 
            "Zero byte update should not change progress");
    }

    @Test
    void testLargeChunkIndices()
    {
        // Test with large chunk indices
        tracker.updateProgress(100, 500);
        tracker.updateProgress(999, 300);
        tracker.updateProgress(1000, 200);

        assertEquals(1000, tracker.getTotalProgress());
    }

    // ============================================================
    // CONCURRENT ACCESS TESTS
    // ============================================================

    @Test
    void testConcurrentUpdatesSameChunk() throws InterruptedException
    {
        int numThreads = 10;
        int updatesPerThread = 100;
        int bytesPerUpdate = 10;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++)
        {
            executor.submit(() -> {
                try
                {
                    for (int j = 0; j < updatesPerThread; j++)
                    {
                        tracker.updateProgress(0, bytesPerUpdate);
                    }
                }
                finally
                {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long expectedTotal = (long) numThreads * updatesPerThread * bytesPerUpdate;
        assertEquals(expectedTotal, tracker.getTotalProgress(), 
            "Concurrent updates to same chunk should be atomic");
    }

    @Test
    void testConcurrentUpdatesDifferentChunks() throws InterruptedException
    {
        int numThreads = 10;
        int updatesPerThread = 100;
        int bytesPerUpdate = 10;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++)
        {
            final int chunkIndex = i;
            executor.submit(() -> {
                try
                {
                    for (int j = 0; j < updatesPerThread; j++)
                    {
                        tracker.updateProgress(chunkIndex, bytesPerUpdate);
                    }
                }
                finally
                {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long expectedTotal = (long) numThreads * updatesPerThread * bytesPerUpdate;
        assertEquals(expectedTotal, tracker.getTotalProgress(), 
            "Concurrent updates to different chunks should be thread-safe");
    }

    @RepeatedTest(5)
    void testConcurrentMixedOperations() throws InterruptedException
    {
        int numThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        // Mix of updates and reads
        for (int i = 0; i < numThreads; i++)
        {
            final int chunkIndex = i % 4; // Use 4 chunks
            executor.submit(() -> {
                try
                {
                    for (int j = 0; j < 100; j++)
                    {
                        if (j % 3 == 0)
                        {
                            // Read operation
                            tracker.getTotalProgress();
                            tracker.getProgressPercentage(TOTAL_SIZE);
                        }
                        else
                        {
                            // Write operation
                            tracker.updateProgress(chunkIndex, 10);
                        }
                    }
                }
                finally
                {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Verify progress is consistent
        long progress = tracker.getTotalProgress();
        assertTrue(progress > 0, "Should have some progress");
        
        // Percentage should be consistent with total progress
        double percentage = tracker.getProgressPercentage(TOTAL_SIZE);
        assertEquals((double) progress / TOTAL_SIZE * 100, percentage, 0.01);
    }

    @Test
    void testHighVolumeUpdates() throws InterruptedException, ExecutionException
    {
        int numChunks = 100;
        int updatesPerChunk = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numChunks; i++)
        {
            final int chunkIndex = i;
            Future<?> future = executor.submit(() -> {
                for (int j = 0; j < updatesPerChunk; j++)
                {
                    tracker.updateProgress(chunkIndex, 1);
                }
            });
            futures.add(future);
        }

        // Wait for all to complete
        for (Future<?> future : futures)
        {
            future.get();
        }

        executor.shutdown();

        long expectedTotal = (long) numChunks * updatesPerChunk;
        assertEquals(expectedTotal, tracker.getTotalProgress(), 
            "High volume updates should be accurate");
    }

    // ============================================================
    // EDGE CASES AND ERROR HANDLING
    // ============================================================

    @Test
    void testNegativeChunkIndex()
    {
        // Should handle negative indices (even though they shouldn't occur in practice)
        assertDoesNotThrow(() -> tracker.updateProgress(-1, 100));
        
        // Verify it was tracked
        assertEquals(100, tracker.getTotalProgress());
    }

    @Test
    void testVeryLargeByteValue()
    {
        long largeValue = Long.MAX_VALUE / 10;
        
        tracker.updateProgress(0, largeValue);

        assertEquals(largeValue, tracker.getTotalProgress());
    }

    @Test
    void testProgressPercentageWithZeroTotalSize()
    {
        tracker.updateProgress(0, 100);
        
        // Division by zero should be handled gracefully or return infinity
        assertDoesNotThrow(() -> {
            double percentage = tracker.getProgressPercentage(0);
            assertTrue(Double.isInfinite(percentage) || percentage == 0.0);
        });
    }

    @Test
    void testGetTotalProgressMultipleTimes()
    {
        tracker.updateProgress(0, 100);
        tracker.updateProgress(1, 200);

        // Multiple reads should return same value
        long progress1 = tracker.getTotalProgress();
        long progress2 = tracker.getTotalProgress();
        long progress3 = tracker.getTotalProgress();

        assertEquals(300, progress1);
        assertEquals(progress1, progress2);
        assertEquals(progress2, progress3);
    }

    @Test
    void testProgressPercentageWithDifferentTotalSizes()
    {
        tracker.updateProgress(0, 5000);

        // Same progress, different total sizes
        assertEquals(50.0, tracker.getProgressPercentage(10000), 0.01);
        assertEquals(25.0, tracker.getProgressPercentage(20000), 0.01);
        assertEquals(100.0, tracker.getProgressPercentage(5000), 0.01);
    }

    // ============================================================
    // REALISTIC DOWNLOAD SCENARIOS
    // ============================================================

    @Test
    void testRealisticFourChunkDownload()
    {
        // Simulate a 4-chunk download of 10MB file
        long fileSize = 10 * 1024 * 1024L; // 10MB
        ProgressTracker downloadTracker = new ProgressTracker();

        // Chunk 0: 2.5MB
        downloadTracker.updateProgress(0, 2621440);
        // Chunk 1: 2.5MB
        downloadTracker.updateProgress(1, 2621440);
        // Chunk 2: 2.5MB
        downloadTracker.updateProgress(2, 2621440);
        // Chunk 3: 2.5MB
        downloadTracker.updateProgress(3, 2621440);

        assertEquals(10485760, downloadTracker.getTotalProgress());
        assertEquals(100.0, downloadTracker.getProgressPercentage(fileSize), 0.01);
    }

    @Test
    void testProgressiveDownloadUpdates() throws InterruptedException
    {
        // Simulate chunks downloading at different rates
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        long fileSize = 1000000L;
        ProgressTracker downloadTracker = new ProgressTracker();

        // Chunk 0: fast download (500 updates of 500 bytes)
        executor.submit(() -> {
            for (int i = 0; i < 500; i++)
            {
                downloadTracker.updateProgress(0, 500);
                try
                {
                    Thread.sleep(1);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // Chunk 1: medium download (250 updates of 1000 bytes)
        executor.submit(() -> {
            for (int i = 0; i < 250; i++)
            {
                downloadTracker.updateProgress(1, 1000);
                try
                {
                    Thread.sleep(2);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // Chunk 2: slow download (125 updates of 2000 bytes)
        executor.submit(() -> {
            for (int i = 0; i < 125; i++)
            {
                downloadTracker.updateProgress(2, 2000);
                try
                {
                    Thread.sleep(3);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        });

        // Chunk 3: fast download (500 updates of 500 bytes)
        executor.submit(() -> {
            for (int i = 0; i < 500; i++)
            {
                downloadTracker.updateProgress(3, 500);
                try
                {
                    Thread.sleep(1);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        });

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        // Each chunk downloads 250000 bytes = 1000000 total
        assertEquals(1000000, downloadTracker.getTotalProgress());
        assertEquals(100.0, downloadTracker.getProgressPercentage(fileSize), 0.01);
    }

    @Test
    void testPartialDownloadProgress()
    {
        long fileSize = 1000000L;
        ProgressTracker downloadTracker = new ProgressTracker();

        // Simulate partial downloads
        downloadTracker.updateProgress(0, 100000); // 10%
        assertEquals(10.0, downloadTracker.getProgressPercentage(fileSize), 0.01);

        downloadTracker.updateProgress(1, 150000); // 25% total
        assertEquals(25.0, downloadTracker.getProgressPercentage(fileSize), 0.01);

        downloadTracker.updateProgress(2, 200000); // 45% total
        assertEquals(45.0, downloadTracker.getProgressPercentage(fileSize), 0.01);

        downloadTracker.updateProgress(3, 550000); // 100% total
        assertEquals(100.0, downloadTracker.getProgressPercentage(fileSize), 0.01);
    }

    // ============================================================
    // PERFORMANCE TESTS
    // ============================================================

    @Test
    void testPerformanceHighFrequencyUpdates() throws InterruptedException
    {
        int numChunks = 10;
        int updatesPerChunk = 10000;
        ExecutorService executor = Executors.newFixedThreadPool(numChunks);
        
        long startTime = System.currentTimeMillis();

        CountDownLatch latch = new CountDownLatch(numChunks);
        for (int i = 0; i < numChunks; i++)
        {
            final int chunkIndex = i;
            executor.submit(() -> {
                try
                {
                    for (int j = 0; j < updatesPerChunk; j++)
                    {
                        tracker.updateProgress(chunkIndex, 1);
                    }
                }
                finally
                {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 100,000 updates should complete reasonably fast (under 5 seconds)
        assertTrue(duration < 5000, 
            "High frequency updates took too long: " + duration + "ms");
        
        assertEquals(100000, tracker.getTotalProgress());
    }
}

/* ============================================================
   RUNNING TESTS
   ============================================================

Run all ProgressTracker tests:
   mvn test -Dtest=ProgressTrackerTest

Run specific test:
   mvn test -Dtest=ProgressTrackerTest#testConcurrentUpdatesSameChunk

Run with all other tests:
   mvn test

============================================================ */
