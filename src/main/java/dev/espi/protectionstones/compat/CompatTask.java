package dev.espi.protectionstones.compat;

public interface CompatTask {
    void cancel();

    boolean isCancelled();
}
