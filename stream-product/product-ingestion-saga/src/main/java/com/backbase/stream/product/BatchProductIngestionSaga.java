package com.backbase.stream.product;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;


import com.backbase.dbs.arrangement.api.service.v2.model.AccountArrangementItemPost;
import com.backbase.stream.legalentity.model.BaseProduct;
import com.backbase.stream.legalentity.model.BaseProductGroup;
import com.backbase.stream.legalentity.model.BatchProductGroup;
import com.backbase.stream.legalentity.model.BusinessFunctionGroup;
import com.backbase.stream.legalentity.model.CustomDataGroupItem;
import com.backbase.stream.legalentity.model.JobProfileUser;
import com.backbase.stream.legalentity.model.ProductGroup;
import com.backbase.stream.legalentity.model.ServiceAgreement;
import com.backbase.stream.legalentity.model.User;
import com.backbase.stream.product.configuration.ProductIngestionSagaConfigurationProperties;
import com.backbase.stream.product.service.ArrangementService;
import com.backbase.stream.product.task.BatchProductGroupTask;
import com.backbase.stream.product.task.ProductGroupTask;
import com.backbase.stream.product.utils.StreamUtils;
import com.backbase.stream.service.AccessGroupService;
import com.backbase.stream.service.UserService;
import com.backbase.stream.worker.exception.StreamTaskException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Saga  to ingest customer products using DBS batch APIs.
 */
@Slf4j
public class BatchProductIngestionSaga extends ProductIngestionSaga {

    public static final String BATCH_PRODUCT_GROUP = "batch-product-group";


    public BatchProductIngestionSaga(ArrangementService arrangementService, AccessGroupService accessGroupService, UserService userService, ProductIngestionSagaConfigurationProperties configurationProperties) {
        super(arrangementService, accessGroupService, userService,  configurationProperties);
    }

    public Mono<ProductGroupTask> process(ProductGroupTask streamTask) {

        ProductGroup productGroup = streamTask.getProductGroup();

        BatchProductGroupTask batchProductGroupTask = new BatchProductGroupTask();
        batchProductGroupTask.setBatchProductGroup(new BatchProductGroup()
            .serviceAgreement(productGroup.getServiceAgreement())
            .addProductGroupsItem(productGroup));

        return process(batchProductGroupTask)
            .map(batchProductGroup -> {
                streamTask.addHistory(batchProductGroup.getHistory());
               return streamTask;
            });
    }


    /**
     * Setup entitlements and arrangements trying to utilize DBS batch APIs.
     * <br/><br/>
     * If mode is <b>UPDATE</b>, then only provided arrangements and groups are updated.
     * <ul>
     * <li> Provided arrangements are added to specified product groups and removed from other product groups.</li>
     * <li> Permissions for provided data groups are aligned with users job profiles.</li>
     * </ul>
     * <br/><br/>
     * If mode is <b>REPLACE</b>, then entire configuration is replaced by provided values.
     * <ul>
     * <li> Arrangements not present in any provided data group will be removed from DBS.</li>
     * <li> Data groups will contain only provided items.</li>
     * <li> Permissions will be completely replaced by provided job  profiles.</li>
     * </ul>
     */
    @ContinueSpan(log = "processProductsBatch")
    public Mono<BatchProductGroupTask> process(BatchProductGroupTask streamTask) {
        return validateBatchProductGroup(streamTask)
                .doOnNext(batchProductGroupTask -> {
                    streamTask.info(BATCH_PRODUCT_GROUP, PROCESS, null, streamTask.getId(), null, "Process Batch Product Group Task: %s", streamTask.getId());
                })
                .flatMap(this::upsertArrangementsBatch)
                .flatMap(this::setupProductGroupsBatch)
                .flatMap(this::setupBusinessFunctionsAndPermissionsBatch);
    }


    protected Mono<BatchProductGroupTask> validateBatchProductGroup(BatchProductGroupTask streamTask) {
        try {
            if (CollectionUtils.isEmpty(streamTask.getData().getProductGroups())) {
                streamTask.warn(BATCH_PRODUCT_GROUP, VALIDATE, REJECTED, null, null, "Product Groups are empty!");
                throw new StreamTaskException(streamTask, "Product Groups should not be empty!");
            }
            streamTask.getData().getProductGroups().forEach(productGroup -> {
                String name = productGroup.getName();
                List<CustomDataGroupItem> customDataGroupItems = productGroup.getCustomDataGroupItems();
                if (productGroup.getUsers() == null) {
                    streamTask.warn(BATCH_PRODUCT_GROUP, VALIDATE, REJECTED, name, null, "Product Group must have users assigned to it!");
                    throw new StreamTaskException(streamTask, "Product Group must have users assigned to it!");
                }
                if (StreamUtils.getInternalProductIds(productGroup).isEmpty() && customDataGroupItems.isEmpty()) {
                    streamTask.warn(BATCH_PRODUCT_GROUP, VALIDATE, REJECTED, name, null, "Product group must have products or Custom Data Group Items assigned to it!");
                    throw new StreamTaskException(streamTask, "Product group must have products or Custom Data Group Items assigned to it!");
                }
                if (customDataGroupItems != null && !customDataGroupItems.isEmpty() && productGroup.getProductGroupType() == null) {
                    streamTask.warn(BATCH_PRODUCT_GROUP, VALIDATE, REJECTED, name, null, "Product Group with Custom Data Group Items must have a Product Group Defined!");
                    throw new StreamTaskException(streamTask, "Product Group with Custom Data Group Items must have a Product Group Defined!");
                }
                if (productGroup.getProductGroupType() == null && StreamUtils.getAllProducts(productGroup).count() > 0) {
                    streamTask.warn(BATCH_PRODUCT_GROUP, VALIDATE, REJECTED, name, null, "Product Group: {} does not have type setup. As Product Group contains products setting type to ARRANGEMENTS");
                    productGroup.setProductGroupType(BaseProductGroup.ProductGroupTypeEnum.ARRANGEMENTS);
                }
            });
            return Mono.just(streamTask);
        } catch (StreamTaskException e) {
            return Mono.error(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static<T> Predicate<T> distinctByKeys(final Function<? super T, ?>... keyExtractors) {
      final Map<List<?>, Boolean> seen = new ConcurrentHashMap<>();
      return t -> {
        final List<?> keys = Arrays.stream(keyExtractors)
            .map(key -> key.apply(t))
            .collect(Collectors.toList());
        return seen.putIfAbsent(keys, Boolean.TRUE) == null;
      };
    }

    protected Mono<BatchProductGroupTask> upsertArrangementsBatch(BatchProductGroupTask batchProductGroupTask) {
        List<AccountArrangementItemPost> batchArrangements = new ArrayList<>();
        batchProductGroupTask.getData().getProductGroups().forEach(pg -> batchArrangements.addAll(
                Stream.of(
                        StreamUtils.nullableCollectionToStream(pg.getCurrentAccounts()).map(productMapper::toPresentation),
                        StreamUtils.nullableCollectionToStream(pg.getSavingAccounts()).map(productMapper::toPresentation),
                        StreamUtils.nullableCollectionToStream(pg.getDebitCards()).map(productMapper::toPresentation),
                        StreamUtils.nullableCollectionToStream(pg.getCreditCards()).map(productMapper::toPresentation),
                        StreamUtils.nullableCollectionToStream(pg.getLoans()).map(productMapper::toPresentation),
                        StreamUtils.nullableCollectionToStream(pg.getTermDeposits()).map(productMapper::toPresentation),
                        StreamUtils.nullableCollectionToStream(pg.getInvestmentAccounts()).map(productMapper::toPresentation),
                        StreamUtils.nullableCollectionToStream(pg.getCustomProducts()).map(productMapper::toPresentation)
                )
                        .flatMap(i -> i)
                        .map(product -> ensureLegalEntityId(pg.getUsers(), product))
                        .collect(Collectors.toList())
        ));
        // Insert  without duplicates.
        // TODO: Revert this change when either OpenAPI generated methods can call super in equals
        // or if the product spec is modified to mitigate the issue
        List<AccountArrangementItemPost> itemsToUpsert = batchArrangements.stream()
            .filter(distinctByKeys(
                AccountArrangementItemPost::getExternalArrangementId,
                AccountArrangementItemPost::getExternalLegalEntityIds,
                AccountArrangementItemPost::getExternalProductId,
                AccountArrangementItemPost::getExternalStateId,
                AccountArrangementItemPost::getProductId,
               /* AccountArrangementItemPost::getAlias,*/
                AccountArrangementItemPost::getAdditions
            )).collect(Collectors.toList());

        Set<String> upsertedInternalIds = new HashSet<>();
        return Flux.fromIterable(itemsToUpsert)
                .sort(comparing(AccountArrangementItemPost::getExternalParentId, nullsFirst(naturalOrder()))) // Avoiding child to be created before parent
                .buffer(50) // hardcoded to match DBS limitation
                .concatMap(batch -> arrangementService.upsertBatchArrangements(batch, batchProductGroupTask)
                        .doOnNext(r -> batchProductGroupTask.info(ARRANGEMENT, UPSERT_ARRANGEMENT, UPDATED, r.getResourceId(), r.getArrangementId(), "Updated Arrangements (in batch)"))
                        .collectList()
                ).map(batchResponses -> {
                    // Update products with internal IDs.
                    return batchProductGroupTask.getData().getProductGroups().stream()
                        .flatMap(pg -> StreamUtils.getAllProducts(pg))
                        .map(product -> {
                            batchResponses.forEach(result -> {
                                if (result.getResourceId().equalsIgnoreCase(product.getExternalId())) {
                                    product.setInternalId(result.getArrangementId());
                                    upsertedInternalIds.add(result.getArrangementId());
                                }
                            });
                            return product;
                        });
                })
                .flatMap(baseProductStream -> Flux.fromStream(baseProductStream)
                    .filter(baseProduct -> !CollectionUtils.isEmpty(baseProduct.getUsersPreferences()))
                    .flatMap(this::updateUsersPreferences))
                .collectList()
                .thenReturn(batchProductGroupTask)
                .flatMap(task -> {
                    if (task.getIngestionMode().equals(BatchProductGroupTask.IngestionMode.REPLACE)) {
                        // Remove arrangements which are not provided in product group.
                        return accessGroupService.getArrangementInternalIdsForServiceAgreement(task.getData().getServiceAgreement().getInternalId())
                                //  find arrangements which are not present in product groups, but available in dbs.
                                .filter(arrangementInternalId -> !upsertedInternalIds.contains(arrangementInternalId))
                                // remove arrangement.
                                .flatMap(arrangementService::deleteArrangementByInternalId)
                                .doOnNext(r -> batchProductGroupTask.info(ARRANGEMENT, UPSERT_ARRANGEMENT, REMOVED, null, r, "Removed Arrangement"))
                                .collectList()
                                .thenReturn(task);
                    } else {
                        return Mono.just(task);
                    }
                });
    }

    protected Mono<BaseProduct> updateUsersPreferences(BaseProduct product) {
        return Flux.fromIterable(product.getUsersPreferences())
            .map(productMapper::mapUserPreference)
            .flatMap(userPreferencesItem ->
                userService.getUserByExternalId(userPreferencesItem.getUserId())
                    .flatMap(user -> arrangementService.updateUserPreferences(
                        userPreferencesItem
                            .userId(user.getInternalId())
                            .arrangementId(product.getInternalId())))
                    .onErrorResume(WebClientResponseException.NotFound.class, throwable -> {
                        log.info("User Id not found for: {}. Request:[{}] {}  Response: {}",
                            userPreferencesItem.getUserId(), throwable.getRequest().getMethod(),
                            throwable.getRequest().getURI(), throwable.getResponseBodyAsString());
                        return Mono.empty();
                    })
                    .thenReturn(userPreferencesItem)
            )
            .collectList()
            .thenReturn(product);
    }

    protected Mono<BatchProductGroupTask> setupProductGroupsBatch(BatchProductGroupTask task) {
        List<BaseProductGroup> productGroups = task.getData().getProductGroups();
        return accessGroupService.getExistingDataGroups(task.getData().getServiceAgreement().getInternalId(), null)
                .collectList()
                .flatMap(existingDataGroups -> {
                    // set IDs from DBS
                    productGroups.forEach(pg -> existingDataGroups.stream()
                            .filter(eg -> accessGroupService.isEquals(pg, eg)).findFirst()
                            .ifPresent(g -> pg.setInternalId(g.getId())));

                    List<BaseProductGroup> toCreate =
                            productGroups.stream()
                                    .filter(pg -> existingDataGroups.stream()
                                            .noneMatch(dgi -> accessGroupService.isEquals(pg, dgi)))
                                    .collect(Collectors.toList());
                    // Create new groups.
                    return Flux.fromIterable(toCreate)
                            .concatMap(g -> accessGroupService.createArrangementDataAccessGroup(task.getData().getServiceAgreement(), g, task))
                            .collectList()
                            .thenReturn(existingDataGroups);
                })
                .flatMap(existingGroups -> accessGroupService.updateExistingDataGroupsBatch(task, existingGroups, productGroups));
    }


    protected Mono<BatchProductGroupTask> setupBusinessFunctionsAndPermissionsBatch(BatchProductGroupTask task) {
        List<JobProfileUser> profileUsers = task.getData().getProductGroups().stream().flatMap(g -> g.getUsers().stream()).distinct().collect(Collectors.toList());

        return setupBusinessFunctions(task, task.getData().getServiceAgreement(), profileUsers)
                .flatMap(functionGroups -> {
                    Collection<JobProfileUser> uniqueUsers = profileUsers.stream().collect(Collectors.toMap(jpu -> jpu.getUser().getExternalId(), u -> u, (u, id) -> u)).values();
                    return Flux.fromIterable(uniqueUsers)
                            .flatMap(user -> processUser(task, user))
                            .collectList()
                            .flatMap(users -> {
                                Map<User, Map<BusinessFunctionGroup, List<BaseProductGroup>>> usersPermissions = new HashMap<>();
                                users.forEach(user -> {
                                    functionGroups.forEach(bfg -> {
                                        List<BaseProductGroup> groupsForFunction = getUsersGroupWithFunction(task.getBatchProductGroup(), user, bfg);
                                        if (!CollectionUtils.isEmpty(groupsForFunction)) {
                                            Map<BusinessFunctionGroup, List<BaseProductGroup>> up = usersPermissions.getOrDefault(user, new HashMap<>());
                                            List<BaseProductGroup> fpg = up.getOrDefault(bfg, new ArrayList<>());
                                            fpg.addAll(groupsForFunction);
                                            up.put(bfg, fpg);
                                            usersPermissions.put(user, up);
                                        }
                                    });
                                });
                                return accessGroupService.assignPermissionsBatch(task, usersPermissions);
                            });
                });

    }

    protected Mono<List<BusinessFunctionGroup>> setupBusinessFunctions(BatchProductGroupTask streamTask, ServiceAgreement serviceAgreement, List<JobProfileUser> jobProfileUsers) {
        streamTask.info(FUNCTION_GROUP, "setup-business-functions", "", serviceAgreement.getExternalId(), null, "Setting up Business Functions for Users: %s", prettyPrintUsers(jobProfileUsers));
        return Flux.fromIterable(jobProfileUsers)
                .doOnNext(user -> log.info("Setup Business Function for: {} with Product Groups: {}",user.getUser().getExternalId(), prettyPrintProductGroups(streamTask)))
                .flatMap(jobProfileUser -> getBusinessFunctionGroups(jobProfileUser, serviceAgreement)
                        .map(bfGroups -> {
                            jobProfileUser.setBusinessFunctionGroups(bfGroups);
                            return bfGroups;
                        }))
                .flatMap(Flux::fromIterable)
                .collectList()
                .map(businessFunctionGroups -> {
                    // remove duplicates
                    return new ArrayList<>(businessFunctionGroups.stream()
                            .collect(Collectors.toMap(BusinessFunctionGroup::getName, p -> p, (p, q) -> p)).values());
                })
                .flatMap(businessFunctionGroups -> accessGroupService.setupFunctionGroups(streamTask, serviceAgreement, businessFunctionGroups))
                .map(businessFunctionGroups -> {

                    // Update ids of existing business function groups in JobProfileUser objects.
                    jobProfileUsers.forEach(jobProfileUser -> jobProfileUser.getBusinessFunctionGroups().forEach(userBfg -> {
                        businessFunctionGroups.stream()
                                .filter(bfg -> bfg.getName().equals(userBfg.getName()))
                                .findFirst()
                                .ifPresent(businessFunctionGroup -> userBfg.setId(businessFunctionGroup.getId()));
                    }));

                    streamTask.info(FUNCTION_GROUP, "setup-business-functions", "success", serviceAgreement.getExternalId(), null, "Setting up Business Functions Groups: %s", prettyPrintBusinessGroups(businessFunctionGroups));
                    return businessFunctionGroups;
                });
    }

    @NotNull
    private String prettyPrintProductGroups(BatchProductGroupTask streamTask) {
        return streamTask.getData().getProductGroups().stream().map(BaseProductGroup::getName).collect(Collectors.joining(","));
    }


    @NotNull
    private String prettyPrintUsers(List<JobProfileUser> profileUsers) {
        return profileUsers.stream().map(jobProfileUser -> jobProfileUser.getUser().getExternalId()).collect(Collectors.joining(","));
    }

    @NotNull
    private String prettyPrintBusinessGroups(List<BusinessFunctionGroup> businessFunctionGroups) {
        return businessFunctionGroups.stream().map(BusinessFunctionGroup::getName).collect(Collectors.joining(","));
    }


    protected List<BaseProductGroup> getUsersGroupWithFunction(BatchProductGroup batchProductGroup, User user, BusinessFunctionGroup functionGroup) {
        return batchProductGroup.getProductGroups().stream()
                // get groups with requested function group assigned to the job profile user.
                .filter(group ->
                        group.getUsers().stream()
                                // get only products for specified user.
                                .filter(u -> u.getUser().getExternalId().equals(user.getExternalId()))
                                .map(JobProfileUser::getBusinessFunctionGroups)
                                .flatMap(Collection::stream)
                                .anyMatch(bfg -> bfg.getName().equals(functionGroup.getName()))
                ).collect(Collectors.toList());
    }
}
