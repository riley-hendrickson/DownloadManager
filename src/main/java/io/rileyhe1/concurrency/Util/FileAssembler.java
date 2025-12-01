package io.rileyhe1.concurrency.Util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.rileyhe1.concurrency.Data.ChunkResult;

// @SuppressWarnings("unused")
public class FileAssembler
{
    private final int BUFFER_SIZE = 8192;

    public void assembleChunks(List<ChunkResult> results, String destination) throws IOException
    {
        // sort by chunk index for easier assembly
        results = sortChunks(results);
        // validate all chunks were successful and that we have all chunks
        // compiled
        validateChunks(results);
        // generate list of each chunk's associated tempFile location
        List<String> tempFiles = new ArrayList<>();
        for (int i = 0; i < results.size(); i++)
        {
            tempFiles.add(i, results.get(i).getTempFilePath());
        }
        // merge chunks to destination file
        mergeFiles(tempFiles, destination);
        // clean up and delete chunk temp directories
        cleanupTempFiles(tempFiles);
    }

    private void validateChunks(List<ChunkResult> results) throws IOException
    {
        if (results == null || results.isEmpty())
            throw new IOException("Results list is null/contains no chunks");
        for (int i = 0; i < results.size(); i++)
        {
            ChunkResult current = results.get(i);
            if (current.getChunkIndex() != i)
            {
                throw new IOException("Expected Chunk Index did not match, there may be missing chunks. Expected: " + i
                        + ", Actual: " + current.getChunkIndex());
            }
            if (!current.isSuccessful())
                throw new IOException("Not all chunks succeeded: Chunk " + current.getChunkIndex() + " failed.");
        }
    }

    private List<ChunkResult> sortChunks(List<ChunkResult> results)
    {
        List<ChunkResult> sorted = new ArrayList<>();
        for (int i = 0; i < results.size(); i++)
        {
            sorted.add(null);
            ChunkResult cur = results.get(i);
            sorted.set(cur.getChunkIndex(), cur);
        }
        return sorted;
    }

    private void mergeFiles(List<String> tempFiles, String output) throws IOException
    {
        // open destination file, if it already exists its contents will be overwritten
        try (FileOutputStream outputStream = new FileOutputStream(output)) 
        {
            // process each chunk in order
            for(String tempFile : tempFiles)
            {
                // open current chunk for reading
                try(FileInputStream inputStream = new FileInputStream(tempFile))
                {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    // read up to BUFFER_SIZE bytes from current chunk and write them to the output file stream, until everything is copied over
                    while((bytesRead = inputStream.read(buffer)) != -1)
                    {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                // current chunk closes here because we opened via try with resources block
            }
        }
        // output file closes here because we opened via try with resources block
    }

    private void cleanupTempFiles(List<String> tempFiles) throws IOException
    {
        for(String tempFile : tempFiles)
        {
            Files.deleteIfExists(Paths.get(tempFile));
        }
    }
}
