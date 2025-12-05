import io.rileyhe1.concurrency.Data.ChunkResult;
import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Util.ChunkDownloader;
import io.rileyhe1.concurrency.Util.ProgressTracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ChunkDownloader class.
 * Tests basic download functionality, retry logic, and error handling.
 */
class ChunkDownloaderTest
{
    // Use a reliable test file - W3C dummy PDF (small, ~13KB)
    private static final String TEST_URL = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";
    
    // Use a larger file for pause/resume/cancel tests (100KB sample image)
    private static final String LARGE_TEST_URL = "https://archive.org/download/Rick_Astley_Never_Gonna_Give_You_Up/Rick_Astley_Never_Gonna_Give_You_Up.mp4";
    
    private DownloadConfig config;
    private String tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDirectory)
    {
        // Set up temp directory for test files
        tempDir = tempDirectory.toString();
        
        // Create a config with faster timeouts for testing
        config = DownloadConfig.builder()
            .numberOfThreads(4)
            .chunkSizeMB(1)
            .timeoutsInSeconds(10)
            .maxRetries(3)
            .retryDelayMS(1000)
            .tempDirectory(tempDir)
            .bufferSize(8192)
            .build();
    }

    @AfterEach
    void tearDown() throws IOException
    {
        // Clean up any test files
        Files.walk(Paths.get(tempDir))
            .filter(Files::isRegularFile)
            .forEach(path -> {
                try
                {
                    Files.deleteIfExists(path);
                }
                catch (IOException e)
                {
                    // Ignore cleanup errors
                }
            });
    }

    @Test
    void testSuccessfulDownload()
    {
        // Download first 1KB of test file (bytes 0-1023 = 1024 bytes)
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-1",
            TEST_URL,
            0,
            1023,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        ChunkResult result = downloader.call();

        assertTrue(result.isSuccessful(), "Download should succeed");
        assertEquals(1024, result.getBytesDownloaded(), "Should download exactly 1024 bytes (0-1023 inclusive)");
        assertNotNull(result.getTempFilePath(), "Temp file path should not be null");
        assertTrue(Files.exists(Paths.get(result.getTempFilePath())), "Temp file should exist");
        assertNull(result.getError(), "Error should be null on success");
    }

    @Test
    void testDownloadMultipleChunks()
    {
        // Download first chunk (0-1023)
        ChunkDownloader chunk1 = new ChunkDownloader(
            "test-download-2",
            TEST_URL,
            0,
            1023,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        // Download second chunk (1024-2047)
        ChunkDownloader chunk2 = new ChunkDownloader(
            "test-download-2",
            TEST_URL,
            1024,
            2047,
            0, // alreadyDownloaded
            1, // chunkIndex
            config,
            null
        );

        ChunkResult result1 = chunk1.call();
        ChunkResult result2 = chunk2.call();

        assertTrue(result1.isSuccessful(), "First chunk should succeed");
        assertTrue(result2.isSuccessful(), "Second chunk should succeed");
        assertEquals(1024, result1.getBytesDownloaded());
        assertEquals(1024, result2.getBytesDownloaded());
        
        // Verify they created different files
        assertNotEquals(result1.getTempFilePath(), result2.getTempFilePath());
    }

    @Test
    void testInvalidURL()
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-3",
            "https://this-is-not-a-real-url-12345.com/file.bin",
            0,
            1023,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        ChunkResult result = downloader.call();

        assertFalse(result.isSuccessful(), "Download should fail with invalid URL");
        assertTrue(result.hasError(), "Should have an error");
        assertNotNull(result.getError(), "Error should not be null");
    }

    @Test
    void testInvalidByteRange()
    {
        // Request bytes beyond file size
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-4",
            TEST_URL,
            999999999,
            999999999 + 1023,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        ChunkResult result = downloader.call();

        assertFalse(result.isSuccessful(), "Download should fail with invalid range");
        assertTrue(result.hasError(), "Should have an error");
    }

    @Test
    void testConstructorValidation()
    {
        // Test null download ID
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader(null, TEST_URL, 0, 1023, 0, 0, config, null);
        });

        // Test empty download ID
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("", TEST_URL, 0, 1023, 0, 0, config, null);
        });

        // Test null URL
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("test-id", null, 0, 1023, 0, 0, config, null);
        });

        // Test empty URL
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("test-id", "", 0, 1023, 0, 0, config, null);
        });

        // Test negative start byte
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("test-id", TEST_URL, -1, 1023, 0, 0, config, null);
        });

        // Test end byte less than start byte
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("test-id", TEST_URL, 1000, 500, 0, 0, config, null);
        });

        // Test null config
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("test-id", TEST_URL, 0, 1023, 0, 0, null, null);
        });

        // Test negative chunk index
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("test-id", TEST_URL, 0, 1023, 0, -1, config, null);
        });

        // Test negative alreadyDownloaded
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("test-id", TEST_URL, 0, 1023, -1, 0, config, null);
        });

        // Test alreadyDownloaded exceeds chunk size
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("test-id", TEST_URL, 0, 1023, 2000, 0, config, null);
        });
    }

    @Test
    void testGetBytesDownloaded()
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-5",
            TEST_URL,
            0,
            511,  // 512 bytes
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        // Before download
        assertEquals(0, downloader.getBytesDownloaded());

        // After download
        ChunkResult result = downloader.call();
        
        if (result.isSuccessful())
        {
            assertEquals(512, downloader.getBytesDownloaded());
            assertEquals(downloader.getBytesDownloaded(), result.getBytesDownloaded());
        }
    }

    @Test
    void testChunkResultProperties()
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-6",
            TEST_URL,
            0,
            1023,
            0, // alreadyDownloaded
            5, // chunkIndex
            config,
            null
        );

        ChunkResult result = downloader.call();

        if (result.isSuccessful())
        {
            assertEquals(5, result.getChunkIndex(), "Chunk index should be preserved");
            assertTrue(result.getTempFilePath().contains("chunk5"), 
                      "Temp file should include chunk index");
        }
    }

    @Test
    void testSmallDownload()
    {
        // Download just 100 bytes (0-99 = 100 bytes)
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-7",
            TEST_URL,
            0,
            99,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        ChunkResult result = downloader.call();

        assertTrue(result.isSuccessful());
        assertEquals(100, result.getBytesDownloaded(), "Bytes 0-99 inclusive = 100 bytes");
    }

    @Test
    void testWithProgressTracker()
    {
        // Create a simple progress tracker for testing
        ProgressTracker tracker = new ProgressTracker();

        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-8",
            TEST_URL,
            0,
            1023,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            tracker
        );

        ChunkResult result = downloader.call();

        assertTrue(result.isSuccessful());
        
        // Progress tracker should have been updated
        assertTrue(tracker.getTotalProgress() > 0, "Progress should be tracked");
    }

    @Test
    void testRetryOnFailure()
    {
        // Use an unreliable URL to trigger retries
        DownloadConfig retryConfig = DownloadConfig.builder()
            .maxRetries(3)
            .retryDelayMS(500)
            .timeoutsInSeconds(2)  // Short timeout to trigger failures
            .tempDirectory(tempDir)
            .build();

        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-9",
            "https://httpstat.us/500",  // Returns 500 error
            0,
            1023,
            0, // alreadyDownloaded
            0, // chunkIndex
            retryConfig,
            null
        );

        ChunkResult result = downloader.call();

        // Should fail after retries
        assertFalse(result.isSuccessful());
        assertTrue(result.hasError());
    }

    @Test
    void testPauseResumeCancelMethodsExist()
    {
        // Verify pause/resume/cancel methods exist and don't throw errors
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-10",
            TEST_URL,
            0,
            1023,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        // These methods should exist and not throw exceptions
        assertDoesNotThrow(() -> downloader.pause());
        assertDoesNotThrow(() -> downloader.resume());
        assertDoesNotThrow(() -> downloader.cancel());
    }

    // ============================================================
    // PAUSE/RESUME/CANCEL FUNCTIONALITY TESTS
    // ============================================================

    private ExecutorService executor;

    @BeforeEach
    void setUpExecutor()
    {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDownExecutor()
    {
        if (executor != null)
        {
            executor.shutdownNow();
        }
    }

    /**
     * Helper method to wait for a condition to become true.
     */
    private boolean waitForCondition(java.util.function.BooleanSupplier condition, long timeoutMs)
    {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs)
        {
            if (condition.getAsBoolean())
            {
                return true;
            }
            try
            {
                Thread.sleep(5);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Helper method to wait for bytes downloaded to stabilize.
     */
    private long waitForStableBytes(ChunkDownloader downloader, long stabilityMs, long timeoutMs)
    {
        long startTime = System.currentTimeMillis();
        long lastBytes = downloader.getBytesDownloaded();
        long lastChangeTime = startTime;
        
        while (System.currentTimeMillis() - startTime < timeoutMs)
        {
            long currentBytes = downloader.getBytesDownloaded();
            
            if (currentBytes != lastBytes)
            {
                lastBytes = currentBytes;
                lastChangeTime = System.currentTimeMillis();
            }
            else if (System.currentTimeMillis() - lastChangeTime >= stabilityMs)
            {
                return currentBytes;
            }
            
            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        
        return -1;
    }

    @Test
    @Timeout(30)
    void testPauseStopsDownload() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-pause-1",
            LARGE_TEST_URL,
            0,
            80000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        assertTrue(waitForCondition(() -> downloader.getBytesDownloaded() > 0, 5000),
            "Should start downloading within 5 seconds");

        downloader.pause();
        
        long bytesWhenPaused = waitForStableBytes(downloader, 300, 2000);
        assertTrue(bytesWhenPaused > 0, "Should have downloaded some bytes before pause");
        
        Thread.sleep(200);
        assertEquals(bytesWhenPaused, downloader.getBytesDownloaded(), 
            "Bytes should not increase while paused");

        downloader.resume();
        future.get();
    }

    @Test
    @Timeout(30)
    void testResumeAfterPause() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-resume-1",
            LARGE_TEST_URL,
            0,
            80000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        assertTrue(waitForCondition(() -> downloader.getBytesDownloaded() > 0, 5000),
            "Download should start within 5 seconds");

        downloader.pause();
        long bytesAtPause = waitForStableBytes(downloader, 300, 2000);
        assertTrue(bytesAtPause > 0, "Should have bytes when paused");

        downloader.resume();

        assertTrue(waitForCondition(() -> downloader.getBytesDownloaded() > bytesAtPause, 3000),
            "Download should continue after resume. Paused at: " + bytesAtPause);
        
        future.get();
    }

    @Test
    @Timeout(30)
    void testCancelStopsDownload() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-cancel-1",
            LARGE_TEST_URL,
            0,
            50000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        Thread.sleep(100);

        downloader.cancel();

        ChunkResult result = future.get();

        assertFalse(result.isSuccessful(), "Download should fail when cancelled");
        assertTrue(result.hasError(), "Should have an error");
        assertNotNull(result.getError(), "Error should not be null");
        
        assertTrue(result.getError() instanceof InterruptedException,
            "Error should be InterruptedException, was: " + result.getError().getClass());
    }

    @Test
    @Timeout(30)
    void testCancelWhilePaused() throws InterruptedException, ExecutionException, TimeoutException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-cancel-2",
            LARGE_TEST_URL,
            0,
            50000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        assertTrue(waitForCondition(() -> downloader.getBytesDownloaded() > 0, 5000),
            "Download should start within 5 seconds");
        downloader.pause();
        
        long bytesBeforeCancel = downloader.getBytesDownloaded();
        assertTrue(bytesBeforeCancel > 0, "Should have downloaded something");

        downloader.cancel();

        ChunkResult result = future.get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccessful(), "Should fail due to cancellation");
        assertTrue(result.hasError());
        assertTrue(result.getError() instanceof InterruptedException);
    }

    @Test
    @Timeout(30)
    void testPauseBeforeStart() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-pause-2",
            TEST_URL,
            0,
            10000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        downloader.pause();

        Future<ChunkResult> future = executor.submit(downloader);

        Thread.sleep(100);
        assertEquals(0, downloader.getBytesDownloaded(), 
            "Should not download when paused from start");

        downloader.resume();
        
        ChunkResult result = future.get();
        assertTrue(result.isSuccessful(), "Should complete after resume");
        assertEquals(10001, result.getBytesDownloaded(), "Should download all bytes after resume");
    }

    @Test
    @Timeout(30)
    void testResumeWithoutPause() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-resume-2",
            TEST_URL,
            0,
            10000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        downloader.resume();

        Future<ChunkResult> future = executor.submit(downloader);

        ChunkResult result = future.get();
        
        assertTrue(result.isSuccessful(), "Should succeed even with spurious resume");
        assertEquals(10001, result.getBytesDownloaded(), "Bytes 0-10000 inclusive = 10001 bytes");
    }

    @Test
    @Timeout(30)
    void testMultipleCancels() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-cancel-3",
            TEST_URL,
            0,
            50000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);
        Thread.sleep(50);

        downloader.cancel();
        downloader.cancel();
        downloader.cancel();

        ChunkResult result = future.get();

        assertFalse(result.isSuccessful());
        assertTrue(result.getError() instanceof InterruptedException);
    }

    @Test
    @Timeout(30)
    void testPauseThenCancel() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-pause-cancel",
            LARGE_TEST_URL,
            0,
            50000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);
        Thread.sleep(50);

        downloader.pause();
        Thread.sleep(100);
        
        downloader.cancel();

        ChunkResult result = future.get();

        assertFalse(result.isSuccessful());
        assertTrue(result.getError() instanceof InterruptedException);
    }

    @Test
    @Timeout(30)
    void testCompletedDownloadIgnoresPause() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-complete-pause",
            TEST_URL,
            0,
            100,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        ChunkResult result = future.get();
        
        assertTrue(result.isSuccessful(), "Small download should complete");

        downloader.pause();
        
        assertEquals(101, result.getBytesDownloaded(), "Bytes 0-100 inclusive = 101 bytes");
    }

    @Test
    @Timeout(30)
    void testConcurrentPauseResumeFromMultipleThreads() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-concurrent",
            LARGE_TEST_URL,
            0,
            80000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        Runnable pauseResume = () -> {
            for (int i = 0; i < 10; i++)
            {
                try
                {
                    if (Math.random() > 0.5)
                    {
                        downloader.pause();
                    }
                    else
                    {
                        downloader.resume();
                    }
                    Thread.sleep(10);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        };

        Thread t1 = new Thread(pauseResume);
        Thread t2 = new Thread(pauseResume);
        
        t1.start();
        t2.start();
        
        t1.join();
        t2.join();

        downloader.resume();
        future.get();
    }

    @Test
    @Timeout(30)
    void testPauseDoesNotLoseData() throws InterruptedException, ExecutionException, IOException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-data-integrity",
            LARGE_TEST_URL,
            0,
            20000,
            0, // alreadyDownloaded
            0, // chunkIndex
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        for (int i = 0; i < 5; i++)
        {
            Thread.sleep(50);
            downloader.pause();
            Thread.sleep(50);
            downloader.resume();
        }

        ChunkResult result = future.get();

        assertTrue(result.isSuccessful(), "Download should succeed");
        assertEquals(20001, result.getBytesDownloaded(), 
            "Should download exact number of bytes despite pausing (0-20000 inclusive = 20001 bytes)");
        
        Path tempFile = Paths.get(result.getTempFilePath());
        assertTrue(Files.exists(tempFile), "Temp file should exist");
        assertEquals(20001, Files.size(tempFile), "File size should match bytes downloaded");
    }

    @Test
    @Timeout(30)
    void testResumeWithPartialDownload() throws InterruptedException, ExecutionException
    {
        // Test downloading with alreadyDownloaded > 0
        // This simulates resuming a partially completed chunk
        ChunkDownloader downloader = new ChunkDownloader(
            "test-download-partial",
            TEST_URL,
            0,
            2000,
            1000, // Already downloaded 1000 bytes
            0, // chunkIndex
            config,
            null
        );

        // Should have 1000 bytes already "downloaded"
        assertEquals(1000, downloader.getBytesDownloaded(), 
            "Should start with alreadyDownloaded bytes");

        Future<ChunkResult> future = executor.submit(downloader);
        ChunkResult result = future.get();

        assertTrue(result.isSuccessful(), "Download should succeed");
        // Should download remaining bytes: 2001 total - 1000 already = 1001 more
        assertEquals(2001, result.getBytesDownloaded(), 
            "Should download all remaining bytes");
    }

    @Test
    void testDifferentDownloadsUseDifferentDirectories()
    {
        ChunkDownloader downloader1 = new ChunkDownloader(
            "download-id-1",
            TEST_URL,
            0,
            1023,
            0,
            0,
            config,
            null
        );

        ChunkDownloader downloader2 = new ChunkDownloader(
            "download-id-2",
            TEST_URL,
            0,
            1023,
            0,
            0,
            config,
            null
        );

        ChunkResult result1 = downloader1.call();
        ChunkResult result2 = downloader2.call();

        assertTrue(result1.isSuccessful());
        assertTrue(result2.isSuccessful());

        // Verify they use different directories
        String path1 = result1.getTempFilePath();
        String path2 = result2.getTempFilePath();
        
        assertTrue(path1.contains("download-id-1"), "Path should contain download ID 1");
        assertTrue(path2.contains("download-id-2"), "Path should contain download ID 2");
        assertNotEquals(path1, path2, "Different downloads should use different paths");
    }
}

/* ============================================================
   RUNNING TESTS
   ============================================================

Run all ChunkDownloader tests:
   mvn test -Dtest=ChunkDownloaderTest

Run specific test:
   mvn test -Dtest=ChunkDownloaderTest#testSuccessfulDownload

Run all tests except slow ones:
   mvn test -Dtest=ChunkDownloaderTest#test*

============================================================ */