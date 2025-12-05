package io.rileyhe1.concurrency.Data;

public class DownloadConfig
{
    private final int numberOfThreads;
    private final long chunkSize;
    private final int connectionTimeout;
    private final int readTimeout;
    private final int maxRetries;
    private final int retryDelayMS;
    private final String tempDirectory;
    private final int bufferSize;
    private final long minSizeForChunking;

    public DownloadConfig(Builder builder)
    {
        this.numberOfThreads = builder.numberOfThreads;
        this.chunkSize = builder.chunkSize;
        this.connectionTimeout = builder.connectionTimeout;
        this.readTimeout = builder.readTimeout;
        this.maxRetries = builder.maxRetries;
        this.retryDelayMS = builder.retryDelayMS;
        this.tempDirectory = builder.tempDirectory;
        this.bufferSize = builder.bufferSize;
        this.minSizeForChunking = builder.minSizeForChunking;
    }

    public int getNumberOfThreads()
    {
        return numberOfThreads;
    }

    public long getChunkSize()
    {
        return chunkSize;
    }

    public int getConnectionTimeout()
    {
        return connectionTimeout;
    }

    public int getMaxRetries()
    {
        return maxRetries;
    }

    public String getTempDirectory()
    {
        return tempDirectory;
    }

    public int getReadTimeout()
    {
        return readTimeout;
    }

    public int getRetryDelayMS()
    {
        return retryDelayMS;
    }

    public int getBufferSize()
    {
        return bufferSize;
    }

    public long getMinSizeForChunking()
    {
        return minSizeForChunking;
    }

    /**
     * Creates a new builder with default values
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Creates a builder pre-populated with default values
     */
    public static Builder defaultConfig()
    {
        return new Builder();
    }

    public static class Builder
    {
        private int numberOfThreads = 16;
        private long chunkSize = 5 * 1024 * 1024; // 5 MB
        private int connectionTimeout = 30000; // 30 seconds
        private int readTimeout = 30000; // 30 seconds
        private int maxRetries = 3;
        private int retryDelayMS = 2000; // 2 seconds
        private String tempDirectory = System.getProperty("java.io.tmpdir");
        private int bufferSize = 8192; // 8 KB
        private long minSizeForChunking = 1024 * 1024; // 1 MB

        public Builder numberOfThreads(int numberOfThreads)
        {
            if (numberOfThreads < 1)
            {
                throw new IllegalArgumentException("Number of threads must be at least 1");
            }
            this.numberOfThreads = numberOfThreads;
            return this;
        }

        public Builder chunkSize(long chunkSize)
        {
            if (chunkSize < 1024)
            {
                throw new IllegalArgumentException("Chunk size must be at least 1 KB");
            }
            this.chunkSize = chunkSize;
            return this;
        }

        public Builder connectionTimeout(int connectionTimeout)
        {
            if (connectionTimeout < 0)
            {
                throw new IllegalArgumentException("Connection timeout cannot be negative");
            }
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder readTimeout(int readTimeout)
        {
            if (readTimeout < 0)
            {
                throw new IllegalArgumentException("Read timeout cannot be negative");
            }
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder maxRetries(int maxRetries)
        {
            if (maxRetries < 0)
            {
                throw new IllegalArgumentException("Max retries cannot be negative");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryDelayMS(int retryDelayMS)
        {
            if (retryDelayMS < 0)
            {
                throw new IllegalArgumentException("Retry delay cannot be negative");
            }
            this.retryDelayMS = retryDelayMS;
            return this;
        }

        public Builder tempDirectory(String tempDirectory)
        {
            if (tempDirectory == null || tempDirectory.trim().isEmpty())
            {
                throw new IllegalArgumentException("Temp directory cannot be null or empty");
            }
            this.tempDirectory = tempDirectory;
            return this;
        }

        public Builder bufferSize(int bufferSize)
        {
            if (bufferSize < 1024)
            {
                throw new IllegalArgumentException("Buffer size must be at least 1 KB");
            }
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder minSizeForChunking(long minSizeForChunking)
        {
            if (minSizeForChunking < 0)
            {
                throw new IllegalArgumentException("Min size for chunking cannot be negative");
            }
            this.minSizeForChunking = minSizeForChunking;
            return this;
        }

        /**
         * Convenience method to set chunk size in megabytes
         */
        public Builder chunkSizeMB(int megabytes)
        {
            return chunkSize(megabytes * 1024L * 1024L);
        }

        /**
         * Convenience method to set timeouts in seconds
         */
        public Builder timeoutsInSeconds(int seconds)
        {
            int milliseconds = seconds * 1000;
            this.connectionTimeout = milliseconds;
            this.readTimeout = milliseconds;
            return this;
        }

        public DownloadConfig build()
        {
            return new DownloadConfig(this);
        }
    }
}
