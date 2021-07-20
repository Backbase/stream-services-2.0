package com.backbase.stream.transaction;

import com.backbase.dbs.transaction.api.service.v2.TransactionPresentationServiceApi;
import com.backbase.dbs.transaction.api.service.v2.model.TransactionsPostRequestBody;
import com.backbase.dbs.transaction.api.service.v2.model.TransactionsPostResponseBody;
import com.backbase.stream.worker.StreamTaskExecutor;
import com.backbase.stream.worker.exception.StreamTaskException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
public class TransactionTaskExecutor implements StreamTaskExecutor<TransactionTask> {

    private final TransactionPresentationServiceApi transactionPresentationServiceApi;

    public TransactionTaskExecutor(TransactionPresentationServiceApi transactionPresentationServiceApi) {
        this.transactionPresentationServiceApi = transactionPresentationServiceApi;
    }

    @Override
    public Mono<TransactionTask> executeTask(TransactionTask streamTask) {
        List<TransactionsPostRequestBody> data = streamTask.getData();
        String externalIds = streamTask.getData().stream().map(TransactionsPostRequestBody::getExternalId)
            .collect(Collectors.joining(","));
        log.info("Post {} transactions: ", data.size());
        return transactionPresentationServiceApi.postTransactions(data, null)
            .onErrorResume(WebClientResponseException.class, throwable -> {
                streamTask.error("transactions", "post", "failed", externalIds, null, throwable,
                    throwable.getResponseBodyAsString(), "Failed to ingest transactions");
                return Mono.error(new StreamTaskException(streamTask, throwable,
                    "Failed to Ingest Transactions: " + throwable.getResponseBodyAsString()));
            })
            .collectList()
            .map(transactionIds -> {
                streamTask.error("transactions", "post", "success", externalIds, transactionIds.stream().map(
                    TransactionsPostResponseBody::getId).collect(Collectors.joining(",")), "Ingested Transactions");
                streamTask.setResponse(transactionIds);
                return streamTask;
            });
    }

    @Override
    public Mono<TransactionTask> rollBack(TransactionTask streamTask) {
        return Mono.just(streamTask);
    }

}
