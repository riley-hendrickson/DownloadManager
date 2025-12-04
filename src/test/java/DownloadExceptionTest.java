import io.rileyhe1.concurrency.Data.DownloadException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for DownloadException class.
 * Tests all constructor variants and error context preservation.
 */
class DownloadExceptionTest
{
    // ============================================================
    // CONSTRUCTOR TESTS
    // ============================================================

    @Test
    void testSimpleMessageConstructor()
    {
        String message = "Download failed";
        DownloadException ex = new DownloadException(message);

        assertEquals(message, ex.getMessage());
        assertNull(ex.getDownloadId(), "Download ID should be null");
        assertNull(ex.getUrl(), "URL should be null");
        assertNull(ex.getCause(), "Cause should be null");
    }

    @Test
    void testMessageWithCauseConstructor()
    {
        String message = "Download failed due to network error";
        IOException cause = new IOException("Connection timeout");
        
        DownloadException ex = new DownloadException(message, cause);

        assertEquals(message, ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNull(ex.getDownloadId(), "Download ID should be null");
        assertNull(ex.getUrl(), "URL should be null");
    }

    @Test
    void testMessageWithContextConstructor()
    {
        String message = "Download failed";
        String downloadId = "download-123";
        String url = "https://example.com/file.pdf";
        
        DownloadException ex = new DownloadException(message, downloadId, url);

        assertEquals(message, ex.getMessage());
        assertEquals(downloadId, ex.getDownloadId(), "Download ID should be preserved");
        assertEquals(url, ex.getUrl(), "URL should be preserved");
        assertNull(ex.getCause(), "Cause should be null");
    }

    @Test
    void testFullConstructor()
    {
        String message = "Download failed with full context";
        String downloadId = "download-456";
        String url = "https://example.com/large-file.bin";
        IOException cause = new IOException("Network unreachable");
        
        DownloadException ex = new DownloadException(message, cause, downloadId, url);

        assertEquals(message, ex.getMessage());
        assertEquals(downloadId, ex.getDownloadId());
        assertEquals(url, ex.getUrl());
        assertSame(cause, ex.getCause());
    }

    // ============================================================
    // GETTER TESTS
    // ============================================================

    @Test
    void testGetDownloadIdWithContext()
    {
        String downloadId = "download-789";
        DownloadException ex = new DownloadException("Error", downloadId, "url");

        assertEquals(downloadId, ex.getDownloadId());
    }

    @Test
    void testGetDownloadIdWithoutContext()
    {
        DownloadException ex = new DownloadException("Error");

        assertNull(ex.getDownloadId());
    }

    @Test
    void testGetUrlWithContext()
    {
        String url = "https://test.com/file.txt";
        DownloadException ex = new DownloadException("Error", "id", url);

        assertEquals(url, ex.getUrl());
    }

    @Test
    void testGetUrlWithoutContext()
    {
        DownloadException ex = new DownloadException("Error");

        assertNull(ex.getUrl());
    }

    // ============================================================
    // TO_STRING TESTS
    // ============================================================

    @Test
    void testToStringWithMinimalInfo()
    {
        DownloadException ex = new DownloadException("Simple error");
        String result = ex.toString();

        assertTrue(result.contains("DownloadException"));
        assertTrue(result.contains("Simple error"));
        assertFalse(result.contains("downloadId"));
        assertFalse(result.contains("url"));
    }

    @Test
    void testToStringWithDownloadId()
    {
        DownloadException ex = new DownloadException("Error", "download-123", null);
        String result = ex.toString();

        assertTrue(result.contains("DownloadException"));
        assertTrue(result.contains("Error"));
        assertTrue(result.contains("downloadId=download-123"));
    }

    @Test
    void testToStringWithUrl()
    {
        DownloadException ex = new DownloadException("Error", null, "https://example.com/file.pdf");
        String result = ex.toString();

        assertTrue(result.contains("DownloadException"));
        assertTrue(result.contains("Error"));
        assertTrue(result.contains("url=https://example.com/file.pdf"));
    }

    @Test
    void testToStringWithAllContext()
    {
        DownloadException ex = new DownloadException(
            "Download failed", 
            "download-999", 
            "https://test.com/data.bin"
        );
        String result = ex.toString();

        assertTrue(result.contains("DownloadException"));
        assertTrue(result.contains("Download failed"));
        assertTrue(result.contains("downloadId=download-999"));
        assertTrue(result.contains("url=https://test.com/data.bin"));
    }

    @Test
    void testToStringWithCause()
    {
        IOException cause = new IOException("Network timeout");
        DownloadException ex = new DownloadException("Download failed", cause, "id-123", "url");
        String result = ex.toString();

        assertTrue(result.contains("DownloadException"));
        assertTrue(result.contains("Download failed"));
        assertTrue(result.contains("caused by"));
        assertTrue(result.contains("IOException"));
        assertTrue(result.contains("Network timeout"));
    }

    // ============================================================
    // EDGE CASES
    // ============================================================

    @Test
    void testNullMessage()
    {
        DownloadException ex = new DownloadException(null);
        
        assertNull(ex.getMessage());
        assertNull(ex.getDownloadId());
        assertNull(ex.getUrl());
    }

    @Test
    void testEmptyMessage()
    {
        DownloadException ex = new DownloadException("");
        
        assertEquals("", ex.getMessage());
    }

    @Test
    void testNullDownloadIdAndUrl()
    {
        DownloadException ex = new DownloadException("Error", null, null);
        
        assertEquals("Error", ex.getMessage());
        assertNull(ex.getDownloadId());
        assertNull(ex.getUrl());
    }

    @Test
    void testEmptyDownloadIdAndUrl()
    {
        DownloadException ex = new DownloadException("Error", "", "");
        
        assertEquals("Error", ex.getMessage());
        assertEquals("", ex.getDownloadId());
        assertEquals("", ex.getUrl());
    }

    @Test
    void testVeryLongMessage()
    {
        String longMessage = "Error: " + "x".repeat(1000);
        DownloadException ex = new DownloadException(longMessage);
        
        assertEquals(longMessage, ex.getMessage());
    }

    @Test
    void testSpecialCharactersInContext()
    {
        String url = "https://example.com/file with spaces & special chars!.pdf";
        String downloadId = "id-with-特殊-chars-123";
        
        DownloadException ex = new DownloadException("Error", downloadId, url);
        
        assertEquals(downloadId, ex.getDownloadId());
        assertEquals(url, ex.getUrl());
    }

    // ============================================================
    // EXCEPTION CHAIN TESTS
    // ============================================================

    @Test
    void testNestedExceptionChain()
    {
        Exception rootCause = new Exception("Root cause");
        IOException ioException = new IOException("IO error", rootCause);
        DownloadException downloadException = new DownloadException(
            "Download failed", 
            ioException, 
            "id-123", 
            "url"
        );

        assertEquals(ioException, downloadException.getCause());
        assertEquals(rootCause, downloadException.getCause().getCause());
    }

    @Test
    void testCauseIsPreserved()
    {
        InterruptedException cause = new InterruptedException("Thread interrupted");
        DownloadException ex = new DownloadException("Download cancelled", cause);

        assertSame(cause, ex.getCause());
        assertTrue(ex.getCause() instanceof InterruptedException);
    }

    // ============================================================
    // INSTANCEOF TESTS
    // ============================================================

    @Test
    void testIsException()
    {
        DownloadException ex = new DownloadException("Error");
        
        assertTrue(ex instanceof Exception);
        assertTrue(ex instanceof Throwable);
    }

    @Test
    void testIsNotRuntimeException()
    {
        DownloadException ex = new DownloadException("Error");
        
        assertFalse(ex instanceof RuntimeException);
    }

    // ============================================================
    // SERIALIZATION COMPATIBILITY TESTS (if needed)
    // ============================================================

    @Test
    void testExceptionCanBeThrownAndCaught()
    {
        assertThrows(DownloadException.class, () -> {
            throw new DownloadException("Test exception");
        });
    }

    @Test
    void testExceptionCanBeCaughtAsException()
    {
        try
        {
            throw new DownloadException("Test exception", "id", "url");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof DownloadException);
            DownloadException de = (DownloadException) e;
            assertEquals("id", de.getDownloadId());
            assertEquals("url", de.getUrl());
        }
    }

    // ============================================================
    // REAL-WORLD USAGE SCENARIOS
    // ============================================================

    @Test
    void testScenarioNetworkError()
    {
        IOException networkError = new IOException("Connection refused");
        DownloadException ex = new DownloadException(
            "Failed to download file",
            networkError,
            "download-abc123",
            "https://example.com/largefile.zip"
        );

        assertEquals("Failed to download file", ex.getMessage());
        assertEquals("download-abc123", ex.getDownloadId());
        assertEquals("https://example.com/largefile.zip", ex.getUrl());
        assertEquals("Connection refused", ex.getCause().getMessage());
    }

    @Test
    void testScenarioChunkFailure()
    {
        DownloadException ex = new DownloadException(
            "Chunk 5 of 10 failed to download",
            "download-xyz789",
            "https://cdn.example.com/video.mp4"
        );

        assertTrue(ex.getMessage().contains("Chunk 5"));
        assertEquals("download-xyz789", ex.getDownloadId());
        assertEquals("https://cdn.example.com/video.mp4", ex.getUrl());
    }

    @Test
    void testScenarioInvalidMetadata()
    {
        DownloadException ex = new DownloadException(
            "Failed to retrieve file metadata",
            new IOException("Server returned 404"),
            "download-metadata-001",
            "https://test.com/missing-file.pdf"
        );

        assertTrue(ex.getMessage().contains("metadata"));
        assertNotNull(ex.getCause());
        assertEquals(IOException.class, ex.getCause().getClass());
    }
}

/* ============================================================
   RUNNING TESTS
   ============================================================

Run all DownloadException tests:
   mvn test -Dtest=DownloadExceptionTest

Run specific test:
   mvn test -Dtest=DownloadExceptionTest#testFullConstructor

============================================================ */
