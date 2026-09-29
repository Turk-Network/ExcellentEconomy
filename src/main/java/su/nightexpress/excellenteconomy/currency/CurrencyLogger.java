package su.nightexpress.excellenteconomy.currency;

import org.jetbrains.annotations.NotNull;

import su.nightexpress.excellenteconomy.EconomyPlugin;
import su.nightexpress.excellenteconomy.api.currency.operation.NotificationTarget;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationContext;
import su.nightexpress.nightcore.util.TimeUtil;
import su.nightexpress.nightcore.util.text.night.NightMessage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CurrencyLogger {

    private final EconomyPlugin           plugin;
    private final BlockingQueue<LogEntry> queue;
    private final DateTimeFormatter       formatter;

    private final boolean logToConsole;
    private final boolean logToFile;

    private BufferedWriter writer;
    private volatile boolean running;

    public CurrencyLogger(@NotNull EconomyPlugin plugin,
                          @NotNull DateTimeFormatter formatter,
                          @NotNull Path filePath,
                          boolean logToConsole,
                          boolean logToFile) throws IOException {
        this.plugin = plugin;
        this.formatter = formatter;
        this.logToConsole = logToConsole;
        this.logToFile = logToFile;
        this.queue = new LinkedBlockingQueue<>();

        if (logToFile) {
            this.writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
            this.running = true;
        }
    }

    private record LogEntry(@NotNull String log, long timestamp) {
    }

    public void shutdown() {
        // Stop the periodic writer first, so waiting for its monitor never blocks on a long queue.
        this.running = false;

        synchronized (this) {
            if (this.writer != null) {
                try {
                    // Persist operations queued since the last write interval instead of dropping them.
                    LogEntry entry;
                    while ((entry = this.queue.poll()) != null) {
                        this.append(entry);
                    }
                    this.writer.flush();
                }
                catch (IOException exception) {
                    exception.printStackTrace();
                }
                finally {
                    try {
                        this.writer.close();
                    }
                    catch (IOException exception) {
                        exception.printStackTrace();
                    }
                }
            }
            this.queue.clear();
        }
    }

    public void addEntry(@NotNull OperationContext context, @NotNull String log) {
        String stripped = NightMessage.stripTags(log);

        if (this.logToConsole && context.shouldNotify(NotificationTarget.CONSOLE_LOGGER)) {
            this.plugin.info(stripped);
        }
        if (this.running && this.logToFile && context.shouldNotify(NotificationTarget.FILE_LOGGER)) {
            this.queue.add(new LogEntry(stripped, System.currentTimeMillis()));
        }
    }

    public synchronized void write() {
        try {
            while (this.running && !this.queue.isEmpty()) {
                LogEntry result = this.queue.poll(500, TimeUnit.MILLISECONDS);
                if (result != null) {
                    this.append(result);
                    this.writer.flush();
                }
            }
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void append(@NotNull LogEntry entry) throws IOException {
        String date = TimeUtil.getLocalDateTimeOf(entry.timestamp()).format(this.formatter);
        this.writer.append("[").append(date).append("] ").append(entry.log());
        this.writer.newLine();
    }
}
