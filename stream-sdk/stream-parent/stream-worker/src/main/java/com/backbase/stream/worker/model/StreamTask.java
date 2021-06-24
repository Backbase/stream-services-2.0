package com.backbase.stream.worker.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.SpanTag;

@Data
@NoArgsConstructor
@Slf4j
public abstract class StreamTask {

    private String id;

    private OffsetDateTime registeredAt;
    private OffsetDateTime finishedAt;
    private State state;

    // Temporary until
    private List<TaskHistory> history = new ArrayList<>();

    public StreamTask(String id) {
        this.id = id;
    }

    public void info(String entity, String operation, String result, String externalId, String internalId,
                     String message, Object... messageArgs) {
        addHistory(entity, operation, result, externalId, internalId, String.format(message, messageArgs),
            TaskHistory.Severity.INFO, null, null);
    }

    public void warn(String entity, String operation, String result, String externalId, String internalId,
                     String message, Object... messageArgs) {
        addHistory(entity, operation, result, externalId, internalId, String.format(message, messageArgs),
            TaskHistory.Severity.WARN, null, null);
    }

    public void error(String entity, String operation, String result, String externalId, String internalId,
                      String message, Object... messageArgs) {
        addHistory(entity, operation, result, externalId, internalId, String.format(message, messageArgs),
            TaskHistory.Severity.ERROR, null, null);
    }

    public void error(String entity, String operation, String result, String externalId, String internalId,
                      Throwable throwable, String errorMessage, String message, Object... messageArgs) {
        addHistory(entity, operation, result, externalId, internalId, String.format(message, messageArgs),
            TaskHistory.Severity.ERROR, throwable, errorMessage);
    }

    @ContinueSpan
    public void addHistory(
        @SpanTag("entity") String entity,
        @SpanTag("operation") String operation,
        @SpanTag("result") String result,
        @SpanTag("externalId") String externalId,
        @SpanTag("internalId") String internalId,
        @SpanTag("message") String message,
        @SpanTag("severity") TaskHistory.Severity severity,
        @SpanTag("severity") Throwable throwable,
        @SpanTag("severity") String errorMessage) {
        TaskHistory taskHistory = new TaskHistory();
        taskHistory.setTimestamp(OffsetDateTime.now());
        taskHistory.setEntity(entity);
        taskHistory.setOperation(operation);
        taskHistory.setResult(result);
        taskHistory.setExternalId(externalId);
        taskHistory.setInternalId(internalId);
        taskHistory.setMessage(message);
        taskHistory.setSeverity(severity);
        taskHistory.setErrorMessage(errorMessage);
        if (throwable != null && errorMessage == null) {
            taskHistory.setErrorMessage(throwable.getMessage());
        }
        history.add(taskHistory);
    }

    public StreamTask addHistory(List<TaskHistory> history) {
        this.history.addAll(history);
        return this;
    }

    public boolean isCompleted() {
        return this.state == State.COMPLETED;
    }

    public boolean isFailed() {
        return this.state == State.FAILED;
    }

    public abstract String getName();

    public enum State {
        ACCEPTED,
        IN_PROGRESS,
        FAILED,
        COMPLETED
    }

    public void logSummary(Logger log) {
        log.info("\n\n" +
                "Stream Task: {}\n" +
                "Status: {}\n\n" +
                "History:\n{}\n",
            this.getId(),
            this.getState(), "\t" + this.getHistory().stream().map(TaskHistory::toDisplayString)
                .collect(Collectors.joining("\n\t")));
    }

    public void logSummary() {
        this.logSummary(log);
    }


}
