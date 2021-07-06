package com.backbase.stream.mapper;

import com.backbase.dbs.accesscontrol.api.service.v2.model.ApprovalStatus;
import com.backbase.dbs.accesscontrol.api.service.v2.model.FunctionGroupItem;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ParticipantIngest;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationIngestFunctionGroup;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationPermission;
import com.backbase.dbs.accesscontrol.api.service.v2.model.PresentationPermissionFunctionGroupUpdate;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServiceAgreementItem;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServiceAgreementItemQuery;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServiceAgreementPut;
import com.backbase.dbs.accesscontrol.api.service.v2.model.ServicesAgreementIngest;
import com.backbase.stream.legalentity.model.BusinessFunction;
import com.backbase.stream.legalentity.model.BusinessFunctionGroup;
import com.backbase.stream.legalentity.model.JobRole;
import com.backbase.stream.legalentity.model.LegalEntityParticipant;
import com.backbase.stream.legalentity.model.Privilege;
import com.backbase.stream.legalentity.model.ServiceAgreement;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AccessGroupMapper {

    @Mapping(source = "id", target = "internalId")
    ServiceAgreement toStream(ServiceAgreementItemQuery getServiceAgreement);

    @Mapping(source = "id", target = "internalId")
    ServiceAgreement toStream(ServiceAgreementItem serviceAgreementItem);

    BusinessFunction toStream(FunctionGroupItem functionsGetResponseBody);

    @Mapping(source = "participants", target = "participantsToIngest")
    ServicesAgreementIngest toPresentation(ServiceAgreement serviceAgreement);

    ServiceAgreementPut toPresentationPut(ServiceAgreement serviceAgreement);

    // Initialize users list to workaround https://backbase.atlassian.net/browse/MAINT-10442
    @Mapping(defaultExpression = "java( new ArrayList<>() )", source = "users", target = "users")
    ParticipantIngest toPresentation(LegalEntityParticipant legalEntityParticipant);

    PresentationIngestFunctionGroup toPresentation(JobRole referenceJobRole);

    com.backbase.stream.legalentity.model.ApprovalStatus map(
        ApprovalStatus approvalStatus);

    /**
     * Map {@link BusinessFunctionGroup} with privileges to {@link PresentationPermission}.
     *
     * @param functionGroups defined function groups
     * @return mapped object
     */
    default List<PresentationPermission> toPresentation(List<BusinessFunctionGroup> functionGroups) {
        if (Objects.isNull(functionGroups)) {
            return Collections.emptyList();
        }

        return functionGroups.stream()
            .filter(Objects::nonNull)
            .map(BusinessFunctionGroup::getFunctions)
            .flatMap(Collection::stream)
            .map(f -> {
                PresentationPermission presentationPermission = new PresentationPermission();
                presentationPermission.setFunctionId(f.getFunctionId());
                f.getPrivileges().stream()
                    .filter(Objects::nonNull)
                    .map(Privilege::getPrivilege)
                    .forEach(presentationPermission::addPrivilegesItem);

                return presentationPermission;
            }).collect(Collectors.toList());
    }

    default List<PresentationPermissionFunctionGroupUpdate> toUpdate(List<BusinessFunctionGroup> functionGroups) {
        if (Objects.isNull(functionGroups)) {
            return Collections.emptyList();
        }

        return functionGroups.stream()
            .filter(Objects::nonNull)
            .map(BusinessFunctionGroup::getFunctions)
            .flatMap(Collection::stream)
            .map(f -> {
                PresentationPermissionFunctionGroupUpdate presentationPermission
                    = new PresentationPermissionFunctionGroupUpdate();
                presentationPermission.functionName(f.getName());
                f.getPrivileges().stream()
                    .filter(Objects::nonNull)
                    .map(Privilege::getPrivilege)
                    .forEach(presentationPermission::addPrivilegesItem);

                return presentationPermission;
            }).collect(Collectors.toList());
    }

}
