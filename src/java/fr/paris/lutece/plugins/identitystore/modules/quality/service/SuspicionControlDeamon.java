package fr.paris.lutece.plugins.identitystore.modules.quality.service;

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspicionAction;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspicionActionHome;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.identity.IdentityHome;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.service.daemon.LoggingDaemon;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.indexer.elastic.index.business.IndexAction;
import fr.paris.lutece.plugins.identitystore.service.indexer.elastic.index.business.IndexActionHome;
import fr.paris.lutece.plugins.identitystore.v3.web.request.validator.IdentityDuplicateValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.DtoConverter;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AttributeDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AuthorType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.history.IdentityChangeType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.QualifiedIdentitySearchResult;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceNotFoundException;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SuspicionControlDeamon extends LoggingDaemon {

    private static final String clientCode = AppPropertiesService.getProperty("daemon.suspicionControlDaemon.client.code");
    private static final Integer delay = AppPropertiesService.getPropertyInt( "daemon.suspicionControlDaemon.delay", 600 );
    private static final Integer batchSize = AppPropertiesService.getPropertyInt( "daemon.suspicionControlDaemon.batch.size", 300 );
    private static final RequestAuthor author;
    static
    {
        author = new RequestAuthor( );
        author.setType(AuthorType.application);
        author.setName( AppPropertiesService.getProperty( "daemon.suspicionControlDaemon.author.name", SuspicionControlDeamon.class.getSimpleName() ) );
    }

    @Override
    public void doTask() {
        final StopWatch stopWatch = new StopWatch( );
        stopWatch.start( );
        this.info("Starting SuspicionControlDeamon (" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS")) + ")...");
        this.info( "daemon.suspicionControlDaemon.client.code: " + clientCode );
        this.info( "daemon.suspicionControlDaemon.delay: " + delay );
        this.info( "daemon.suspicionControlDaemon.batch.size: " + batchSize );

        final List<SuspicionAction> suspicionActions = SuspicionActionHome.selectWithLimit(batchSize);
        this.info("-- " + suspicionActions.size() + " suspicion actions selected.");
        final List<Integer> treatedActionIds = new ArrayList<>();

        for(final SuspicionAction action : suspicionActions) {
            if (!isActionEligible( action )) {
                continue;
            }
            try {
                this.info("------");
                this.info("Processing action for CUID=["+action.getCustomerId()+"]...");
                processSuspicionAction(action);
                treatedActionIds.add(action.getId());
                this.info("Action processed successfully.");
            } catch (final Exception e) {
                this.error("Error occurred while processing suspicion action for CUID=[" + action.getCustomerId() + "] : " + e.getMessage());
                this.info( "Continuing..." );
            }
        }

        this.info("-- " + treatedActionIds.size() + " suspicion actions treated successfully.");
        this.info("-- Deleting those actions...");
        try {
            SuspicionActionService.instance().delete(treatedActionIds);
        } catch (IdentityStoreException e) {
            this.error("Error occurred while deleting treated suspicion actions.");
        }

        stopWatch.stop( );
        final String duration = DurationFormatUtils.formatDurationWords(stopWatch.getTime(), true, true);
        this.info( "Ending SuspicionControlDeamon (" + LocalDateTime.now( ).format( DateTimeFormatter.ofPattern( "dd/MM/yyyy HH:mm:ss.SSS" ) ) + ")" );
        this.info( "Execution time " + duration );
    }

    private boolean isActionEligible(final SuspicionAction action) {
        // if the action date didn't yet pass the delay, we skip it
        if (action.getDate().toInstant().plus(delay, ChronoUnit.SECONDS).isAfter(Instant.now())) {
            return false;
        }

        // if there is an entry in identitystore_index_action, we skip the action
        final List<IndexAction> indexActions = IndexActionHome.selectWithCuidAndActionType(action.getCustomerId(), action.getActionType());
        return indexActions.isEmpty();
    }

    /**
     * revérifier toutes les suspicions (on vérifie à la fois pour identité principale et doublons potentiel)
     * Le traitment sera comme suit:
     *     SI on avait AB, AC, DA, on supprime AB et AC et DA, on ne recherche que sur A et sur D
     *     si on avait BA, CA, on supprime BA et CA, et on recherche tous les doublons sur B, sur C et sur A qui a changé
     *
     * Si on est sur une action de type DELETE, on supprime toutes les suspicions de doublon existantes sur le CUID de l'identité supprimée
     */
    private void processSuspicionAction(final SuspicionAction action) throws IdentityStoreException {
        final String customerId = action.getCustomerId();
        final List<SuspiciousIdentity> suspiciousIdentityList = SuspiciousIdentityHome.selectByCustomerIDs(List.of(customerId));
        if(CollectionUtils.isNotEmpty(suspiciousIdentityList)) {
            if(action.getActionType().equals(IdentityChangeType.DELETE.name())) {
                this.info("Suspicion action is of type DELETE : deleting all " + suspiciousIdentityList.size() + " existing suspicions for CUID=["+action.getCustomerId()+"]...");
                SuspiciousIdentityService.instance().delete(suspiciousIdentityList);
                this.info("Suspicious identities succesfully deleted.");
                return;
            }
            final Map<String, Set<String>> rulesByCuidToCheckOn = new HashMap<>();
            final Set<String> defaultRules =
                    Arrays.stream(AppPropertiesService.getProperty(
                                                              IdentityChangeType.CREATE.name().equals(action.getActionType()) ?
                                                              IdentityDuplicateValidator.PROPERTY_DUPLICATES_CREATION_RULES : IdentityDuplicateValidator.PROPERTY_DUPLICATES_UPDATE_RULES, "")
                                                      .split(",")).filter(StringUtils::isNotEmpty).collect(Collectors.toSet());

            rulesByCuidToCheckOn.computeIfAbsent(customerId, k -> new HashSet<>(defaultRules));
            suspiciousIdentityList.forEach(
                    suspiciousIdentity -> rulesByCuidToCheckOn.computeIfAbsent(suspiciousIdentity.getCustomerId(), k -> new HashSet<>(defaultRules))
                                                              .add(suspiciousIdentity.getDuplicateRuleCode()));

            this.info("Deleting " + suspiciousIdentityList.size() + " existing suspicious identities regarding CUID=[" + customerId + "]...");
            SuspiciousIdentityService.instance().delete(suspiciousIdentityList);
            this.info("Delete completed.");

            for (final Map.Entry<String, Set<String>> entryToCheck : rulesByCuidToCheckOn.entrySet()) {
                final String cuid = entryToCheck.getKey();
                final Set<String> ruleCodes = entryToCheck.getValue();

                final Identity identity = IdentityHome.findByCustomerId(cuid);
                final List<DuplicateRule> rules = ruleCodes.stream().map(code -> {
                    try {
                        return DuplicateRuleService.instance().get(code);
                    } catch (final ResourceNotFoundException e) {
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toList());
                final Map<String, String> attributeMap = DtoConverter.convertIdentityToDto(identity).getAttributes().stream()
                                                                     .collect(Collectors.toMap(AttributeDto::getKey, AttributeDto::getValue));
                final Map<String, QualifiedIdentitySearchResult> duplicates =
                        SearchDuplicatesService.instance().findDuplicates(attributeMap, cuid, rules, Collections.emptyList(), true);
                if (duplicates != null && !duplicates.isEmpty()) {
                    for (final Map.Entry<String, QualifiedIdentitySearchResult> entry : duplicates.entrySet()) {
                        final String ruleCode = entry.getKey();
                        final QualifiedIdentitySearchResult duplicateResult = entry.getValue();
                        final DuplicateRule rule = rules.stream().filter(r -> ruleCode.equals(r.getCode())).findFirst().get();
                        for (final IdentityDto potentialDuplicateIdentity : duplicateResult.getQualifiedIdentities()) {
                            if (!SuspiciousIdentityService.instance().existsSuspicious(cuid, potentialDuplicateIdentity.getCustomerId(), rule.getId())) {
                                final SuspiciousIdentityChangeRequest request = new SuspiciousIdentityChangeRequest();
                                request.setSuspiciousIdentity(new SuspiciousIdentityDto());
                                request.getSuspiciousIdentity().setCustomerId(cuid);
                                request.getSuspiciousIdentity().setDuplicateCuid(potentialDuplicateIdentity.getCustomerId());
                                request.getSuspiciousIdentity().setDuplicationRuleCode(ruleCode);
                                request.getSuspiciousIdentity().getMetadata().putAll(duplicateResult.getMetadata());
                                SuspiciousIdentityService.instance()
                                                         .create(request, identity, potentialDuplicateIdentity.getCustomerId(), rule, clientCode, author);
                                this.info("Identity pair [" + identity.getCustomerId() + " / " + cuid + "] has been marked suspicious for rule code=[" +
                                          ruleCode + "].");
                            } else {
                                this.info("Identity pair [" + identity.getCustomerId() + " / " + cuid + "] already exists has suspicious for rule code=[" +
                                          ruleCode + "].");
                            }
                        }
                    }
                } else {
                    this.info("No potential duplicate found.");
                }
            }
        }
    }


}
