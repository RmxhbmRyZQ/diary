package com.diary.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class UpdateRateLimitRequest {

    @NotBlank(message = "端点名称不能为空")
    private String endpoint;

    @Min(value = 1, message = "限流阈值至少为1")
    private int limit;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
}
