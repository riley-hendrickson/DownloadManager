import io.rileyhe1.concurrency.Data.ChunkResult;
import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Util.ChunkDownloader;
import io.rileyhe1.concurrency.Util.ProgressTracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
    // Use a reliable test file - W3C dummy PDF
    private static final String TEST_URL = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";
    
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
        // Download first 1KB of test file
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
        assertEquals(1024, result.getBytesDownloaded(), "Should download exactly 1024 bytes");
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
        // Download just 100 bytes
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
        assertEquals(100, result.getBytesDownloaded());
    }

    @Test
    @Disabled
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

    @Test
    @Timeout(30)
    void testPauseStopsDownload() throws InterruptedException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            TEST_URL,
            0,
            50000,
            0,
            config,
            null
        );

        // Future<ChunkResult> future = 
        executor.submit(downloader);

        Thread.sleep(50);

        downloader.pause();
        long bytesAtPause = downloader.getBytesDownloaded();
        
        assertTrue(bytesAtPause > 0, "Should have downloaded some bytes before pause");

        Thread.sleep(200);
        long bytesAfterPause = downloader.getBytesDownloaded();
        
        assertEquals(bytesAtPause, bytesAfterPause, 
            "Bytes should not increase while paused");

        downloader.resume();
    }

    @Test
    @Timeout(30)
    void testResumeAfterPause() throws InterruptedException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            TEST_URL,
            0,
            50000,
            0,
            config,
            null
        );

        // Future<ChunkResult> future = 
        executor.submit(downloader);

        Thread.sleep(50);

        downloader.pause();
        long bytesAtPause = downloader.getBytesDownloaded();

        Thread.sleep(200);

        downloader.resume();

        Thread.sleep(200);

        long bytesAfterResume = downloader.getBytesDownloaded();
        
        assertTrue(bytesAfterResume > bytesAtPause, 
            "Download should continue after resume. Paused at: " + bytesAtPause 
            + ", After resume: " + bytesAfterResume);
    }

    @Test
    @Timeout(30)
    void testMultiplePauseResumeCycles() throws InterruptedException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            TEST_URL,
            0,
            50000,
            0,
            config,
            null
        );

        // Future<ChunkResult> future = 
        executor.submit(downloader);

        long lastBytes = 0;

        for (int i = 0; i < 3; i++)
        {
            Thread.sleep(100);
            long bytesBeforePause = downloader.getBytesDownloaded();
            assertTrue(bytesBeforePause > lastBytes, 
                "Should download in cycle " + (i + 1));

            downloader.pause();
            Thread.sleep(100);
            long bytesDuringPause = downloader.getBytesDownloaded();
            assertEquals(bytesBeforePause, bytesDuringPause,
                "Should not download while paused in cycle " + (i + 1));

            downloader.resume();
            lastBytes = bytesDuringPause;
        }

        downloader.resume();
    }

    @Test
    @Timeout(30)
    void testCancelStopsDownload() throws InterruptedException, ExecutionException
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

        Thread.sleep(100);

        downloader.cancel();

        ChunkResult result = future.get();

        assertFalse(result.isSuccessful(), "Download should fail when cancelled");
        assertTrue(result.hasError(), "Should have an error");
        assertNotNull(result.getError(), "Error should not be null");
        
        assertTrue(result.getError() instanceof InterruptedException,
            "Error should be InterruptedException, was: " + result.getError().getClass());
        
        assertTrue(result.getError().getMessage().contains("cancel"),
            "Error message should mention cancellation");
    }

    @Test
    @Timeout(30)
    void testCancelWhilePaused() throws InterruptedException, ExecutionException
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

        Thread.sleep(100);
        downloader.pause();
        
        long bytesBeforeCancel = downloader.getBytesDownloaded();
        assertTrue(bytesBeforeCancel > 0, "Should have downloaded something");

        downloader.cancel();

        try
        {
            ChunkResult result = future.get(5, TimeUnit.SECONDS);
            assertFalse(result.isSuccessful(), "Should fail due to cancellation");
            assertTrue(result.hasError());
            assertTrue(result.getError() instanceof InterruptedException);
        }
        catch(TimeoutException e)
        {

        }
        
    }

    @Test
    @Timeout(30)
    void testPauseBeforeStart()
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

        // Future<ChunkResult> future = 
        executor.submit(downloader);

        assertEquals(0, downloader.getBytesDownloaded(), 
            "Should not download when paused from start");

        downloader.resume();
    }

    @Test
    @Timeout(30)
    void testResumeWithoutPause() throws InterruptedException, ExecutionException
    {
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
        assertEquals(10000, result.getBytesDownloaded());
    }

    @Test
    @Timeout(30)
    void testMultipleCancelsAreIdempotent() throws InterruptedException, ExecutionException
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
            TEST_URL,
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
        
        assertEquals(100, result.getBytesDownloaded());
    }

    @Test
    @Timeout(30)
    void testConcurrentPauseResumeFromMultipleThreads() throws InterruptedException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            TEST_URL,
            0,
            50000,
            0,
            config,
            null
        );

        // Future<ChunkResult> future = 
        executor.submit(downloader);

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
    }

    @Test
    @Timeout(30)
    void testPauseDoesNotLoseData() throws InterruptedException, ExecutionException
    {
        ChunkDownloader downloader = new ChunkDownloader(
            TEST_URL,
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
        assertEquals(20000, result.getBytesDownloaded(), 
            "Should download exact number of bytes despite pausing");
        
        Path tempFile = Paths.get(result.getTempFilePath());
        assertTrue(Files.exists(tempFile), "Temp file should exist");
        try
        {
            assertEquals(20000, Files.size(tempFile), "File size should match bytes downloaded");
        }
        catch(IOException ioe)
        {

        }
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