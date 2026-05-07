package com.example.parallelisme.web;

import com.example.parallelisme.benchmark.StepByStepDifferentParallelismsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/stepByStep")
@RequiredArgsConstructor
public class StepByStepController {

    private final StepByStepDifferentParallelismsService stepByStepDifferentParallelismsService;

    @GetMapping("/availableProc")
    public int availableProc() {
        return Runtime.getRuntime().availableProcessors();
    }

    @GetMapping("/java1")
    public void java1() {
        stepByStepDifferentParallelismsService.useCaseJava1();
    }

    @GetMapping("/java1VT")
    public void java1VT() {
        stepByStepDifferentParallelismsService.useCaseJava1WithVT();
    }

    @GetMapping("/java5")
    public void java5Runnable() {
        stepByStepDifferentParallelismsService.useCaseJava5Runnable();
    }

    @GetMapping("/java5-callable")
    public void java5Callable() {
        stepByStepDifferentParallelismsService.useCaseJava5Callable();
    }
}
