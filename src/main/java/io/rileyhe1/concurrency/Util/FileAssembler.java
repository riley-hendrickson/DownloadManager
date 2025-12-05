package io.rileyhe1.concurrency.Util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.rileyhe1.concurrency.Data.ChunkResult;

// @SuppressWarnings("unused")
public class FileAssembler
{
    private final static int BUFFER_SIZE = 8192;

    public static void assembleChunks(List<ChunkResult> results, String destination) throws IOException
    {
        // validate all chunks were successful and that we have all chunks compiled
        validateChunks(results);
        // sort by chunk index for easier assembly
        results = sortChunks(results);
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

    private static void validateChunks(List<ChunkResult> results) throws IOException
    {
        if (results == null || results.isEmpty()) throw new IOException("Results list is null/contains no chunks");
        int largestIndex = 0;
        Set<Integer> seenIndices = new HashSet<>();
        for(ChunkResult result : results)
        {
            largestIndex = largestIndex > result.getChunkIndex() ? largestIndex : result.getChunkIndex();
            seenIndices.add(result.getChunkIndex());
        }
        for (int i = 0; i <= largestIndex; i++)
        {
            if (!seenIndices.contains(i))
            {
                throw new IOException("Cannot assemble chunks, chunk " + i + " is missing");
            }
            ChunkResult current = results.get(i);
            if (!current.isSuccessful())
                throw new IOException("Not all chunks succeeded: Chunk " + current.getChunkIndex() + " failed.");
        }
    }

    private static List<ChunkResult> sortChunks(List<ChunkResult> results)
    {
        List<ChunkResult> sorted = new ArrayList<>();
        for (int i = 0; i < results.size(); i++)
        {
            sorted.add(null);
        }
        for(ChunkResult result : results)
        {
            sorted.set(result.getChunkIndex(), result);
        }
        return sorted;
    }

    private static void mergeFiles(List<String> tempFiles, String output) throws IOException
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

    public static void cleanupTempFiles(List<String> tempFiles) throws IOException
    {
        for(String tempFile : tempFiles)
        {
            Files.deleteIfExists(Paths.get(tempFile));
        }
    }
}
