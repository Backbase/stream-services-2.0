package com.backbase.stream;

import com.backbase.dbs.transaction.api.service.v2.TransactionPresentationServiceApi;
import com.backbase.dbs.transaction.api.service.v2.model.ArrangementItem;
import com.backbase.dbs.transaction.api.service.v2.model.TransactionItem;
import com.backbase.dbs.transaction.api.service.v2.model.TransactionsDeleteRequestBody;
import com.backbase.dbs.transaction.api.service.v2.model.TransactionsPatchRequestBody;
import com.backbase.dbs.transaction.api.service.v2.model.TransactionsPostRequestBody;
import com.backbase.dbs.transaction.api.service.v2.model.TransactionsPostResponseBody;
import com.backbase.stream.transaction.TransactionTask;
import com.backbase.stream.transaction.TransactionUnitOfWorkExecutor;
import com.backbase.stream.transaction.TransactionsQuery;
import com.backbase.stream.worker.model.UnitOfWork;
import java.util.Collection;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Main Transaction Ingestion Service. Supports Retry and back pressure and controller number of transactions to ingest
 * per second.
 */
@Slf4j
public class TransactionService {

    private final TransactionPresentationServiceApi transactionPresentationServiceApi;
    private final TransactionUnitOfWorkExecutor transactionTaskExecutor;

    public TransactionService(TransactionPresentationServiceApi transactionPresentationServiceApi,
        TransactionUnitOfWorkExecutor transactionTaskExecutor) {
        this.transactionTaskExecutor = transactionTaskExecutor;
        this.transactionPresentationServiceApi = transactionPresentationServiceApi;
    }

    /**
     * Upsert Transactions.
     *
     * @param transactions Unbounded list of Transactions
     * @return Ingestion Transactions IDs
     */
    public Flux<TransactionsPostResponseBody> processTransactions(Flux<TransactionsPostRequestBody> transactions) {
        Flux<UnitOfWork<TransactionTask>> unitOfWorkFlux = transactionTaskExecutor.prepareUnitOfWork(transactions);
        return unitOfWorkFlux.flatMap(transactionTaskExecutor::executeUnitOfWork)
            .flatMap(this::getTransactionIdsFlux);
    }

    private Flux<TransactionsPostResponseBody> getTransactionIdsFlux(UnitOfWork<TransactionTask> unitOfWork) {
        Stream<TransactionsPostResponseBody> transactionIdsStream = unitOfWork.getStreamTasks().stream()
            .map(TransactionTask::getResponse)
            .flatMap(Collection::stream);
        return Flux.fromStream(transactionIdsStream);
    }


    /**
     * Retrieve latest transactions for an Arrangement.
     *
     * @param arrangementId external productId
     * @param size          number of transactions to return.
     * @return List of transactions
     */
    public Flux<TransactionItem> getLatestTransactions(String arrangementId, int size) {
        TransactionsQuery transactionsQuery = new TransactionsQuery();
        transactionsQuery.setArrangementId(arrangementId);
        transactionsQuery.setSize(size);
        return getTransactions(transactionsQuery)
            .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                log.info("No transactions found for: {} message: {}", arrangementId, ex.getResponseBodyAsString());
                return Flux.empty();
            }).doOnError(Throwable.class, ex -> log.error("Error: {}", ex.getMessage(), ex));
    }


    /**
     * Remove transactions from DBS.
     *
     * @param transactionItemDelete Body of transactions to delete
     * @return Void if successful.
     */
    @SuppressWarnings("WeakerAccess")
    public Mono<Void> deleteTransactions(Flux<TransactionsDeleteRequestBody> transactionItemDelete) {
        return transactionItemDelete
            .collectList()
            .flatMap(item -> transactionPresentationServiceApi.postDelete(item, null));

    }

    /**
     * Get list of transactions.
     *
     * @param transactionsQuery Transaction Query
     * @return A list of transactions
     */
    public Flux<TransactionItem> getTransactions(TransactionsQuery transactionsQuery) {
        return transactionPresentationServiceApi.getTransactions(
            null, // xTransactionsUserId
            transactionsQuery.getAmountGreaterThan(),
            transactionsQuery.getAmountLessThan(),
            transactionsQuery.getBookingDateGreaterThan(),
            transactionsQuery.getBookingDateLessThan(),
            transactionsQuery.getTypes(),
            transactionsQuery.getDescription(),
            transactionsQuery.getReference(),
            transactionsQuery.getTypeGroups(),
            transactionsQuery.getCounterPartyName(),
            transactionsQuery.getCounterPartyAccountNumber(),
            transactionsQuery.getCreditDebitIndicator(),
            transactionsQuery.getCategories(),
            transactionsQuery.getBillingStatus(),
            transactionsQuery.getState(),
            transactionsQuery.getCurrency(),
            transactionsQuery.getNotes(),
            transactionsQuery.getId(),
            transactionsQuery.getArrangementId(),
            transactionsQuery.getArrangementsIds(),
            transactionsQuery.getFromCheckSerialNumber(),
            transactionsQuery.getToCheckSerialNumber(),
            transactionsQuery.getCheckSerialNumbers(),
            transactionsQuery.getQuery(),
            transactionsQuery.getFrom(),
            transactionsQuery.getCursor(),
            transactionsQuery.getSize(),
            transactionsQuery.getOrderBy(),
            transactionsQuery.getDirection(),
            transactionsQuery.getSecDirection());
    }

    /**
     * Update Transactions  with a new category or billing status.
     *
     * @param transactionItems Updated category and billing status fields
     * @return empty mono on completion
     */
    public Mono<Void> patchTransactions(Flux<TransactionsPatchRequestBody> transactionItems) {
        return transactionItems
            .collectList()
            .flatMap(items -> transactionPresentationServiceApi.patchTransactions(items, null));
    }

    /**
     * Trigger refresh action for transactions.
     *
     * @param arrangementItems Arrangement ids for which to retrieve new transactions
     * @return empty mono on completion
     */
    public Mono<Void> postRefresh(Flux<ArrangementItem> arrangementItems) {
        return arrangementItems
            .collectList()
            .flatMap(item -> transactionPresentationServiceApi.postRefresh(item, null));
    }

}
