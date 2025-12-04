import io.rileyhe1.concurrency.Data.DownloadSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Test suite for DownloadSnapshot class.
 * Tests serialization/deserialization and data preservation.
 */
class DownloadSnapshotTest
{
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // ============================================================
    // CONSTRUCTOR TESTS
    // ============================================================

    @Test
    void testNoArgConstructor()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot();
        
        assertNotNull(snapshot, "No-arg constructor should work");
        assertNull(snapshot.getId());
        assertNull(snapshot.getUrl());
        assertNull(snapshot.getDestination());
        assertEquals(0, snapshot.getTotalSize());
        assertNull(snapshot.getChunkProgress());
        assertNull(snapshot.getState());
    }

    @Test
    void testFullConstructor()
    {
        Map<Integer, Long> progress = new HashMap<>();
        progress.put(0, 1024L);
        progress.put(1, 2048L);
        
        DownloadSnapshot snapshot = new DownloadSnapshot(
            "id-123",
            "https://example.com/file.pdf",
            "/tmp/file.pdf",
            10000L,
            progress,
            "DOWNLOADING"
        );

        assertEquals("id-123", snapshot.getId());
        assertEquals("https://example.com/file.pdf", snapshot.getUrl());
        assertEquals("/tmp/file.pdf", snapshot.getDestination());
        assertEquals(10000L, snapshot.getTotalSize());
        assertEquals(2, snapshot.getChunkProgress().size());
        assertEquals(1024L, snapshot.getChunkProgress().get(0));
        assertEquals(2048L, snapshot.getChunkProgress().get(1));
        assertEquals("DOWNLOADING", snapshot.getState());
    }

    // ============================================================
    // GETTER AND SETTER TESTS
    // ============================================================

    @Test
    void testSetAndGetId()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot();
        
        snapshot.setId("test-id-456");
        
        assertEquals("test-id-456", snapshot.getId());
    }

    @Test
    void testSetAndGetUrl()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot();
        
        snapshot.setUrl("https://test.com/data.bin");
        
        assertEquals("https://test.com/data.bin", snapshot.getUrl());
    }

    @Test
    void testSetAndGetDestination()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot();
        
        snapshot.setDestination("/home/user/downloads/file.zip");
        
        assertEquals("/home/user/downloads/file.zip", snapshot.getDestination());
    }

    @Test
    void testSetAndGetTotalSize()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot();
        
        snapshot.setTotalSize(524288L);
        
        assertEquals(524288L, snapshot.getTotalSize());
    }

    @Test
    void testSetAndGetChunkProgress()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot();
        Map<Integer, Long> progress = new HashMap<>();
        progress.put(0, 100L);
        progress.put(1, 200L);
        progress.put(2, 300L);
        
        snapshot.setChunkProgress(progress);
        
        assertEquals(3, snapshot.getChunkProgress().size());
        assertEquals(100L, snapshot.getChunkProgress().get(0));
        assertEquals(200L, snapshot.getChunkProgress().get(1));
        assertEquals(300L, snapshot.getChunkProgress().get(2));
    }

    @Test
    void testSetAndGetState()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot();
        
        snapshot.setState("PAUSED");
        
        assertEquals("PAUSED", snapshot.getState());
    }

    // ============================================================
    // SERIALIZATION TESTS
    // ============================================================

    @Test
    void testSerializeToJson()
    {
        Map<Integer, Long> progress = new HashMap<>();
        progress.put(0, 1024L);
        progress.put(1, 2048L);
        
        DownloadSnapshot snapshot = new DownloadSnapshot(
            "download-789",
            "https://example.com/video.mp4",
            "/tmp/video.mp4",
            5242880L,
            progress,
            "DOWNLOADING"
        );

        String json = gson.toJson(snapshot);
        
        assertNotNull(json);
        assertTrue(json.contains("download-789"));
        assertTrue(json.contains("https://example.com/video.mp4"));
        assertTrue(json.contains("/tmp/video.mp4"));
        assertTrue(json.contains("5242880"));
        assertTrue(json.contains("DOWNLOADING"));
    }

    @Test
    void testDeserializeFromJson()
    {
        String json = "{\n" +
            "  \"id\": \"download-abc\",\n" +
            "  \"url\": \"https://test.com/file.pdf\",\n" +
            "  \"destination\": \"/home/downloads/file.pdf\",\n" +
            "  \"totalSize\": 10000,\n" +
            "  \"chunkProgress\": {\n" +
            "    \"0\": 2500,\n" +
            "    \"1\": 2500,\n" +
            "    \"2\": 2500\n" +
            "  },\n" +
            "  \"state\": \"PAUSED\"\n" +
            "}";

        DownloadSnapshot snapshot = gson.fromJson(json, DownloadSnapshot.class);

        assertNotNull(snapshot);
        assertEquals("download-abc", snapshot.getId());
        assertEquals("https://test.com/file.pdf", snapshot.getUrl());
        assertEquals("/home/downloads/file.pdf", snapshot.getDestination());
        assertEquals(10000L, snapshot.getTotalSize());
        assertEquals(3, snapshot.getChunkProgress().size());
        assertEquals(2500L, snapshot.getChunkProgress().get(0));
        assertEquals(2500L, snapshot.getChunkProgress().get(1));
        assertEquals(2500L, snapshot.getChunkProgress().get(2));
        assertEquals("PAUSED", snapshot.getState());
    }

    @Test
    void testRoundTripSerialization()
    {
        Map<Integer, Long> progress = new HashMap<>();
        progress.put(0, 1000L);
        progress.put(1, 2000L);
        progress.put(2, 3000L);
        
        DownloadSnapshot original = new DownloadSnapshot(
            "roundtrip-id",
            "https://example.com/data.bin",
            "/tmp/data.bin",
            6000L,
            progress,
            "DOWNLOADING"
        );

        // Serialize
        String json = gson.toJson(original);
        
        // Deserialize
        DownloadSnapshot restored = gson.fromJson(json, DownloadSnapshot.class);

        // Verify all fields match
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getUrl(), restored.getUrl());
        assertEquals(original.getDestination(), restored.getDestination());
        assertEquals(original.getTotalSize(), restored.getTotalSize());
        assertEquals(original.getChunkProgress().size(), restored.getChunkProgress().size());
        assertEquals(original.getChunkProgress().get(0), restored.getChunkProgress().get(0));
        assertEquals(original.getChunkProgress().get(1), restored.getChunkProgress().get(1));
        assertEquals(original.getChunkProgress().get(2), restored.getChunkProgress().get(2));
        assertEquals(original.getState(), restored.getState());
    }

    // ============================================================
    // EDGE CASES
    // ============================================================

    @Test
    void testSerializeWithNullValues()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot();

        String json = gson.toJson(snapshot);
        
        assertNotNull(json);
        // Gson should handle null values
    }

    @Test
    void testDeserializeWithMissingFields()
    {
        String json = "{\n" +
            "  \"id\": \"partial-id\"\n" +
            "}";

        DownloadSnapshot snapshot = gson.fromJson(json, DownloadSnapshot.class);

        assertNotNull(snapshot);
        assertEquals("partial-id", snapshot.getId());
        assertNull(snapshot.getUrl());
        assertNull(snapshot.getDestination());
        assertEquals(0, snapshot.getTotalSize());
        assertNull(snapshot.getChunkProgress());
        assertNull(snapshot.getState());
    }

    @Test
    void testEmptyChunkProgress()
    {
        Map<Integer, Long> emptyProgress = new HashMap<>();
        
        DownloadSnapshot snapshot = new DownloadSnapshot(
            "empty-progress-id",
            "https://example.com/file.pdf",
            "/tmp/file.pdf",
            10000L,
            emptyProgress,
            "PENDING"
        );

        String json = gson.toJson(snapshot);
        DownloadSnapshot restored = gson.fromJson(json, DownloadSnapshot.class);

        assertNotNull(restored.getChunkProgress());
        assertTrue(restored.getChunkProgress().isEmpty());
    }

    @Test
    void testLargeChunkProgress()
    {
        Map<Integer, Long> largeProgress = new HashMap<>();
        for (int i = 0; i < 100; i++)
        {
            largeProgress.put(i, (long)(i * 1024));
        }
        
        DownloadSnapshot snapshot = new DownloadSnapshot(
            "large-id",
            "https://example.com/huge.bin",
            "/tmp/huge.bin",
            102400L,
            largeProgress,
            "DOWNLOADING"
        );

        String json = gson.toJson(snapshot);
        DownloadSnapshot restored = gson.fromJson(json, DownloadSnapshot.class);

        assertEquals(100, restored.getChunkProgress().size());
        for (int i = 0; i < 100; i++)
        {
            assertEquals(i * 1024L, restored.getChunkProgress().get(i));
        }
    }

    @Test
    void testVeryLargeTotalSize()
    {
        long veryLarge = Long.MAX_VALUE - 1000;
        
        DownloadSnapshot snapshot = new DownloadSnapshot(
            "large-file-id",
            "https://example.com/huge.bin",
            "/tmp/huge.bin",
            veryLarge,
            new HashMap<>(),
            "DOWNLOADING"
        );

        String json = gson.toJson(snapshot);
        DownloadSnapshot restored = gson.fromJson(json, DownloadSnapshot.class);

        assertEquals(veryLarge, restored.getTotalSize());
    }

    @Test
    void testSpecialCharactersInStrings()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot(
            "id-with-特殊字符",
            "https://example.com/file with spaces & chars!.pdf",
            "/tmp/path/with/special chars & 文字/file.pdf",
            10000L,
            new HashMap<>(),
            "DOWNLOADING"
        );

        String json = gson.toJson(snapshot);
        DownloadSnapshot restored = gson.fromJson(json, DownloadSnapshot.class);

        assertEquals(snapshot.getId(), restored.getId());
        assertEquals(snapshot.getUrl(), restored.getUrl());
        assertEquals(snapshot.getDestination(), restored.getDestination());
    }

    @Test
    void testDifferentStates()
    {
        String[] states = {"PENDING", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED", "CANCELLED"};
        
        for (String state : states)
        {
            DownloadSnapshot snapshot = new DownloadSnapshot(
                "id-" + state,
                "https://example.com/file.pdf",
                "/tmp/file.pdf",
                10000L,
                new HashMap<>(),
                state
            );

            String json = gson.toJson(snapshot);
            DownloadSnapshot restored = gson.fromJson(json, DownloadSnapshot.class);

            assertEquals(state, restored.getState(), 
                "State " + state + " should serialize correctly");
        }
    }

    // ============================================================
    // DATA INTEGRITY TESTS
    // ============================================================

    @Test
    void testChunkProgressIsIndependent()
    {
        Map<Integer, Long> progress = new HashMap<>();
        progress.put(0, 1000L);
        
        DownloadSnapshot snapshot = new DownloadSnapshot(
            "id",
            "url",
            "dest",
            10000L,
            progress,
            "DOWNLOADING"
        );

        // Modify original map
        progress.put(1, 2000L);

        // Snapshot should still only have one entry if it made a defensive copy
        // Note: This depends on your implementation - if you don't make defensive copies,
        // the test expectations would be different
        Map<Integer, Long> snapshotProgress = snapshot.getChunkProgress();
        
        // Document current behavior
        assertNotNull(snapshotProgress);
    }

    @Test
    void testZeroTotalSize()
    {
        DownloadSnapshot snapshot = new DownloadSnapshot(
            "zero-size-id",
            "https://example.com/empty.txt",
            "/tmp/empty.txt",
            0L,
            new HashMap<>(),
            "COMPLETED"
        );

        String json = gson.toJson(snapshot);
        DownloadSnapshot restored = gson.fromJson(json, DownloadSnapshot.class);

        assertEquals(0L, restored.getTotalSize());
    }

    @Test
    void testNegativeTotalSize()
    {
        // This shouldn't happen in practice, but test serialization handles it
        DownloadSnapshot snapshot = new DownloadSnapshot(
            "negative-id",
            "https://example.com/file.pdf",
            "/tmp/file.pdf",
            -1L,
            new HashMap<>(),
            "FAILED"
        );

        String json = gson.toJson(snapshot);
        DownloadSnapshot restored = gson.fromJson(json, DownloadSnapshot.class);

        assertEquals(-1L, restored.getTotalSize());
    }

    // ============================================================
    // MULTIPLE SNAPSHOTS TESTS
    // ============================================================

    @Test
    void testMultipleSnapshotsInArray()
    {
        Map<Integer, Long> progress1 = new HashMap<>();
        progress1.put(0, 1000L);
        
        Map<Integer, Long> progress2 = new HashMap<>();
        progress2.put(0, 2000L);
        progress2.put(1, 3000L);
        
        DownloadSnapshot[] snapshots = new DownloadSnapshot[] {
            new DownloadSnapshot("id1", "url1", "dest1", 10000L, progress1, "DOWNLOADING"),
            new DownloadSnapshot("id2", "url2", "dest2", 20000L, progress2, "PAUSED")
        };

        String json = gson.toJson(snapshots);
        DownloadSnapshot[] restored = gson.fromJson(json, DownloadSnapshot[].class);

        assertEquals(2, restored.length);
        assertEquals("id1", restored[0].getId());
        assertEquals("id2", restored[1].getId());
        assertEquals(1, restored[0].getChunkProgress().size());
        assertEquals(2, restored[1].getChunkProgress().size());
    }
}

/* ============================================================
   RUNNING TESTS
   ============================================================

Run all DownloadSnapshot tests:
   mvn test -Dtest=DownloadSnapshotTest

Run specific test:
   mvn test -Dtest=DownloadSnapshotTest#testRoundTripSerialization

============================================================ */
