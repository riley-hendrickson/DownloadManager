package io.rileyhe1.concurrency.Util;

import java.io.IOException;
import java.util.List;

import io.rileyhe1.concurrency.Data.ChunkResult;

@SuppressWarnings("unused")
public class FileAssembler
{
    public void assembleChunks(List<ChunkResult> results, String destination) throws IOException
    {
        validateChunks(results);

    }

    private void validateChunks(List<ChunkResult> results) throws IOException
    {
        if(results == null || results.isEmpty()) throw new IOException("Results list is null/contains no chunks");
        for(int i = 0; i < results.size(); i++)
        {
            ChunkResult current = results.get(i);
        }
    }

    private List<ChunkResult> sortChunks(List<ChunkResult> results)
    {
        return results;
    }

    private void mergeFiles(List<String> tempFiles, String output)
    {

    }

    private void cleanupTempFiles(List<String> tempFiles)
    {

    }
}
