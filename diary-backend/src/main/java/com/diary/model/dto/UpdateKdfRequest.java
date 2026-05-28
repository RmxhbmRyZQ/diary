package com.diary.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class UpdateKdfRequest {

    @NotBlank(message = "算法名称不能为空")
    private String algorithm;

    @Min(value = 100000, message = "迭代次数不能低于100000")
    private int iterations;

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }
}
