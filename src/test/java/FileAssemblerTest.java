import io.rileyhe1.concurrency.Data.ChunkResult;
import io.rileyhe1.concurrency.Util.FileAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for FileAssembler class.
 * Tests chunk assembly, validation, and cleanup functionality.
 */
class FileAssemblerTest
{
    private String tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDirectory)
    {
        tempDir = tempDirectory.toString();
    }

    @AfterEach
    void tearDown() throws IOException
    {
        // Clean up any remaining test files
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
    void testSuccessfulAssembly() throws IOException
    {
        // Create 3 temp chunk files with known content
        Path chunk0 = Paths.get(tempDir, "chunk0.bin");
        Path chunk1 = Paths.get(tempDir, "chunk1.bin");
        Path chunk2 = Paths.get(tempDir, "chunk2.bin");

        Files.write(chunk0, "Hello".getBytes());
        Files.write(chunk1, " World".getBytes());
        Files.write(chunk2, "!".getBytes());

        // Create ChunkResults
        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success(chunk0.toString(), 5, 0));
        results.add(ChunkResult.success(chunk1.toString(), 6, 1));
        results.add(ChunkResult.success(chunk2.toString(), 1, 2));

        // Assemble
        String destination = Paths.get(tempDir, "output.txt").toString();
        FileAssembler.assembleChunks(results, destination);

        // Verify output file exists and has correct content
        assertTrue(Files.exists(Paths.get(destination)), "Output file should exist");
        String content = Files.readString(Paths.get(destination));
        assertEquals("Hello World!", content, "File should contain merged content");
    }

    @Test
    void testAssemblyWithOutOfOrderResults() throws IOException
    {
        // Create chunks out of order
        Path chunk0 = Paths.get(tempDir, "chunk0.bin");
        Path chunk1 = Paths.get(tempDir, "chunk1.bin");
        Path chunk2 = Paths.get(tempDir, "chunk2.bin");

        Files.write(chunk0, "First".getBytes());
        Files.write(chunk1, "Second".getBytes());
        Files.write(chunk2, "Third".getBytes());

        // Add results in wrong order
        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success(chunk2.toString(), 5, 2)); // chunk 2 first
        results.add(ChunkResult.success(chunk0.toString(), 5, 0)); // chunk 0 second
        results.add(ChunkResult.success(chunk1.toString(), 6, 1)); // chunk 1 third

        String destination = Paths.get(tempDir, "output.txt").toString();
        FileAssembler.assembleChunks(results, destination);

        // Should still be assembled in correct order
        String content = Files.readString(Paths.get(destination));
        assertEquals("FirstSecondThird", content);
    }

    @Test
    void testSingleChunkAssembly() throws IOException
    {
        // Test with just one chunk
        Path chunk0 = Paths.get(tempDir, "chunk0.bin");
        Files.write(chunk0, "OnlyChunk".getBytes());

        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success(chunk0.toString(), 9, 0));

        String destination = Paths.get(tempDir, "output.txt").toString();
        FileAssembler.assembleChunks(results, destination);

        String content = Files.readString(Paths.get(destination));
        assertEquals("OnlyChunk", content);
    }

    @Test
    void testLargeChunks() throws IOException
    {
        // Test with larger chunks to verify buffer handling
        byte[] largeData1 = new byte[10000]; // 10KB
        byte[] largeData2 = new byte[10000];
        
        // Fill with recognizable pattern
        for (int i = 0; i < largeData1.length; i++)
        {
            largeData1[i] = (byte) (i % 256);
            largeData2[i] = (byte) ((i + 128) % 256);
        }

        Path chunk0 = Paths.get(tempDir, "chunk0.bin");
        Path chunk1 = Paths.get(tempDir, "chunk1.bin");
        Files.write(chunk0, largeData1);
        Files.write(chunk1, largeData2);

        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success(chunk0.toString(), 10000, 0));
        results.add(ChunkResult.success(chunk1.toString(), 10000, 1));

        String destination = Paths.get(tempDir, "output.bin").toString();
        FileAssembler.assembleChunks(results, destination);

        // Verify file size
        assertEquals(20000, Files.size(Paths.get(destination)));

        // Verify content pattern is correct
        byte[] result = Files.readAllBytes(Paths.get(destination));
        for (int i = 0; i < 10000; i++)
        {
            assertEquals((byte) (i % 256), result[i], "First chunk pattern should match");
            assertEquals((byte) ((i + 128) % 256), result[i + 10000], "Second chunk pattern should match");
        }
    }

    @Test
    void testEmptyResultsList()
    {
        List<ChunkResult> results = new ArrayList<>();
        String destination = Paths.get(tempDir, "output.txt").toString();

        // Should throw exception for empty list
        assertThrows(IOException.class, () -> {
            FileAssembler.assembleChunks(results, destination);
        });
    }

    @Test
    void testNullResultsList()
    {
        String destination = Paths.get(tempDir, "output.txt").toString();

        // Should throw exception for null list
        assertThrows(IOException.class, () -> {
            FileAssembler.assembleChunks(null, destination);
        });
    }

    @Test
    void testFailedChunkInResults() throws IOException
    {
        // Create one successful chunk
        Path chunk0 = Paths.get(tempDir, "chunk0.bin");
        Files.write(chunk0, "Hello".getBytes());

        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success(chunk0.toString(), 5, 0));
        results.add(ChunkResult.failure(new IOException("Download failed"), 1)); // Failed chunk

        String destination = Paths.get(tempDir, "output.txt").toString();

        // Should throw exception and not create output file
        assertThrows(IOException.class, () -> {
            FileAssembler.assembleChunks(results, destination);
        });

        // Output file should not be created
        assertFalse(Files.exists(Paths.get(destination)), "Output file should not exist when chunk failed");
    }

    @Test
    void testMissingChunkIndex() throws IOException
    {
        // Create chunks with indices 0 and 2, but missing index 1
        Path chunk0 = Paths.get(tempDir, "chunk0.bin");
        Path chunk2 = Paths.get(tempDir, "chunk2.bin");
        Files.write(chunk0, "First".getBytes());
        Files.write(chunk2, "Third".getBytes());

        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success(chunk0.toString(), 5, 0));
        results.add(ChunkResult.success(chunk2.toString(), 5, 2)); // Missing index 1

        String destination = Paths.get(tempDir, "output.txt").toString();

        // Should throw exception for missing chunk
        assertThrows(IOException.class, () -> {
            FileAssembler.assembleChunks(results, destination);
        });
    }

    @Test
    void testChunkFileDoesNotExist()
    {
        // Create result pointing to non-existent file
        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success("/nonexistent/chunk0.bin", 5, 0));

        String destination = Paths.get(tempDir, "output.txt").toString();

        // Should throw exception when trying to read non-existent chunk
        assertThrows(IOException.class, () -> {
            FileAssembler.assembleChunks(results, destination);
        });
    }

    @Test
    void testOverwriteExistingDestination() throws IOException
    {
        // Create existing destination file
        String destination = Paths.get(tempDir, "output.txt").toString();
        Files.write(Paths.get(destination), "OldContent".getBytes());

        // Create chunk
        Path chunk0 = Paths.get(tempDir, "chunk0.bin");
        Files.write(chunk0, "NewContent".getBytes());

        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success(chunk0.toString(), 10, 0));

        // Assemble should overwrite
        FileAssembler.assembleChunks(results, destination);

        String content = Files.readString(Paths.get(destination));
        assertEquals("NewContent", content, "Should overwrite existing file");
    }

    @Test
    void testEmptyChunks() throws IOException
    {
        // Create empty chunk files
        Path chunk0 = Paths.get(tempDir, "chunk0.bin");
        Path chunk1 = Paths.get(tempDir, "chunk1.bin");
        Files.write(chunk0, new byte[0]);
        Files.write(chunk1, "Data".getBytes());

        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success(chunk0.toString(), 0, 0));
        results.add(ChunkResult.success(chunk1.toString(), 4, 1));

        String destination = Paths.get(tempDir, "output.txt").toString();
        FileAssembler.assembleChunks(results, destination);

        String content = Files.readString(Paths.get(destination));
        assertEquals("Data", content);
    }

    @Test
    void testManyChunks() throws IOException
    {
        // Test with 10 chunks
        List<ChunkResult> results = new ArrayList<>();
        
        for (int i = 0; i < 10; i++)
        {
            Path chunk = Paths.get(tempDir, "chunk" + i + ".bin");
            String content = "Chunk" + i;
            Files.write(chunk, content.getBytes());
            results.add(ChunkResult.success(chunk.toString(), content.length(), i));
        }

        String destination = Paths.get(tempDir, "output.txt").toString();
        FileAssembler.assembleChunks(results, destination);

        String content = Files.readString(Paths.get(destination));
        assertEquals("Chunk0Chunk1Chunk2Chunk3Chunk4Chunk5Chunk6Chunk7Chunk8Chunk9", content);
    }

    @Test
    void testBinaryData() throws IOException
    {
        // Test with binary data (not just text)
        byte[] binaryData1 = {0x00, 0x01, 0x02, 0x7F, (byte) 0xFF};
        byte[] binaryData2 = {0x10, 0x20, 0x30, 0x40, 0x50};

        Path chunk0 = Paths.get(tempDir, "chunk0.bin");
        Path chunk1 = Paths.get(tempDir, "chunk1.bin");
        Files.write(chunk0, binaryData1);
        Files.write(chunk1, binaryData2);

        List<ChunkResult> results = new ArrayList<>();
        results.add(ChunkResult.success(chunk0.toString(), 5, 0));
        results.add(ChunkResult.success(chunk1.toString(), 5, 1));

        String destination = Paths.get(tempDir, "output.bin").toString();
        FileAssembler.assembleChunks(results, destination);

        byte[] result = Files.readAllBytes(Paths.get(destination));
        assertEquals(10, result.length);
        
        // Verify exact binary content
        assertArrayEquals(binaryData1, java.util.Arrays.copyOfRange(result, 0, 5));
        assertArrayEquals(binaryData2, java.util.Arrays.copyOfRange(result, 5, 10));
    }
}

/* ============================================================
   RUNNING TESTS
   ============================================================

Run all FileAssembler tests:
   mvn test -Dtest=FileAssemblerTest

Run specific test:
   mvn test -Dtest=FileAssemblerTest#testSuccessfulAssembly

Run all tests except ChunkDownloader:
   mvn test -Dtest=!ChunkDownloaderTest

Run both test classes:
   mvn test -Dtest=FileAssemblerTest,ChunkDownloaderTest

============================================================ */