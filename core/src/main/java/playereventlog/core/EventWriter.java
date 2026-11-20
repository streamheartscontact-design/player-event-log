package playereventlog.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class EventWriter {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int QUEUE_LIMIT = 20000;

    private final File file;
    private final long maxBytes;
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>(QUEUE_LIMIT);
    private final Thread thread;
    private volatile boolean running = true;
    private volatile long dropped;
    private OutputStream out;
    private long size;

    public EventWriter(File file, long maxBytes) {
        this.file = file;
        this.maxBytes = Math.max(4096, maxBytes);
        this.thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "PlayerEventLog-writer");
        this.thread.setDaemon(true);
        this.thread.setPriority(Thread.MIN_PRIORITY);
        this.thread.start();
    }

    public File file() {
        return file;
    }

    public void offer(String line) {
        if (!running) return;
        if (queue.offer(line)) offered.incrementAndGet();
        else dropped++;
    }

    public long dropped() {
        return dropped;
    }

    public void close(long waitMillis) {
        running = false;
        thread.interrupt();
        try {
            thread.join(waitMillis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public void flush(long waitMillis) {
        long end = System.currentTimeMillis() + waitMillis;
        while (System.currentTimeMillis() < end) {
            if (processed >= offered.get()) return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private final java.util.concurrent.atomic.AtomicLong offered = new java.util.concurrent.atomic.AtomicLong();
    private volatile long processed;

    private void loop() {
        List<String> batch = new ArrayList<String>(256);
        while (running || !queue.isEmpty()) {
            try {
                String first = running ? queue.poll(500, TimeUnit.MILLISECONDS) : queue.poll();
                if (first == null) {
                    if (!running) break;
                    continue;
                }
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, 1024);
                try {
                    write(batch);
                } finally {
                    processed += batch.size();
                }
            } catch (InterruptedException e) {
            } catch (Throwable t) {
                System.err.println("[PlayerEventLog] write failed: " + t);
                closeStream();
                sleepQuietly(1000);
            }
        }
        List<String> rest = new ArrayList<String>();
        queue.drainTo(rest);
        if (!rest.isEmpty()) {
            try {
                write(rest);
            } catch (Throwable t) {
                System.err.println("[PlayerEventLog] final write failed: " + t);
            }
        }
        processed += rest.size();
        closeStream();
    }

    private void write(List<String> lines) throws IOException {
        for (String line : lines) {
            byte[] bytes = (line + "\n").getBytes(UTF8);
            ensureOpen();
            if (size > 0 && size + bytes.length > maxBytes) rotate();
            out.write(bytes);
            size += bytes.length;
        }
        out.flush();
    }

    private void ensureOpen() throws IOException {
        if (out != null) return;
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        size = file.isFile() ? file.length() : 0;
        out = new FileOutputStream(file, true);
    }

    private void rotate() throws IOException {
        closeStream();
        File old = new File(file.getPath() + ".1");
        if (old.exists() && !old.delete()) throw new IOException("cannot replace " + old);
        if (!file.renameTo(old)) throw new IOException("cannot rotate " + file);
        ensureOpen();
    }

    private void closeStream() {
        if (out != null) {
            try {
                out.flush();
                out.close();
            } catch (IOException ignored) {
            }
            out = null;
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
