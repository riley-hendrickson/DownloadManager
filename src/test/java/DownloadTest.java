import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Data.DownloadException;
import io.rileyhe1.concurrency.Data.DownloadState;
import io.rileyhe1.concurrency.Util.Download;
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
 * Test suite for Download class.
 * Tests download orchestration, state management, pause/resume/cancel functionality.
 */
class DownloadTest
{
    // Test URLs
    private static final String TEST_URL = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";
    private static final String LARGE_TEST_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
    
    private DownloadConfig config;
    private String tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDirectory)
    {
        tempDir = tempDirectory.toString();
        
        config = DownloadConfig.builder()
            .numberOfThreads(4)
            .chunkSizeMB(1)
            .timeoutsInSeconds(30)
            .maxRetries(3)
            .retryDelayMS(1000)
            .tempDirectory(tempDir)
            .bufferSize(8192)
            .minSizeForChunking(1024) // 1KB threshold
            .build();
    }

    @AfterEach
    void tearDown() throws IOException
    {
        // Clean up test files
        Files.walk(Paths.get(tempDir))
            .filter(Files::isRegularFile)
            .forEach(path -> {
                try
                {
                    Files.deleteIfExists(path);
                }
                catch(IOException e)
                {
                    // Ignore cleanup errors
                }
            });
    }

    // ============================================================
    // CONSTRUCTOR TESTS
    // ============================================================

    @Test
    void testConstructorWithValidParameters() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);

        assertNotNull(download.getId(), "Download should have an ID");
        assertEquals(DownloadState.PENDING, download.getState(), "Initial state should be PENDING");
        assertTrue(download.getTotalSize() > 0, "Should have retrieved file size");
        assertNull(download.getError(), "Should have no error initially");
    }

    @Test
    void testConstructorNullURL()
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        assertThrows(IllegalArgumentException.class, () -> {
            new Download(null, destination, config, tracker);
        });
    }

    @Test
    void testConstructorEmptyURL()
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        assertThrows(IllegalArgumentException.class, () -> {
            new Download("", destination, config, tracker);
        });
    }

    @Test
    void testConstructorNullDestination()
    {
        ProgressTracker tracker = new ProgressTracker();

        assertThrows(IllegalArgumentException.class, () -> {
            new Download(TEST_URL, null, config, tracker);
        });
    }

    @Test
    void testConstructorNullConfig()
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        assertThrows(IllegalArgumentException.class, () -> {
            new Download(TEST_URL, destination, null, tracker);
        });
    }

    @Test
    void testConstructorNullProgressTracker()
    {
        String destination = Paths.get(tempDir, "test.pdf").toString();

        assertThrows(IllegalArgumentException.class, () -> {
            new Download(TEST_URL, destination, config, null);
        });
    }

    @Test
    void testConstructorInvalidURL()
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        assertThrows(DownloadException.class, () -> {
            new Download("https://this-is-not-a-real-url-12345.com/file.bin", 
                        destination, config, tracker);
        });
    }

    @Test
    void testConstructorSetsUniqueIds() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination1 = Paths.get(tempDir, "test1.pdf").toString();
        String destination2 = Paths.get(tempDir, "test2.pdf").toString();

        Download download1 = new Download(TEST_URL, destination1, config, tracker);
        Download download2 = new Download(TEST_URL, destination2, config, tracker);

        assertNotEquals(download1.getId(), download2.getId(), 
            "Each download should have a unique ID");
    }

    @Test
    void testConstructorCreatesTempDirectory() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);

        // Temp directory should be created: config.getTempDirectory() + "/" + downloadId
        String expectedTempDir = tempDir + "/" + download.getId();
        assertTrue(Files.exists(Paths.get(expectedTempDir)), 
            "Download-specific temp directory should be created");
    }

    // ============================================================
    // START METHOD TESTS
    // ============================================================

    @Test
    @Timeout(30)
    void testStartAndCompleteDownload() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "dummy.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        
        assertEquals(DownloadState.PENDING, download.getState());
        
        download.start();
        
        assertEquals(DownloadState.DOWNLOADING, download.getState());
        
        download.awaitCompletion();
        
        assertEquals(DownloadState.COMPLETED, download.getState());
        assertTrue(Files.exists(Paths.get(destination)), "Downloaded file should exist");
        assertTrue(Files.size(Paths.get(destination)) > 0, "Downloaded file should have content");
        assertNull(download.getError(), "Should have no error on success");
        
        // Verify temp directory was cleaned up
        String tempDirPath = tempDir + "/" + download.getId();
        assertFalse(Files.exists(Paths.get(tempDirPath)), 
            "Temp directory should be cleaned up after completion");
    }

    @Test
    void testStartTwiceThrowsException() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        download.start();

        assertThrows(IllegalStateException.class, () -> {
            download.start();
        }, "Starting a download twice should throw exception");
    }

    @Test
    @Timeout(30)
    void testProgressTracking() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        
        assertEquals(0.0, download.getProgress(), 0.01, "Initial progress should be 0");
        
        download.start();
        
        // Wait a bit for some progress
        Thread.sleep(500);
        
        double midProgress = download.getProgress();
        assertTrue(midProgress >= 0.0 && midProgress <= 100.0, 
            "Progress should be between 0 and 100");
        
        download.awaitCompletion();
        
        assertEquals(100.0, download.getProgress(), 0.01, 
            "Progress should be 100% when complete");
    }

    // ============================================================
    // PAUSE/RESUME TESTS
    // ============================================================

    @Test
    void testPauseBeforeStartThrowsException() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);

        assertThrows(IllegalStateException.class, () -> {
            download.pause();
        }, "Cannot pause before starting");
    }

    @Test
    void testResumeBeforeStartThrowsException() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);

        assertThrows(IllegalStateException.class, () -> {
            download.resume();
        }, "Cannot resume before starting");
    }

    @Test
    @Timeout(30)
    void testPauseAndResume() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();

        // Wait for download to make progress
        Thread.sleep(100);
        
        download.pause();
        assertEquals(DownloadState.PAUSED, download.getState());
        
        double progressAtPause = download.getProgress();
        assertTrue(progressAtPause > 0, "Should have some progress when paused. Progress was: " + progressAtPause);
        
        // Wait and verify progress doesn't increase while paused
        Thread.sleep(50);
        assertEquals(progressAtPause, download.getProgress(), 0.1, 
            "Progress should not increase while paused");
        
        download.resume();
        assertEquals(DownloadState.DOWNLOADING, download.getState());
        
        // Wait for more progress
        Thread.sleep(100);
        assertTrue(download.getProgress() > progressAtPause, 
            "Progress should increase after resume");
        
        download.cancel(); // Clean up
    }

    @Test
    void testResumeWithoutPauseThrowsException() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        download.start();

        assertThrows(IllegalStateException.class, () -> {
            download.resume();
        }, "Cannot resume when not paused");
        
        download.cancel(); // Clean up
    }

    @Test
    void testPauseWhenAlreadyPausedThrowsException() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();
        Thread.sleep(100);
        
        download.pause();

        assertThrows(IllegalStateException.class, () -> {
            download.pause();
        }, "Cannot pause when already paused");
        
        download.cancel(); // Clean up
    }

    @Test
    @Timeout(30)
    void testMultiplePauseResumeCycles() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();

        // Cycle through pause/resume multiple times
        for(int i = 0; i < 3; i++)
        {
            Thread.sleep(100);
            download.pause();
            assertEquals(DownloadState.PAUSED, download.getState());
            
            Thread.sleep(100);
            download.resume();
            assertEquals(DownloadState.DOWNLOADING, download.getState());
        }

        download.cancel(); // Clean up
    }

    // ============================================================
    // CANCEL TESTS
    // ============================================================

    @Test
    void testCancelBeforeStartThrowsException() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);

        assertThrows(IllegalStateException.class, () -> {
            download.cancel();
        }, "Cannot cancel before starting");
    }

    @Test
    @Timeout(30)
    void testCancelDuringDownload() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();

        Thread.sleep(200);
        
        download.cancel();
        
        assertEquals(DownloadState.CANCELLED, download.getState());
        
        // awaitCompletion should not throw for cancelled downloads
        download.awaitCompletion();
        
        assertEquals(DownloadState.CANCELLED, download.getState());
        
        // Verify temp directory was cleaned up
        String tempDirPath = tempDir + "/" + download.getId();
        assertFalse(Files.exists(Paths.get(tempDirPath)), 
            "Temp directory should be cleaned up after cancellation");
    }

    @Test
    @Timeout(30)
    void testCancelWhilePaused() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();

        Thread.sleep(100);
        download.pause();
        
        assertEquals(DownloadState.PAUSED, download.getState());
        
        download.cancel();
        
        assertEquals(DownloadState.CANCELLED, download.getState());
        
        download.awaitCompletion();
        
        // Verify cleanup
        String tempDirPath = tempDir + "/" + download.getId();
        assertFalse(Files.exists(Paths.get(tempDirPath)), 
            "Temp directory should be cleaned up");
    }

    @Test
    @Timeout(30)
    void testCancelAfterCompletion() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        download.start();
        download.awaitCompletion();

        assertEquals(DownloadState.COMPLETED, download.getState());
        
        // Should not throw, just return
        download.cancel();
        
        assertEquals(DownloadState.COMPLETED, download.getState(), 
            "State should remain COMPLETED");
    }

    @Test
    void testMultipleCancelCalls() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();

        Thread.sleep(100);
        
        download.cancel();
        download.cancel(); // Second cancel should be safe
        download.cancel(); // Third cancel should be safe

        assertEquals(DownloadState.CANCELLED, download.getState());
    }

    // ============================================================
    // STOP TESTS (NEW)
    // ============================================================

    @Test
    void testStopBeforeStartThrowsException() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);

        assertThrows(IllegalStateException.class, () -> {
            download.stop();
        }, "Cannot stop before starting");
    }

    @Test
    @Timeout(30)
    void testStopDuringDownload() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();

        Thread.sleep(200);
        
        download.stop();
        
        assertEquals(DownloadState.STOPPED, download.getState());
        
        // Verify temp directory still exists (NOT cleaned up)
        String tempDirPath = tempDir + "/" + download.getId();
        assertTrue(Files.exists(Paths.get(tempDirPath)), 
            "Temp directory should NOT be cleaned up after stop");
    }

    @Test
    @Timeout(30)
    void testStopWhilePaused() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();

        Thread.sleep(100);
        download.pause();
        
        assertEquals(DownloadState.PAUSED, download.getState());
        
        download.stop();
        
        assertEquals(DownloadState.STOPPED, download.getState());
        
        // Verify temp files preserved
        String tempDirPath = tempDir + "/" + download.getId();
        assertTrue(Files.exists(Paths.get(tempDirPath)), 
            "Temp directory should be preserved for resume");
    }

    @Test
    @Timeout(30)
    void testStopAfterCompletion() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        download.start();
        download.awaitCompletion();

        assertEquals(DownloadState.COMPLETED, download.getState());
        
        // Should not throw, just return
        download.stop();
        
        assertEquals(DownloadState.COMPLETED, download.getState(), 
            "State should remain COMPLETED");
    }

    @Test
    void testMultipleStopCalls() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();

        Thread.sleep(100);
        
        download.stop();
        download.stop(); // Second stop should be safe
        download.stop(); // Third stop should be safe

        assertEquals(DownloadState.STOPPED, download.getState());
    }

    // ============================================================
    // HELPER METHOD TESTS (NEW)
    // ============================================================

    @Test
    void testGetUrl() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);

        assertEquals(TEST_URL, download.getUrl());
    }

    @Test
    void testGetDestination() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);

        assertEquals(destination, download.getDestination());
    }

    @Test
    void testGetFileName() throws DownloadException
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "myfile.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);

        assertEquals("myfile.pdf", download.getFileName());
    }

    @Test
    @Timeout(30)
    void testGetDownloadedBytes() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        
        assertEquals(0, download.getDownloadedBytes(), "Should start at 0");
        
        download.start();
        download.awaitCompletion();
        
        assertEquals(download.getTotalSize(), download.getDownloadedBytes(), 
            "Should equal total size when complete");
    }

    @Test
    @Timeout(30)
    void testGetRemainingBytes() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        
        assertEquals(download.getTotalSize(), download.getRemainingBytes(), 
            "Should equal total size initially");
        
        download.start();
        download.awaitCompletion();
        
        assertEquals(0, download.getRemainingBytes(), 
            "Should be 0 when complete");
    }

    // ============================================================
    // STATE MANAGEMENT TESTS
    // ============================================================

    @Test
    void testStateTransitionPendingToDownloading() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        
        assertEquals(DownloadState.PENDING, download.getState());
        download.start();
        assertEquals(DownloadState.DOWNLOADING, download.getState());
        
        download.cancel();
    }

    @Test
    void testStateTransitionDownloadingToCompleted() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        download.start();
        
        assertEquals(DownloadState.DOWNLOADING, download.getState());
        
        download.awaitCompletion();
        
        assertEquals(DownloadState.COMPLETED, download.getState());
    }

    @Test
    void testStateTransitionDownloadingToPaused() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();
        
        Thread.sleep(100);
        
        assertEquals(DownloadState.DOWNLOADING, download.getState());
        download.pause();
        assertEquals(DownloadState.PAUSED, download.getState());
        
        download.cancel();
    }

    @Test
    void testStateTransitionPausedToDownloading() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();
        
        Thread.sleep(100);
        download.pause();
        
        assertEquals(DownloadState.PAUSED, download.getState());
        download.resume();
        assertEquals(DownloadState.DOWNLOADING, download.getState());
        
        download.cancel();
    }

    @Test
    void testStateTransitionDownloadingToStopped() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "large.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();
        
        Thread.sleep(100);
        
        assertEquals(DownloadState.DOWNLOADING, download.getState());
        download.stop();
        assertEquals(DownloadState.STOPPED, download.getState());
    }

    // ============================================================
    // ERROR HANDLING TESTS
    // ============================================================

    @Test
    void testGetErrorReturnsNullOnSuccess() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "test.pdf").toString();

        Download download = new Download(TEST_URL, destination, config, tracker);
        download.start();
        download.awaitCompletion();

        assertNull(download.getError(), "Error should be null on success");
    }

    // ============================================================
    // CHUNK CALCULATION TESTS
    // ============================================================

    @Test
    @Timeout(30)
    void testSingleChunkForSmallFile() throws Exception
    {
        // Configure for single chunk (file is ~13KB, set threshold to 20KB)
        DownloadConfig singleChunkConfig = DownloadConfig.builder()
            .numberOfThreads(1)
            .chunkSizeMB(1)
            .timeoutsInSeconds(30)
            .tempDirectory(tempDir)
            .minSizeForChunking(20 * 1024) // 20KB
            .build();

        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "small.pdf").toString();

        Download download = new Download(TEST_URL, destination, singleChunkConfig, tracker);
        download.start();
        download.awaitCompletion();

        assertEquals(DownloadState.COMPLETED, download.getState());
        assertTrue(Files.exists(Paths.get(destination)));
    }

    @Test
    @Timeout(30)
    void testMultipleChunksForLargeFile() throws Exception
    {
        // Small chunk size to force multiple chunks
        DownloadConfig multiChunkConfig = DownloadConfig.builder()
            .numberOfThreads(4)
            .chunkSize(5000) // 5KB chunks
            .timeoutsInSeconds(30)
            .tempDirectory(tempDir)
            .minSizeForChunking(1024)
            .build();

        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "multi.pdf").toString();

        Download download = new Download(TEST_URL, destination, multiChunkConfig, tracker);
        download.start();
        download.awaitCompletion();

        assertEquals(DownloadState.COMPLETED, download.getState());
        assertTrue(Files.exists(Paths.get(destination)));
        
        // Verify file size matches expected
        long downloadedSize = Files.size(Paths.get(destination));
        assertEquals(download.getTotalSize(), downloadedSize, 
            "Downloaded file size should match expected size");
    }

    // ============================================================
    // CONCURRENCY TESTS
    // ============================================================

    @Test
    @Timeout(30)
    void testConcurrentPauseAndResume() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "concurrent.mp4").toString();

        Download download = new Download(LARGE_TEST_URL, destination, config, tracker);
        download.start();

        // Multiple threads trying to pause/resume
        ExecutorService testExecutor = Executors.newFixedThreadPool(4);
        
        for(int i = 0; i < 10; i++)
        {
            testExecutor.submit(() -> {
                try
                {
                    Thread.sleep((long)(Math.random() * 100));
                    if(Math.random() > 0.5)
                    {
                        try
                        {
                            download.pause();
                        }
                        catch(IllegalStateException e)
                        {
                            // Expected - already paused or not downloading
                        }
                    }
                    else
                    {
                        try
                        {
                            download.resume();
                        }
                        catch(IllegalStateException e)
                        {
                            // Expected - not paused
                        }
                    }
                }
                catch(InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            });
        }

        testExecutor.shutdown();
        testExecutor.awaitTermination(5, TimeUnit.SECONDS);

        // Ensure download is in valid state
        DownloadState state = download.getState();
        assertTrue(state == DownloadState.DOWNLOADING || 
                   state == DownloadState.PAUSED ||
                   state == DownloadState.COMPLETED,
                   "Download should be in valid state");

        download.cancel();
    }

    // ============================================================
    // INTEGRATION TESTS
    // ============================================================

    @Test
    @Timeout(45)
    void testCompleteDownloadLifecycle() throws Exception
    {
        ProgressTracker tracker = new ProgressTracker();
        String destination = Paths.get(tempDir, "lifecycle.pdf").toString();

        // Create
        Download download = new Download(TEST_URL, destination, config, tracker);
        assertEquals(DownloadState.PENDING, download.getState());
        assertNotNull(download.getId());

        // Start
        download.start();
        assertEquals(DownloadState.DOWNLOADING, download.getState());

        // Pause
        Thread.sleep(5);
        download.pause();
        assertEquals(DownloadState.PAUSED, download.getState());

        // Resume
        download.resume();
        assertEquals(DownloadState.DOWNLOADING, download.getState());

        // Complete
        download.awaitCompletion();
        assertEquals(DownloadState.COMPLETED, download.getState());

        // Verify file
        assertTrue(Files.exists(Paths.get(destination)));
        assertEquals(download.getTotalSize(), Files.size(Paths.get(destination)));
        assertEquals(100.0, download.getProgress(), 0.01);
        assertNull(download.getError());
        
        // Verify cleanup
        String tempDirPath = tempDir + "/" + download.getId();
        assertFalse(Files.exists(Paths.get(tempDirPath)), 
            "Temp directory should be cleaned up");
    }
}

/* ============================================================
   RUNNING TESTS
   ============================================================

Run all Download tests:
   mvn test -Dtest=DownloadTest

Run specific test:
   mvn test -Dtest=DownloadTest#testStartAndCompleteDownload

Run tests by category (using method name patterns):
   mvn test -Dtest=DownloadTest#test*Pause*
   mvn test -Dtest=DownloadTest#test*Cancel*
   mvn test -Dtest=DownloadTest#test*State*
   mvn test -Dtest=DownloadTest#test*Stop*

============================================================ */