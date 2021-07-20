package com.backbase.stream.product.service;


import com.backbase.dbs.accesscontrol.api.service.v2.model.BatchResponseItemExtended.StatusEnum;
import com.backbase.dbs.arrangement.api.service.ApiClient;
import com.backbase.dbs.arrangement.api.service.v2.ArrangementsApi;
import com.backbase.dbs.arrangement.api.service.v2.model.AccountArrangementItem;
import com.backbase.dbs.arrangement.api.service.v2.model.AccountArrangementItemPost;
import com.backbase.dbs.arrangement.api.service.v2.model.AccountArrangementItemPut;
import com.backbase.dbs.arrangement.api.service.v2.model.AccountArrangementItems;
import com.backbase.dbs.arrangement.api.service.v2.model.AccountBatchResponseItemExtended;
import com.backbase.dbs.arrangement.api.service.v2.model.AccountExternalLegalEntityIds;
import com.backbase.dbs.arrangement.api.service.v2.model.AccountInternalIdGetResponseBody;
import com.backbase.dbs.arrangement.api.service.v2.model.AccountUserPreferencesItemPut;
import com.backbase.dbs.arrangement.api.service.v2.model.BatchResponseStatusCode;
import com.backbase.stream.product.exception.ArrangementCreationException;
import com.backbase.stream.product.exception.ArrangementUpdateException;
import com.backbase.stream.product.mapping.ProductMapper;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Manage Products (In DBS Called Arrangements).
 */
@SuppressWarnings("WeakerAccess")
@Slf4j
public class ArrangementService {

    private final ArrangementsApi arrangementsApi;
    private final ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);

    public ArrangementService(ArrangementsApi arrangementsApi) {
        this.arrangementsApi = arrangementsApi;
    }

    public Mono<AccountArrangementItem> createArrangement(AccountArrangementItemPost arrangementItemPost) {

        return arrangementsApi.postArrangements(arrangementItemPost)
            .doOnError(WebClientResponseException.class, throwable ->
                log.error("Failed to create arrangement: {}\n{}", arrangementItemPost.getExternalArrangementId(), throwable.getResponseBodyAsString()))
            .onErrorMap(WebClientResponseException.class, throwable -> new ArrangementCreationException(throwable, "Failed to post arrangements"))
            .map(arrangementAddedResponse -> {

                AccountArrangementItem arrangementItem = productMapper.toArrangementItem(arrangementItemPost);
                arrangementItem.setId(arrangementAddedResponse.getId());

                return arrangementItem;
            });

    }

    public Mono<AccountArrangementItemPut> updateArrangement( AccountArrangementItemPut accountArrangementItemPut) {
        log.info("Updating Arrangement: {}", accountArrangementItemPut.getExternalArrangementId());
        if(accountArrangementItemPut.getDebitCards() == null)
            accountArrangementItemPut.setDebitCards(Collections.emptyList());
        return arrangementsApi.putArrangements(accountArrangementItemPut)
            .doOnNext(aVoid -> log.info("Updated Arrangement: {}", accountArrangementItemPut.getExternalArrangementId())).map(aVoid -> accountArrangementItemPut)
            .thenReturn(accountArrangementItemPut)
            .onErrorResume(WebClientResponseException.class, throwable ->
                Mono.error(new ArrangementUpdateException(throwable, "Failed to update Arrangement: " + accountArrangementItemPut.getExternalArrangementId())));

    }

    /**
     * Upsert list of arrangements using DBS batch upsert API.
     *
     * @param arrangementItems list of arrangements to be upserted.
     * @return flux of response items.
     */
    public Flux<AccountBatchResponseItemExtended> upsertBatchArrangements(List<AccountArrangementItemPost> arrangementItems) {
        return arrangementsApi.postBatchUpsertArrangements(arrangementItems)
                .map(r -> {
                    log.info("Batch Arrangement update result for arrangementId: {}, resourceId: {}, action: {}, result: {}", r.getArrangementId(), r.getResourceId(), r.getAction(), r.getStatus());
                    // Check if any failed, then fail everything.
                    if (!BatchResponseStatusCode.HTTP_STATUS_OK.equals(r.getStatus())) {
                        throw new IllegalStateException("Batch arrangement update failed: " + r.getResourceId());
                    }
                    return r;
                })
                .onErrorResume(WebClientResponseException.class, throwable ->
                        Mono.error(new ArrangementUpdateException(throwable, "Batch arrangement update failed: " + arrangementItems)));
    }

    public Mono<Void> updateUserPreferences(AccountUserPreferencesItemPut userPreferencesItemPut){
        return arrangementsApi.putUserPreferences(userPreferencesItemPut)
            .doOnNext(arg -> log.info("Arrangement preferences created for User with ID: {}", userPreferencesItemPut.getUserId()))
            .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                log.info("Arrangement: {} not found", userPreferencesItemPut.getArrangementId());
                return Mono.empty();
            });
    }

    /**
     * Get Product by Internal ID.
     *
     * @param internalId Internal ID
     * @return Product
     */
    public Mono<AccountArrangementItem> getArrangement(String internalId) {
        return arrangementsApi.getArrangementById(internalId, false)
            .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                log.info("Arrangement: {} not found", internalId);
                return Mono.empty();
            });
    }

    public Flux<AccountArrangementItem> getArrangementByExternalId(List<String> externalId) {
        Flux<AccountArrangementItem> executeRequest = arrangementsApi.getArrangements(null, null, externalId)
            .flatMapIterable(AccountArrangementItems::getArrangementElements);
        return executeRequest;
    }


    public Mono<AccountArrangementItem> getArrangementByExternalId(String externalId) {
        return getArrangementByExternalId(Collections.singletonList(externalId)).next();
    }

    public Mono<String> getArrangementInternalId(String externalId) {
        log.info("Checking if arrangement exists with externalId: {}", externalId);
        return arrangementsApi.getInternalId(externalId)
            .doOnNext(response ->
                log.info("Found Arrangement internalId: {} for externalId: {}", response.getInternalId(), externalId))
            .onErrorResume(WebClientResponseException.NotFound.class, notFound -> {
                log.info("Arrangement not found with externalId: {}", externalId);
                return Mono.empty();
            })
            .onErrorResume(WebClientResponseException.class, exception -> {
                log.info("Exception while getting product by externalId: {}, {}", externalId, exception.getResponseBodyAsString());
                return Mono.empty();
            })

            .map(AccountInternalIdGetResponseBody::getInternalId);
    }

    /**
     * Delete arrangement identified by Internal ID.
     *
     * @param arrangementInternalId arrangement internal identifier
     * @return internal identifier of removed arrangement.
     */
    public Mono<String> deleteArrangementByInternalId(String arrangementInternalId) {
        log.debug("Retrieving Arrangement by internal id {}", arrangementInternalId);
        // get arrangement externalId by internal id.
        return arrangementsApi.getArrangementById(arrangementInternalId, false)
                .map(AccountArrangementItem::getExternalArrangementId)
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.warn("Failed to retrieve arrangement by internal id {}, {}", arrangementInternalId, e.getMessage());
                    return Mono.empty();
                })
                // remove arrangement.
                .flatMap(this::deleteArrangementByExternalId)
                .thenReturn(arrangementInternalId);
    }

    /**
     * Delete arrangement identified by External ID.
     *
     * @param arrangementExternalId arrangement external identifier
     * @return external identifier of removed arrangement.
     */
    public Mono<String> deleteArrangementByExternalId(String arrangementExternalId) {
        log.debug("Removing Arrangement with external id {}", arrangementExternalId);
        return arrangementsApi.deleteExternalArrangementId(arrangementExternalId)
                .thenReturn(arrangementExternalId);
    }

    /**
     * Assign Arrangement with specified Legal Entities.
     *
     * @param arrangementExternalId external id of Arrangement.
     * @param legalEntitiesExternalIds list of Legal Entities external identifiers.
     * @return Mono<Void>
     */
    public Mono<Void> addLegalEntitiesForArrangement(String arrangementExternalId, List<String> legalEntitiesExternalIds){
        log.debug("Attaching Arrangement {} to Legal Entities: {}", arrangementExternalId, legalEntitiesExternalIds);
        return arrangementsApi.postArrangementLegalEntities(arrangementExternalId, new AccountExternalLegalEntityIds().ids(legalEntitiesExternalIds));
    }

    /**
     * Detach specified Arrangement from Legal Entities.
     *
     * @param arrangementExternalId arrangement external identifier.
     * @param legalEntityExternalIds List of Legal Entities identified by external identifier.
     * @return Mono<Void>
     */
    public Mono<Void> removeLegalEntityFromArrangement(String arrangementExternalId, List<String> legalEntityExternalIds) {
        log.debug("Removing Arrangement {} from Legal Entities {}", arrangementExternalId, legalEntityExternalIds);
        // TODO: Very ugly, but seems like BOAT doesn't generate body for DELETE requests. Not sure it is incorrect though..
        ApiClient apiClient = arrangementsApi.getApiClient();
        return apiClient.invokeAPI(
                "/arrangements/{externalArrangementId}/legalentities",
                HttpMethod.DELETE,
                Collections.singletonMap("externalArrangementId", arrangementExternalId),
                new LinkedMultiValueMap<>(),
                Collections.singletonMap("ids", legalEntityExternalIds),
                new HttpHeaders(),
                new LinkedMultiValueMap<>(),
                new LinkedMultiValueMap<>(),
                apiClient.selectHeaderAccept(new String[]{"application/json"}),
                apiClient.selectHeaderContentType(new String[]{}),
                new String[]{},
                new ParameterizedTypeReference<Void>() {
                }
        );
    }

}
