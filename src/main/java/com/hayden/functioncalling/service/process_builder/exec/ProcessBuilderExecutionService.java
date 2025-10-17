package com.hayden.functioncalling.service.process_builder.exec;

import com.hayden.functioncalling.service.process_builder.ProcessExecutionRequest;
import com.hayden.functioncalling.service.process_builder.ProcessExecutionResult;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderExecutionService {

    private final ExecutorService runnerTaskExecutor;

    public ProcessExecutionResult executeProcess(ProcessExecutionRequest request) throws IOException, InterruptedException {
        return executeProcessWithPatternWait(request, false, true);
    }

    public ProcessExecutionResult executeProcessWithPatternWait(ProcessExecutionRequest request) throws IOException, InterruptedException {
        return executeProcessWithPatternWait(request, true, true);
    }

    public ProcessExecutionResult executeProcessWithPatternWait(ProcessExecutionRequest request,
                                                                boolean stopEarlyIfFailureDetected,
                                                                boolean isStrictSuccessPatterns) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        List<String> commandParts = buildCommandParts(request.getCommand(), request.getArguments());
        log.info("Executing command with pattern wait: {}", String.join(" ", commandParts));

        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);

        if (StringUtils.isNotBlank(request.getWorkingDirectory())) {
            processBuilder.directory(new File(request.getWorkingDirectory()));
        }

        Process process = processBuilder.start();

        try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            PatternsChecked checkPatterns = new PatternsChecked();

            String error = null;

            ArrayBlockingQueue<NextLog> matchedLogs = new ArrayBlockingQueue<>(1024);

            LogConsumer outputConsumer = new LogConsumer(matchedLogs);
            LogConsumer errorConsumer = new LogConsumer(matchedLogs);
            var outputThread = execThread(request, reader, new LogAppender.LineAppender(), outputConsumer);
            var errorThread = execThread(request, errorReader, new LogAppender.ErrorAppender(), errorConsumer);

            CompletableFuture<Void> outputFuture = CompletableFuture.runAsync(outputThread, runnerTaskExecutor);
            CompletableFuture<Void> errorFuture = CompletableFuture.runAsync(errorThread, runnerTaskExecutor);

            int maxWaitSeconds = request.numWaitSeconds();

            long endTime = System.currentTimeMillis() + (maxWaitSeconds * 1000L);

            List<NextLog> nextValues = new ArrayList<>();

            List<NextLog> fullLog = new ArrayList<>();

            while (System.currentTimeMillis() < endTime && process.isAlive() && checkPatterns.isNotComplete()) {

                var next = matchedLogs.poll(500, TimeUnit.MILLISECONDS);
                if (next != null)
                    nextValues.add(next);

                matchedLogs.drainTo(nextValues);

                if (!nextValues.isEmpty()) {
                    fullLog.addAll(nextValues);
                }

                checkPatterns = checkPatterns.doCheckPatterns(request, nextValues);

                nextValues.clear();

                if (stopEarlyIfFailureDetected && checkPatterns.failureFound()) {
                    error = "Failure pattern detected in output: %s".formatted(checkPatterns.failure);
                    break;
                }
            }

            try {
                outputFuture.get(1, TimeUnit.SECONDS);
                errorFuture.get(1, TimeUnit.SECONDS);
            }  catch (ExecutionException | TimeoutException e) {
                log.warn("Interrupted while waiting for output thread to complete", e);
                waitDestroyProcess(process, Duration.between(Instant.now(), Instant.ofEpochMilli(endTime)).getSeconds());
                outputFuture.cancel(true);
                errorFuture.cancel(true);
            }

            if (!matchedLogs.isEmpty()) {
                matchedLogs.drainTo(nextValues);

                if (!nextValues.isEmpty()) {
                    fullLog.addAll(nextValues);
                }

                checkPatterns = checkPatterns.doCheckPatterns(request, nextValues);

                nextValues.clear();
            }

            if (checkPatterns.isNotComplete()) {
                checkPatterns = checkPatterns.doCheckPatterns(request, fullLog);
            }

            int exitCode = 0;

            try {
                exitCode = !process.isAlive() ? process.exitValue() : process.onExit().get().exitValue();
            } catch (ExecutionException e) {
                log.error("Error while executing process", e);
                exitCode = 1;
            }

            int executionTimeMs = (int)(System.currentTimeMillis() - startTime);
            boolean success;

            if (checkPatterns.failureFound()) {
                if (checkPatterns.patternFound()) {
                    error = "Process completed but found some failures.";
                } else {
                    error = "Failure pattern found in input.";
                }
                success = false;
            }  else if (checkPatterns.patternFound()) {
                success = true;
            } else if (exitCode == 0 && CollectionUtils.isEmpty(request.getSuccessPatterns())) {
                success = true;
            } else if (!checkPatterns.isNotComplete() && !process.isAlive()) {
                if (exitCode != 0) {
                    error = "Process exited with non-zero status: " + exitCode;
                    success = false;
                } else if (isStrictSuccessPatterns && CollectionUtils.isNotEmpty(request.getSuccessPatterns())
                        && CollectionUtils.isEmpty(checkPatterns.pattern)) {
                    error = "Process completed but success pattern not found in output";
                    success = false;
                } else {
                    success = true;
                }
            } else {
                error = "Pattern wait timed out after " + maxWaitSeconds + " seconds";
                success = checkPatterns.isSuccess();
            }

            if (request.getOutputFile() != null && error != null) {
                writeToFile(request.getOutputFile(), "ERROR: " + error);
            } else if (error != null) {
                checkPatterns.failure.add(error);
            }

            boolean didWriteToFile = request.getOutputFile() != null && request.getOutputFile().exists();

            String matchedOutput = String.join("\n", checkPatterns.pattern);

            String allLogs = fullLog.stream()
                    .filter(Objects::nonNull)
                    .map(nl -> nl.isErr ? "ERROR: " + nl.log : nl.log)
                    .collect(Collectors.joining(System.lineSeparator()));

            if (outputConsumer.droppedLines() != 0 || errorConsumer.droppedLines() != 0) {
                checkPatterns.failure.add("Dropped %s output lines and %s error lines while processing."
                        .formatted(outputConsumer.droppedLines(), errorConsumer.droppedLines()));
            }

            return ProcessExecutionResult.builder()
                    .success(success)
                    .matchedOutput(CollectionUtils.isEmpty(request.getOutputRegex()) ? allLogs : matchedOutput)
                    .fullLog(allLogs)
                    .error(String.join("\n", checkPatterns.failure))
                    .didWriteToFile(didWriteToFile)
                    .exitCode(exitCode)
                    .executionTimeMs(executionTimeMs)
                    .logPath(Optional.ofNullable(request.getOutputFile()).map(File::toPath).orElse(null))
                    .process(process)
                    .build();
        }

    }

    private record PatternsChecked(List<String> pattern, List<String> failure) {

        public PatternsChecked() {
            this(new ArrayList<>(), new ArrayList<>());
        }

        boolean isComplete() {
            return patternFound() || failureFound();
        }

        boolean isNotComplete() {
            return !patternFound() && !failureFound();
        }

        boolean failureFound() {
            return CollectionUtils.isNotEmpty(failure);
        }

        boolean patternFound() {
            return CollectionUtils.isNotEmpty(pattern);
        }

        boolean isSuccess() {
            return patternFound() && !failureFound();
        }

        private PatternsChecked doCheckPatterns(ProcessExecutionRequest request, List<NextLog> nextLog) {
            // Check for success patterns

            if (CollectionUtils.isNotEmpty(request.getSuccessPatterns())) {
                var patterns = request.getSuccessPatterns().stream().map(Pattern::compile).toList();

                var succ = nextLog.stream()
                        .filter(Objects::nonNull)
                        .map(nl -> nl.log)
                        .filter(st -> patterns.stream().anyMatch(p -> p.matcher(st).matches()))
                        .toList();

                this.pattern.addAll(succ);
            }

            if (CollectionUtils.isNotEmpty(request.getFailurePatterns())) {

                var patterns = request.getFailurePatterns().stream().map(Pattern::compile).toList();

                var errs = nextLog.stream()
                        .filter(Objects::nonNull)
                        .map(nl -> nl.log)
                        .filter(st -> patterns.stream().anyMatch(p -> p.matcher(st).matches()))
                        .toList();

                this.failure.addAll(errs);
            }

            return new PatternsChecked(this.pattern, this.failure);
        }
    }

    record NextLog(boolean isErr, String log) {
    }

    @Slf4j
    record LogConsumer(ArrayBlockingQueue<NextLog> matchedLogs,
                       AtomicInteger numDropped)  {

        public int droppedLines() {
            return numDropped.get();
        }

        public LogConsumer(ArrayBlockingQueue<NextLog> matchedLogs) {
            this(matchedLogs, new  AtomicInteger(0));
        }

        public void append(String log) {
            doOffer(false, log);
        }

        private void doOffer(boolean isErr, String nextLog) {
            if(matchedLogs.offer(new NextLog(isErr, nextLog))) {
                numDropped.incrementAndGet();
            }
        }

        public void appendErr(String nextLog) {
            doOffer(true, nextLog);
        }
    }

    interface LogAppender {

        void append(ProcessExecutionRequest request,
                    String line,
                    LogConsumer fullLog);

        record LineAppender() implements LogAppender {
            @Override
            public void append(ProcessExecutionRequest request, String line, LogConsumer fullLog) {
                String nextLog = line;
                if (request.getOutputRegex() != null && !request.getOutputRegex().isEmpty()) {
                    if (request.getOutputRegex().stream().anyMatch(nextLog::matches)) {
                        fullLog.append(nextLog);
                    }
                } else {
                    if (request.getOutputFile() == null)
                        fullLog.append(nextLog);
                    else
                        writeToFile(request.getOutputFile(), nextLog);
                }
            }
        }

        record ErrorAppender() implements LogAppender {
            @Override
            public void append(ProcessExecutionRequest request, String line, LogConsumer fullLog) {
                final String nextErr = line;
                if (request.getOutputRegex() != null && !request.getOutputRegex().isEmpty()) {
                    if (request.getOutputRegex().stream().anyMatch(nextErr::matches)) {
                        fullLog.appendErr(nextErr);
                    }
                } else {
                    if (request.getErrorFile() == null)
                        fullLog.appendErr(nextErr);
                    else
                        writeToFile(request.getOutputFile(), "ERROR: " + nextErr);
                }
            }
        }

    }

    private @NotNull Runnable execThread(ProcessExecutionRequest request,
                                         BufferedReader logReader,
                                         LogAppender fullLog,
                                         LogConsumer logConsumer) {
        return () -> {
            try {
                String line;
                while ((line = logReader.readLine()) != null) {
                    fullLog.append(request, line, logConsumer);
                }
            } catch (IOException e) {
                log.error("Error reading process output", e);
            }
        };
    }

    private List<String> buildCommandParts(String command, String arguments) {
        List<String> commandParts = new ArrayList<>(Arrays.asList(command.split("\\s+")));

        if (StringUtils.isNotBlank(arguments)) {
            commandParts.addAll(Arrays.asList(arguments.split("\\s+")));
        }

        return commandParts;
    }

    private boolean waitDestroyProcess(Process process, Long timeoutSeconds) throws InterruptedException {
        if (timeoutSeconds != null && timeoutSeconds > 0L) {
            return process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } else {
            process.destroy();
            return false;
        }
    }

    private static void writeToFile(File outputFile, String content) {
        try {
            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(outputFile, true)) {
                writer.write(content + System.lineSeparator());
            }
        } catch (IOException e) {
            log.error("Error writing to file: {}", outputFile.getAbsolutePath(), e);
        }
    }

}
