package com.diary.exception;

import java.time.Instant;

public class VersionConflictException extends RuntimeException {

    private final int serverVersion;
    private final Instant serverUpdatedAt;

    public VersionConflictException(int serverVersion, Instant serverUpdatedAt) {
        super("版本冲突，数据已被修改");
        this.serverVersion = serverVersion;
        this.serverUpdatedAt = serverUpdatedAt;
    }

    public int getServerVersion() { return serverVersion; }
    public Instant getServerUpdatedAt() { return serverUpdatedAt; }
}
