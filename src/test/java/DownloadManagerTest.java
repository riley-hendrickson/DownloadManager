import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Data.DownloadException;
import io.rileyhe1.concurrency.Data.DownloadState;
import io.rileyhe1.concurrency.DownloadManager;
import io.rileyhe1.concurrency.Util.Download;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for DownloadManager class.
 * Tests manager initialization, download lifecycle, persistence, and concurrent downloads.
 */
class DownloadManagerTest
{
    private static final String TEST_URL = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";
    private static final String LARGE_TEST_URL = "https://archive.org/download/Rick_Astley_Never_Gonna_Give_You_Up/Rick_Astley_Never_Gonna_Give_You_Up.mp4";
    
    private DownloadManager manager;
    private DownloadConfig config;
    private String tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDirectory) throws IOException, DownloadException
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
            .minSizeForChunking(1024)
            .build();
        
        // Delete any existing downloads.json from previous tests
        Files.deleteIfExists(Paths.get("downloads.json"));
        
        manager = new DownloadManager(config);
    }

    @AfterEach
    void tearDown() throws IOException
    {
        if (manager != null)
        {
            manager.shutdown();
        }
        
        // Clean up test files
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
        
        // Clean up downloads.json
        Files.deleteIfExists(Paths.get("downloads.json"));
    }

    // ============================================================
    // CONSTRUCTOR TESTS
    // ============================================================

    @Test
    void testConstructorWithValidConfig() throws IOException, DownloadException
    {
        DownloadManager testManager = new DownloadManager(config);
        assertNotNull(testManager, "Manager should be created");
        testManager.shutdown();
    }

    @Test
    void testConstructorWithNullConfig()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            new DownloadManager(null);
        }, "Should throw exception for null config");
    }

    @Test
    void testConstructorLoadsExistingDownloads() throws Exception
    {
        // Start a download and pause it
        String destination = Paths.get(tempDir, "test1.pdf").toString();
        Download download = manager.startDownload(TEST_URL, destination);
        Thread.sleep(100);
        manager.pauseDownload(download.getId());
        
        // Shutdown to save state
        manager.shutdown();
        
        // Create new manager - should load the paused download
        DownloadManager newManager = new DownloadManager(config);
        
        Download loaded = newManager.getDownload(download.getId());
        assertNotNull(loaded, "Should load saved download");
        assertEquals(download.getId(), loaded.getId(), "Should preserve download ID");
        assertEquals(DownloadState.PENDING, loaded.getState(), 
            "Loaded download should be in PENDING state");
        
        newManager.shutdown();
    }

    // ============================================================
    // START DOWNLOAD TESTS
    // ============================================================

    @Test
    @Timeout(45)
    @Tag("network")
    void testStartDownloadSuccess() throws Exception
    {
        String destination = Paths.get(tempDir, "test.pdf").toString();
        
        Download download = manager.startDownload(TEST_URL, destination);
        
        assertNotNull(download, "Should return download instance");
        assertNotNull(download.getId(), "Download should have ID");
        assertEquals(DownloadState.DOWNLOADING, download.getState(), 
            "Should be in DOWNLOADING state");
        
        download.awaitCompletion();
        
        assertEquals(DownloadState.COMPLETED, download.getState());
        assertTrue(Files.exists(Paths.get(destination)), "File should be downloaded");
    }

    @Test
    void testStartDownloadWithNullUrl()
    {
        String destination = Paths.get(tempDir, "test.pdf").toString();
        
        assertThrows(IllegalArgumentException.class, () -> {
            manager.startDownload(null, destination);
        }, "Should throw exception for null URL");
    }

    @Test
    void testStartDownloadWithEmptyUrl()
    {
        String destination = Paths.get(tempDir, "test.pdf").toString();
        
        assertThrows(IllegalArgumentException.class, () -> {
            manager.startDownload("", destination);
        }, "Should throw exception for empty URL");
    }

    @Test
    void testStartDownloadWithNullDestination()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.startDownload(TEST_URL, null);
        }, "Should throw exception for null destination");
    }

    @Test
    void testStartDownloadWithEmptyDestination()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.startDownload(TEST_URL, "");
        }, "Should throw exception for empty destination");
    }

    @Test
    void testStartDownloadWithDuplicateDestination() throws DownloadException
    {
        String destination = Paths.get(tempDir, "duplicate.pdf").toString();
        
        manager.startDownload(TEST_URL, destination);
        
        assertThrows(IllegalArgumentException.class, () -> {
            manager.startDownload(TEST_URL, destination);
        }, "Should throw exception for duplicate destination");
    }

    @Test
    void testStartMultipleDownloadsConcurrently() throws Exception
    {
        String dest1 = Paths.get(tempDir, "file1.pdf").toString();
        String dest2 = Paths.get(tempDir, "file2.pdf").toString();
        String dest3 = Paths.get(tempDir, "file3.pdf").toString();
        
        Download download1 = manager.startDownload(TEST_URL, dest1);
        Download download2 = manager.startDownload(TEST_URL, dest2);
        Download download3 = manager.startDownload(TEST_URL, dest3);
        
        assertNotNull(download1);
        assertNotNull(download2);
        assertNotNull(download3);
        
        // All should have unique IDs
        assertNotEquals(download1.getId(), download2.getId());
        assertNotEquals(download2.getId(), download3.getId());
        assertNotEquals(download1.getId(), download3.getId());
    }

    // ============================================================
    // PAUSE DOWNLOAD TESTS
    // ============================================================

    @Test
    @Timeout(30)
    @Tag("network")
    void testPauseDownload() throws Exception
    {
        String destination = Paths.get(tempDir, "pause.mp4").toString();
        Download download = manager.startDownload(LARGE_TEST_URL, destination);
        
        Thread.sleep(200);
        
        manager.pauseDownload(download.getId());
        
        assertEquals(DownloadState.PAUSED, download.getState());
    }

    @Test
    void testPauseDownloadWithNullId()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.pauseDownload(null);
        }, "Should throw exception for null ID");
    }

    @Test
    void testPauseDownloadWithEmptyId()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.pauseDownload("");
        }, "Should throw exception for empty ID");
    }

    @Test
    void testPauseDownloadWithInvalidId()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.pauseDownload("invalid-id-12345");
        }, "Should throw exception for invalid ID");
    }

    @Test
    void testPauseAlreadyCompletedDownload() throws Exception
    {
        String destination = Paths.get(tempDir, "completed.pdf").toString();
        Download download = manager.startDownload(TEST_URL, destination);
        
        download.awaitCompletion();
        
        assertThrows(IllegalStateException.class, () -> {
            manager.pauseDownload(download.getId());
        }, "Should not be able to pause completed download");
    }

    // ============================================================
    // RESUME DOWNLOAD TESTS
    // ============================================================

    @Test
    @Timeout(30)
    @Tag("network")
    void testResumeDownload() throws Exception
    {
        String destination = Paths.get(tempDir, "resume.mp4").toString();
        Download download = manager.startDownload(LARGE_TEST_URL, destination);
        
        Thread.sleep(200);
        manager.pauseDownload(download.getId());
        
        assertEquals(DownloadState.PAUSED, download.getState());
        
        manager.resumeDownload(download.getId());
        
        assertEquals(DownloadState.DOWNLOADING, download.getState());
    }

    @Test
    void testResumeDownloadWithNullId()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.resumeDownload(null);
        }, "Should throw exception for null ID");
    }

    @Test
    void testResumeDownloadWithEmptyId()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.resumeDownload("");
        }, "Should throw exception for empty ID");
    }

    @Test
    void testResumeDownloadWithInvalidId()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.resumeDownload("invalid-id-12345");
        }, "Should throw exception for invalid ID");
    }

    @Test
    @Timeout(30)
    @Tag("network")
    void testResumeLoadedDownload() throws Exception
    {
        // Start and pause a download
        String destination = Paths.get(tempDir, "loaded.mp4").toString();
        Download download = manager.startDownload(LARGE_TEST_URL, destination);
        Thread.sleep(200);
        manager.pauseDownload(download.getId());
        
        String downloadId = download.getId();
        manager.shutdown();
        
        // Create new manager and resume
        DownloadManager newManager = new DownloadManager(config);
        Download loaded = newManager.getDownload(downloadId);
        
        assertNotNull(loaded, "Should load saved download");
        assertEquals(DownloadState.PENDING, loaded.getState());
        
        // Resume should call startExisting()
        newManager.resumeDownload(downloadId);
        
        assertEquals(DownloadState.DOWNLOADING, loaded.getState());
        
        newManager.shutdown();
    }

    // ============================================================
    // CANCEL DOWNLOAD TESTS
    // ============================================================

    @Test
    @Timeout(30)
    @Tag("network")
    void testCancelDownload() throws Exception
    {
        String destination = Paths.get(tempDir, "cancel.mp4").toString();
        Download download = manager.startDownload(LARGE_TEST_URL, destination);
        
        Thread.sleep(200);
        
        manager.cancelDownload(download.getId());
        
        assertEquals(DownloadState.CANCELLED, download.getState());
        assertNull(manager.getDownload(download.getId()), 
            "Cancelled download should be removed from active downloads");
    }

    @Test
    void testCancelDownloadWithNullId()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.cancelDownload(null);
        }, "Should throw exception for null ID");
    }

    @Test
    void testCancelDownloadWithEmptyId()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.cancelDownload("");
        }, "Should throw exception for empty ID");
    }

    @Test
    void testCancelDownloadWithInvalidId()
    {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.cancelDownload("invalid-id-12345");
        }, "Should throw exception for invalid ID");
    }

    // ============================================================
    // GET DOWNLOAD TESTS
    // ============================================================

    @Test
    void testGetDownload() throws DownloadException
    {
        String destination = Paths.get(tempDir, "get.pdf").toString();
        Download download = manager.startDownload(TEST_URL, destination);
        
        Download retrieved = manager.getDownload(download.getId());
        
        assertNotNull(retrieved, "Should retrieve download");
        assertEquals(download.getId(), retrieved.getId());
        assertSame(download, retrieved, "Should return same instance");
    }

    @Test
    void testGetDownloadWithInvalidId()
    {
        Download retrieved = manager.getDownload("invalid-id-12345");
        
        assertNull(retrieved, "Should return null for invalid ID");
    }

    @Test
    void testGetDownloadWithNullId()
    {
        Download retrieved = manager.getDownload(null);
        
        assertNull(retrieved, "Should return null for null ID");
    }

    // ============================================================
    // GET ALL DOWNLOADS TESTS
    // ============================================================

    @Test
    void testGetAllDownloadsEmpty()
    {
        List<Download> downloads = manager.getAllDownloads();
        
        assertNotNull(downloads, "Should return list even when empty");
        assertTrue(downloads.isEmpty(), "Should be empty initially");
    }

    @Test
    void testGetAllDownloadsWithActiveDownloads() throws DownloadException
    {
        String dest1 = Paths.get(tempDir, "file1.pdf").toString();
        String dest2 = Paths.get(tempDir, "file2.pdf").toString();
        
        Download download1 = manager.startDownload(TEST_URL, dest1);
        Download download2 = manager.startDownload(TEST_URL, dest2);
        
        List<Download> downloads = manager.getAllDownloads();
        
        assertEquals(2, downloads.size(), "Should return all downloads");
        assertTrue(downloads.contains(download1));
        assertTrue(downloads.contains(download2));
    }

    @Test
    void testGetAllDownloadsDoesNotIncludeCancelled() throws Exception
    {
        String dest1 = Paths.get(tempDir, "file1.mp4").toString();
        String dest2 = Paths.get(tempDir, "file2.mp4").toString();
        
        Download download1 = manager.startDownload(LARGE_TEST_URL, dest1);
        Download download2 = manager.startDownload(LARGE_TEST_URL, dest2);
        
        Thread.sleep(100);
        manager.cancelDownload(download1.getId());
        
        List<Download> downloads = manager.getAllDownloads();
        
        assertEquals(1, downloads.size(), "Should only have non-cancelled download");
        assertFalse(downloads.contains(download1), "Should not include cancelled");
        assertTrue(downloads.contains(download2));
    }

    // ============================================================
    // PERSISTENCE TESTS
    // ============================================================

    @Test
    @Timeout(30)
    @Tag("network")
    void testPersistenceOnShutdown() throws Exception
    {
        String destination = Paths.get(tempDir, "persist.mp4").toString();
        Download download = manager.startDownload(LARGE_TEST_URL, destination);
        
        Thread.sleep(200);
        manager.pauseDownload(download.getId());
        
        String downloadId = download.getId();
        manager.shutdown();
        
        // Verify downloads.json was created
        assertTrue(Files.exists(Paths.get("downloads.json")), 
            "downloads.json should be created");
        
        // Create new manager and verify download was loaded
        DownloadManager newManager = new DownloadManager(config);
        Download loaded = newManager.getDownload(downloadId);
        
        assertNotNull(loaded, "Download should be loaded");
        assertEquals(downloadId, loaded.getId(), "ID should be preserved");
        
        newManager.shutdown();
    }

    @Test
    void testPersistenceDoesNotSaveCompleted() throws Exception
    {
        String destination = Paths.get(tempDir, "completed.pdf").toString();
        Download download = manager.startDownload(TEST_URL, destination);
        
        download.awaitCompletion();
        assertEquals(DownloadState.COMPLETED, download.getState());
        
        manager.shutdown();
        
        // Completed downloads should not be persisted
        DownloadManager newManager = new DownloadManager(config);
        Download loaded = newManager.getDownload(download.getId());
        
        assertNull(loaded, "Completed downloads should not be persisted");
        
        newManager.shutdown();
    }

    @Test
    void testPersistenceDoesNotSaveCancelled() throws Exception
    {
        String destination = Paths.get(tempDir, "cancelled.mp4").toString();
        Download download = manager.startDownload(LARGE_TEST_URL, destination);
        
        Thread.sleep(100);
        manager.cancelDownload(download.getId());
        
        manager.shutdown();
        
        // Cancelled downloads should not be persisted
        DownloadManager newManager = new DownloadManager(config);
        Download loaded = newManager.getDownload(download.getId());
        
        assertNull(loaded, "Cancelled downloads should not be persisted");
        
        newManager.shutdown();
    }

    @Test
    @Tag("network")
    void testLoadDownloadsWithNoFile() throws IOException, DownloadException
    {
        // Ensure no downloads.json exists
        Files.deleteIfExists(Paths.get("downloads.json"));
        
        // Should not throw exception
        DownloadManager newManager = new DownloadManager(config);
        
        List<Download> downloads = newManager.getAllDownloads();
        assertTrue(downloads.isEmpty(), "Should have no downloads");
        
        newManager.shutdown();
    }

    // ============================================================
    // SHUTDOWN TESTS
    // ============================================================

    @Test
    @Timeout(30)
    @Tag("network")
    void testShutdownPausesActiveDownloads() throws Exception
    {
        String destination = Paths.get(tempDir, "shutdown.mp4").toString();
        Download download = manager.startDownload(LARGE_TEST_URL, destination);
        
        Thread.sleep(200);
        assertEquals(DownloadState.DOWNLOADING, download.getState());
        
        manager.shutdown();
        
        // After shutdown, download should be paused
        assertEquals(DownloadState.PAUSED, download.getState());
    }

    @Test
    @Timeout(30)
    @Tag("network")
    void testShutdownCancelsAllDownloads() throws Exception
    {
        String dest1 = Paths.get(tempDir, "file1.mp4").toString();
        String dest2 = Paths.get(tempDir, "file2.mp4").toString();
        
        Download download1 = manager.startDownload(LARGE_TEST_URL, dest1);
        Download download2 = manager.startDownload(LARGE_TEST_URL, dest2);
        
        Thread.sleep(200);
        
        manager.shutdown();
        
        // Both downloads should be cancelled after shutdown
        assertEquals(DownloadState.CANCELLED, download1.getState());
        assertEquals(DownloadState.CANCELLED, download2.getState());
    }

    @Test
    void testShutdownClearsActiveDownloads() throws Exception
    {
        String destination = Paths.get(tempDir, "clear.pdf").toString();
        manager.startDownload(TEST_URL, destination);
        
        assertFalse(manager.getAllDownloads().isEmpty(), "Should have downloads");
        
        manager.shutdown();
        
        assertTrue(manager.getAllDownloads().isEmpty(), 
            "Active downloads should be cleared after shutdown");
    }

    @Test
    void testShutdownCanBeCalledMultipleTimes()
    {
        assertDoesNotThrow(() -> {
            manager.shutdown();
            manager.shutdown();
            manager.shutdown();
        }, "Shutdown should be idempotent");
    }

    // ============================================================
    // INTEGRATION TESTS
    // ============================================================

    @Test
    @Timeout(60)
    @Tag("network")
    @Tag("integration")
    void testCompleteDownloadLifecycleWithPersistence() throws Exception
    {
        String destination = Paths.get(tempDir, "lifecycle.mp4").toString();
        
        // Start download
        Download download = manager.startDownload(LARGE_TEST_URL, destination);
        String downloadId = download.getId();
        assertEquals(DownloadState.DOWNLOADING, download.getState());
        
        // Pause and save
        Thread.sleep(500);
        manager.pauseDownload(downloadId);
        assertEquals(DownloadState.PAUSED, download.getState());
        manager.shutdown();
        
        // Load and resume
        DownloadManager newManager = new DownloadManager(config);
        Download loaded = newManager.getDownload(downloadId);
        assertNotNull(loaded);
        assertEquals(DownloadState.PENDING, loaded.getState());
        
        newManager.resumeDownload(downloadId);
        assertEquals(DownloadState.DOWNLOADING, loaded.getState());
        
        // Pause again
        Thread.sleep(500);
        newManager.pauseDownload(downloadId);
        
        // Resume to completion
        newManager.resumeDownload(downloadId);
        loaded.awaitCompletion();
        
        assertEquals(DownloadState.COMPLETED, loaded.getState());
        assertTrue(Files.exists(Paths.get(destination)));
        
        newManager.shutdown();
    }

    @Test
    @Timeout(60)
    @Tag("network")
    @Tag("integration")
    void testMultipleConcurrentDownloadsWithMixedStates() throws Exception
    {
        String dest1 = Paths.get(tempDir, "concurrent1.pdf").toString();
        String dest2 = Paths.get(tempDir, "concurrent2.mp4").toString();
        String dest3 = Paths.get(tempDir, "concurrent3.pdf").toString();
        
        // Start three downloads
        Download download1 = manager.startDownload(TEST_URL, dest1);
        Download download2 = manager.startDownload(LARGE_TEST_URL, dest2);
        Download download3 = manager.startDownload(TEST_URL, dest3);
        
        Thread.sleep(300);
        
        // Pause one
        manager.pauseDownload(download2.getId());
        
        // Wait for small downloads to complete
        download1.awaitCompletion();
        download3.awaitCompletion();
        
        // Verify states
        assertEquals(DownloadState.COMPLETED, download1.getState());
        assertEquals(DownloadState.PAUSED, download2.getState());
        assertEquals(DownloadState.COMPLETED, download3.getState());
        
        // Resume and complete the paused one
        manager.resumeDownload(download2.getId());
        
        // Cancel it instead
        Thread.sleep(200);
        manager.cancelDownload(download2.getId());
        
        assertEquals(DownloadState.CANCELLED, download2.getState());
    }

    // ============================================================
    // ERROR HANDLING TESTS
    // ============================================================

    @Test
    void testStartDownloadWithInvalidUrl()
    {
        String destination = Paths.get(tempDir, "invalid.pdf").toString();
        
        assertThrows(DownloadException.class, () -> {
            manager.startDownload("https://invalid-url-12345.com/file.pdf", destination);
        }, "Should throw exception for invalid URL");
    }

    @Test
    void testOperationsAfterShutdown() throws Exception
    {
        // String destination = 
        Paths.get(tempDir, "aftershutdown.pdf").toString();
        
        manager.shutdown();
        
        // All operations should fail gracefully or throw appropriate exceptions
        // The behavior depends on your implementation
        // This test documents the expected behavior
    }
}

/* ============================================================
   RUNNING TESTS
   ============================================================

Run all DownloadManager tests:
   mvn test -Dtest=DownloadManagerTest

Run only fast tests (no network):
   mvn test -Dtest=DownloadManagerTest -Dgroups="!network"

Run only integration tests:
   mvn test -Dtest=DownloadManagerTest -Dgroups="integration"

Run specific test:
   mvn test -Dtest=DownloadManagerTest#testStartDownloadSuccess

============================================================ */
