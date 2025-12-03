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
            TEST_URL,
            0,
            1023,
            0,
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
            TEST_URL,
            0,
            1023,
            0,
            config,
            null
        );

        // Download second chunk (1024-2047)
        ChunkDownloader chunk2 = new ChunkDownloader(
            TEST_URL,
            1024,
            2047,
            1,
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
            "https://this-is-not-a-real-url-12345.com/file.bin",
            0,
            1023,
            0,
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
            TEST_URL,
            999999999,
            999999999 + 1023,
            0,
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
        // Test null URL
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader(null, 0, 1023, 0, config, null);
        });

        // Test empty URL
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader("", 0, 1023, 0, config, null);
        });

        // Test negative start byte
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader(TEST_URL, -1, 1023, 0, config, null);
        });

        // Test end byte less than start byte
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader(TEST_URL, 1000, 500, 0, config, null);
        });

        // Test null config
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader(TEST_URL, 0, 1023, 0, null, null);
        });

        // Test negative chunk index
        assertThrows(IllegalArgumentException.class, () -> {
            new ChunkDownloader(TEST_URL, 0, 1023, -1, config, null);
        });
    }

    @Test
    void testGetBytesDownloaded()
    {
        ChunkDownloader downloader = new ChunkDownloader(
            TEST_URL,
            0,
            511,  // 512 bytes
            0,
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
            TEST_URL,
            0,
            1023,
            5,  // Chunk index 5
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
            TEST_URL,
            0,
            99,
            0,
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
            TEST_URL,
            0,
            1023,
            0,
            config,
            tracker
        );

        ChunkResult result = downloader.call();

        assertTrue(result.isSuccessful());
        
        // Progress tracker should have been updated
        // (Exact verification depends on your ProgressTracker implementation)
        assertTrue(tracker.getTotalProgress() > 0, "Progress should be tracked");
    }

    @Test
    void testRetryOnFailure()
    {
        // Use an unreliable URL to trigger retries
        // This test may be flaky depending on network conditions
        DownloadConfig retryConfig = DownloadConfig.builder()
            .maxRetries(3)
            .retryDelayMS(500)
            .timeoutsInSeconds(2)  // Short timeout to trigger failures
            .tempDirectory(tempDir)
            .build();

        ChunkDownloader downloader = new ChunkDownloader(
            "https://httpstat.us/500",  // Returns 500 error
            0,
            1023,
            0,
            retryConfig,
            null
        );

        ChunkResult result = downloader.call();

        // Should fail after retries
        assertFalse(result.isSuccessful());
        assertTrue(result.hasError());
        
        // Note: This test verifies retry logic exists, 
        // but exact behavior depends on network conditions
    }

    @Test
    void testPauseResumeCancelMethodsExist()
    {
        // Verify pause/resume/cancel methods exist and don't throw errors
        ChunkDownloader downloader = new ChunkDownloader(
            TEST_URL,
            0,
            1023,
            0,
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
     * Polls the condition every 50ms until it's true or timeout is reached.
     * 
     * @param condition The condition to check
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if condition became true, false if timeout
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
                Thread.sleep(5); // Check every 5ms
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
     * Helper method to wait for bytes downloaded to stabilize (stop changing).
     * Useful for verifying a download is truly paused.
     * 
     * @param downloader The chunk downloader to monitor
     * @param stabilityMs How long bytes must remain unchanged (in ms)
     * @param timeoutMs Maximum time to wait
     * @return The stable byte count, or -1 if timeout
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
                // Bytes changed, reset stability timer
                lastBytes = currentBytes;
                lastChangeTime = System.currentTimeMillis();
            }
            else if (System.currentTimeMillis() - lastChangeTime >= stabilityMs)
            {
                // Bytes have been stable for required duration
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
        
        return -1; // Timeout
    }

    @Test
    @Timeout(30)
    void testPauseStopsDownload() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            LARGE_TEST_URL,  // Use larger file
            0,
            80000,  // Download more bytes to ensure it takes time
            0,
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        // Wait for some bytes to be downloaded (up to 5 seconds)
        assertTrue(waitForCondition(() -> downloader.getBytesDownloaded() > 0, 5000),
            "Should start downloading within 5 seconds");

        // Pause the download
        downloader.pause();
        
        // Wait for bytes to stabilize (paused for 300ms)
        long bytesWhenPaused = waitForStableBytes(downloader, 300, 2000);
        assertTrue(bytesWhenPaused > 0, "Should have downloaded some bytes before pause");
        
        // Verify it stays paused
        Thread.sleep(200);
        assertEquals(bytesWhenPaused, downloader.getBytesDownloaded(), 
            "Bytes should not increase while paused");

        // Resume and wait for completion to clean up properly
        downloader.resume();
        future.get();
    }

    @Test
    @Timeout(30)
    void testResumeAfterPause() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            LARGE_TEST_URL,  // Use larger file
            0,
            80000,
            0,
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        // Wait for download to start
        assertTrue(waitForCondition(() -> downloader.getBytesDownloaded() > 0, 5000),
            "Download should start within 5 seconds");

        // Pause
        downloader.pause();
        long bytesAtPause = waitForStableBytes(downloader, 300, 2000);
        assertTrue(bytesAtPause > 0, "Should have bytes when paused");

        // Resume
        downloader.resume();

        // Wait for more bytes to be downloaded
        assertTrue(waitForCondition(() -> downloader.getBytesDownloaded() > bytesAtPause, 3000),
            "Download should continue after resume. Paused at: " + bytesAtPause);
        
        // Wait for completion to clean up properly
        future.get();
    }

    @Test
    @Timeout(30)
    void testCancelStopsDownload() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            LARGE_TEST_URL,
            0,
            50000,
            0,
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
            LARGE_TEST_URL,
            0,
            50000,
            0,
            config,
            null
        );

        Future<ChunkResult> future = executor.submit(downloader);

        // wait for the download to start
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
            TEST_URL,
            0,
            10000,
            0,
            config,
            null
        );

        downloader.pause();

        Future<ChunkResult> future = executor.submit(downloader);

        // Wait a moment to verify it's paused
        Thread.sleep(100);
        assertEquals(0, downloader.getBytesDownloaded(), 
            "Should not download when paused from start");

        // Resume and let it complete
        downloader.resume();
        
        // Wait for completion
        ChunkResult result = future.get();
        assertTrue(result.isSuccessful(), "Should complete after resume");
        assertEquals(10001, result.getBytesDownloaded(), "Should download all bytes after resume");
    }

    @Test
    @Timeout(30)
    void testResumeWithoutPause() throws InterruptedException, ExecutionException
    {
        // Download 10001 bytes (0-10000 inclusive)
        ChunkDownloader downloader = new ChunkDownloader(
            TEST_URL,
            0,
            10000,
            0,
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
            TEST_URL,
            0,
            50000,
            0,
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
            LARGE_TEST_URL,
            0,
            50000,
            0,
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
        // Download 101 bytes (0-100 inclusive)
        ChunkDownloader downloader = new ChunkDownloader(
            TEST_URL,
            0,
            100,
            0,
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
            LARGE_TEST_URL,  // Use larger file
            0,
            80000,
            0,
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

        // Ensure it's resumed and wait for completion
        downloader.resume();
        future.get();
    }

    @Test
    @Timeout(30)
    void testPauseDoesNotLoseData() throws InterruptedException, ExecutionException, IOException
    {
        // Download 20001 bytes (0-20000 inclusive)
        ChunkDownloader downloader = new ChunkDownloader(
            LARGE_TEST_URL,
            0,
            20000,
            0,
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
}

/* ============================================================
   ADDITIONAL NOTES FOR RUNNING TESTS
   ============================================================

1. Add JUnit 5 to your pom.xml if not already present:

<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>

2. Run tests with Maven:
   mvn test

3. Run specific test:
   mvn test -Dtest=ChunkDownloaderTest#testSuccessfulDownload

4. Some tests depend on network connectivity and may be flaky.
   The retry test especially may behave differently depending on
   network conditions.

5. Tests use @TempDir which automatically creates and cleans up
   temporary directories for each test.

============================================================ */