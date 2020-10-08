package com.backbase.stream.service;

import com.backbase.dbs.user.integration.api.UsersApi;
import com.backbase.dbs.user.integration.model.AssignRealm;
import com.backbase.dbs.user.integration.model.IdPost;
import com.backbase.dbs.user.integration.model.UserItem;
import com.backbase.dbs.user.integration.model.UserItemGet;
import com.backbase.stream.legalentity.model.IdentityUserLinkStrategy;
import com.backbase.stream.legalentity.model.LegalEntity;
import com.backbase.stream.legalentity.model.User;
import com.backbase.stream.mapper.RealmMapper;
import com.backbase.stream.mapper.UserMapper;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import sun.security.krb5.Realm;

/**
 * Stream User Management. Still needs to be adapted to use Identity correctly
 */
@SuppressWarnings("WeakerAccess")
@Slf4j
@AllArgsConstructor
public class UserService {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);
    private final RealmMapper realmMapper = Mappers.getMapper(RealmMapper.class);

    private final UsersApi usersApi;

    /**
     * Get User by extenral ID.
     *
     * @param externalId Extenral ID
     * @return User if exists. Empty if not.
     */
    public Mono<User> getUserByExternalId(String externalId) {
        return usersApi.getExternalIdexternalId(externalId)
            .doOnNext(userItem -> log.info("Found user: {} for externalId: {}", userItem.getFullName(), userItem.getExternalId()))
            .onErrorResume(WebClientResponseException.NotFound.class, notFound ->
                handleUserNotFound(externalId, notFound.getResponseBodyAsString()))
            .map(mapper::toStream);
    }

    /**
     * Get Identity User.  WIP!
     *
     * @param externalId External ID
     * @return Identity User
     */
    public Mono<User> getIdentityUserByExternalId(String externalId) {
        return usersApi.getExternalIdexternalId(externalId)
            .doOnNext(userItem -> log.info("Found user: {} for externalId: {}", userItem.getFullName(), userItem.getExternalId()))
            .onErrorResume(WebClientResponseException.NotFound.class, notFound ->
                handleUserNotFound(externalId, notFound.getResponseBodyAsString()))
            .map(mapper::toStream);
    }


    private Mono<UserItemGet> handleUserNotFound(String externalId, String responseBodyAsString) {
        log.info("User with externalId: {} does not exist: {}", externalId, responseBodyAsString);
        return Mono.empty();
    }

    public Mono<User> createUser(User user, String legalEntityExternalId) {
        UserItem createUser = mapper.toPresentation(user);
        createUser.setLegalEntityExternalId(legalEntityExternalId);

        return usersApi.postBulk(Collections.singletonList(createUser))
            .doOnError(WebClientResponseException.class, e -> handleCreateUserError(user, e))
            .map(userCreated -> handleCreateUserResult(user, userCreated));
    }

    /**
     * Get users for specified legal entity.
     *
     * @param legalEntityInternalId legal  entity internal id.
     * @return flux of user  items.
     */
    public Mono<GetUsersByLegalEntityIdsResponse> getUsersByLegalEntity(String legalEntityInternalId) {
        log.debug("Retrieving users for Legal Entity '{}'", legalEntityInternalId);

        GetUsersByLegalEntityIds getUsersByLegalEntityIds = new GetUsersByLegalEntityIds();
        getUsersByLegalEntityIds.addLegalEntityIdsItem(legalEntityInternalId);
        return usersApi.postLegalEntityIds(getUsersByLegalEntityIds);
    }

    /**
     * Archive users. As it is not possible to remove users from DBS, to be able to remove Legal Entity
     * user external ID is being updated to random value. (REMOVED_<external_id>_UUID)
     * This step is required to be able re-ingest user with same external id again.
     *
     * @param legalEntityInternalId
     * @param userExternalIds
     * @return Mono<Void>
     */
    public Mono<Void> archiveUsers(String legalEntityInternalId, List<String> userExternalIds) {
        //  There is no way to remove user from DBS, so to bypass this we just archive DBS user representing member.
        return usersApi.putUsers(
            userExternalIds.stream()
                .map(userExternalId -> {
                    return new BatchUser()
                        .externalId(userExternalId)
                        .userUpdate(new com.backbase.dbs.user.presentation.service.model.User()
                            .externalId("REMOVED_" + userExternalId + "_" + UUID.randomUUID().toString())
                            .legalEntityId(legalEntityInternalId)
                            .fullName("archived_" + userExternalId));
                })
                .collect(Collectors.toList()))
            .map(r -> {
                log.debug("Batch Archive User response: status {} for resource {}, errors: {}", r.getStatus(), r.getResourceId(), r.getErrors());
                if (!r.getStatus().getValue().equals("200")) {
                    throw new RuntimeException(
                        MessageFormat.format("Failed item in the batch for User Update: status {0} for resource {1}, errors: {2}",
                            r.getStatus(), r.getResourceId(), r.getErrors())
                    );
                }
                return r;
            })
            .collectList()
            .onErrorResume(WebClientResponseException.class, e -> {
                log.error("Failed to delete user: {}", e.getResponseBodyAsString(), e);
                return Mono.error(e);
            })
            .then();
    }

    /**
     * Create Realm.
     *
     * @param realmName
     * @return
     */
    private Mono<Realm> createRealm(final String realmName) {
        AddRealm assignRealmRequest = new AddRealm().realmName(realmName);
        return usersApi.postRealms(assignRealmRequest)
            .doOnNext(addRealmResponse -> log.info("Realm Created: '{}'", addRealmResponse.getId()))
            .doOnError(WebClientResponseException.class, badRequest ->
                log.error("Error creating Realm"))
            .map(realmMapper::toStream);
    }

    /**
     * Checks for existing Realms and Returns if matching realm is found.
     *
     * @param realmName
     * @return
     */
    private Mono<Realm> existingRealm(final String realmName) {
        log.info("Checking for existing Realm '{}'", realmName);
        return usersApi.getRealms(null)
            .doOnError(WebClientResponseException.class, badRequest ->
                log.error("Error getting Realms"))
            .collectList()
            .map(realms -> realms.stream().filter(realm -> realmName.equals(realm.getRealmName())).findFirst())
            .flatMap(Mono::justOrEmpty);
    }

    /**
     * Setup realm checks if realm exists otherwise creates
     *
     * @param legalEntity
     * @return
     */
    public Mono<Realm> setupRealm(LegalEntity legalEntity) {
        if (StringUtils.isEmpty(legalEntity.getRealmName())) {
            return Mono.empty();
        }
        Mono<Realm> existingRealm = existingRealm(legalEntity.getRealmName());
        Mono<Realm> createNewRealm = createRealm(legalEntity.getRealmName());
        return existingRealm.switchIfEmpty(createNewRealm)
            .map(actual -> actual);

    }

    /**
     * Link LegalEntity to that Realm. (Realm should already be in DBS)
     *
     * @param legalEntity Legal entity object, contains the Realm Name and LE IDs
     * @return the same object on success
     */
    public Mono<LegalEntity> linkLegalEntityToRealm(LegalEntity legalEntity) {
        log.info("Linking Legal Entity with internal Id '{}' to Realm: '{}'", legalEntity.getInternalId(), legalEntity.getRealmName());
        AssignRealm assignRealm = new AssignRealm().legalEntityId(legalEntity.getInternalId());
        return usersApi.postLegalentitiesByRealmName(legalEntity.getRealmName(), assignRealm)
            .doOnError(WebClientResponseException.BadRequest.class, badRequest ->
                log.error("Error Linking: {}", badRequest.getResponseBodyAsString()))
            .then(Mono.just(legalEntity))
            .map(actual -> {
                log.info("Legal Entity: {} linked to Realm: {}", actual.getInternalId(), legalEntity.getRealmName());
                return actual;
            });
    }

    /**
     * Create or Import User from Identity base on {@link IdentityUserLinkStrategy property}
     *
     * @param user
     * @param legalEntityInternalId
     * @return the same User with updated internal and external id on success
     */
    public Mono<User> createOrImportIdentityUser(User user, String legalEntityExternalId) {
        IdPost createIdentityRequest = new IdPost();
        createIdentityRequest.setLegalEntityExternalId(legalEntityExternalId);
        createIdentityRequest.setExternalId(user.getId());

        if (IdentityUserLinkStrategy.CREATE_IN_IDENTITY.equals(user.getIdentityLinkStrategy())) {
            Objects.requireNonNull(user.getFullName(), "User Full Name is required for user: " + user.getId() + " in legal entity: " + legalEntityExternalId);
            Objects.requireNonNull(user.getEmailAddress(), "User Email Address is required for user: " + user.getId() + " in legal entity: " + legalEntityExternalId);
            Objects.requireNonNull(user.getMobileNumber(), "User Mobile Number is required for user: " + user.getId() + " in legal entity: " + legalEntityExternalId);

            createIdentityRequest.setFullName(user.getFullName());
            createIdentityRequest.setEmailAddress(user.getEmailAddress().getAddress());
            createIdentityRequest.setMobileNumber(user.getMobileNumber().getNumber());
        }

        return usersApi.postIdentities(createIdentityRequest)
            .map(identityCreatedItem -> {
                user.setInternalId(identityCreatedItem.getInternalId());
                user.setExternalId(identityCreatedItem.getExternalId());
                return user;
            });
    }


    /**
     * Update identity user attributes
     *
     * @param user
     * @return {@link Mono<Void>}
     */
    public Mono<Void> updateIdentityUserAttributes(User user) {
        ReplaceIdentity replaceIdentity = new ReplaceIdentity();
        replaceIdentity.attributes(user.getAttributes());

        usersApi.
        return usersApi.putInternalIdByInternalId(user.getInternalId(), replaceIdentity);
    }

    private User handleCreateUserResult(User user, UserItem userCreated) {
        log.info("Created user: {} with internalId: {}", user.getFullName(), userCreated.getExternalId());
        return user;
    }

    private void handleCreateUserError(User user, WebClientResponseException response) {
        log.error("Created user: {} with internalId: {}", user, response.getResponseBodyAsString());
    }
}
