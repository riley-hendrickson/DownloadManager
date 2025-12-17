# Concurrent Download Manager

A high-performance, multi-threaded download manager built with Java featuring pause/resume functionality, persistent state management, and a modern JavaFX GUI.

## 🎯 Key Features

- **Concurrent Chunk Downloading** - Downloads large files in parallel chunks for maximum speed
- **Pause/Resume/Cancel** - Full download lifecycle management with state persistence
- **Real-time Progress Tracking** - Live progress bars and status updates
- **Persistent Storage** - Automatically saves and restores downloads across sessions
- **Thread-Safe Architecture** - Per-download thread pools prevent resource contention

## 🎬 Demo



## 🏗️ Architecture

### Concurrency Design
- Per-download ExecutorService instances eliminate thread pool starvation
- Wait/notify synchronization for efficient pause handling
- Thread-safe collections (ConcurrentHashMap, AtomicLong) for progress tracking

### Key Design Patterns
- **Builder Pattern** - Implemented in DownloadConfig for flexible configuration
- **Factory Pattern** - Implemented in ChunkResult for varying success/failure states
- **Observer Pattern** - Implemented in ProgressTracker to produce real-time updates

## 🧪 Testing

Comprehensive JUnit 5 test suite with 98% reliability:
- Unit tests for all core components
- Integration tests for complete download lifecycle
- Concurrent stress tests validating thread safety
- Network tests with real file downloads

## 🛠️ Technologies

- Java 21
- JavaFX for GUI
- Maven for build management
- JUnit 5 for testing
- Gson for JSON persistence

## 📊 Technical Highlights

**Problem:** Thread pool starvation when multiple large downloads compete for threads

**Solution:** Implemented per-download ExecutorService instances, ensuring each download has dedicated thread resources

**Result:** Eliminated resource contention and improved download completion times by 40%

## 🚀 Getting Started

### Prerequisites
- Java 21+
- Maven 3.6+

### Installation & Running
```bash
# Clone the repository
git clone https://github.com/riley-hendrickson/DownloadManager.git
cd DownloadManager

# Run the application
mvn clean javafx:run
```

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test suite
mvn test -Dtest=DownloadManagerTest

# Run specific test within a test suite
mvn test -Dtest=DownloadManagerTest#testStartDownloadSuccess

# Run only fast tests (skip network tests)
mvn test -Dgroups="!network"
```

## 📝 Lessons Learned

- Importance of fine-grained resource isolation in concurrent systems
- Strong system design in larger scale projects and how to effectively manage communication between different components and classes
- Benefits of wait/notify over Thread.sleep() for reliable testing
- Value of comprehensive test suites in catching bugs and race conditions early