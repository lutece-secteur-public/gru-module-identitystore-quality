/*
 * Copyright (c) 2002-2024, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.identitystore.modules.quality.service;

import fr.paris.lutece.plugins.identitystore.business.attribute.AttributeKey;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.identity.IdentityHome;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.service.attribute.IdentityAttributeService;
import fr.paris.lutece.plugins.identitystore.service.daemon.LoggingDaemon;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityQualityService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityService;
import fr.paris.lutece.plugins.identitystore.service.network.DelayedNetworkService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.DtoConverter;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AttributeChangeStatusType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AttributeDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AttributeStatus;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AuthorType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.QualityDefinition;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.ResponseStatusType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.merge.IdentityMergeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.QualifiedIdentitySearchResult;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceNotFoundException;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This task attempts to automatically resolve duplicates.
 */
public class IdentityDuplicatesResolutionDaemon extends LoggingDaemon
{
    private final String clientCode = AppPropertiesService.getProperty( "daemon.identityDuplicatesResolutionDaemon.client.code" );
    private final String authorName = AppPropertiesService.getProperty( "daemon.identityDuplicatesResolutionDaemon.author.name" );
    private final String ruleCode = AppPropertiesService.getProperty( "daemon.identityDuplicatesResolutionDaemon.rule.code" );
    private final DelayedNetworkService<IdentityDto> identityDtoDelayedNetworkService = new DelayedNetworkService<>();
    private final DelayedNetworkService<Map<String, QualifiedIdentitySearchResult>> duplicateSearchResponseDelayedNetworkService = new DelayedNetworkService<>();
    private int nbIdentitiesMerged = 0;

    @Override
    public void doTask( )
    {
        final StopWatch stopWatch = new StopWatch( );
        stopWatch.start( );
        final RequestAuthor author = this.buildAuthor( stopWatch.getStartTime( ) );
        final String startingMessage = "Starting IdentityDuplicatesResolutionDaemon...";
        this.info( startingMessage );

        nbIdentitiesMerged = 0;

        try
        {
            /* Get rule that identifies strict duplicates */
            final DuplicateRule processedRule = DuplicateRuleService.instance( ).get( ruleCode );
            if ( processedRule != null )
            {
                this.info( "Processing rule " + ruleCode );

                /* Get a batch of suspicious identities that match the rule */
                final List<SuspiciousIdentity> listSuspiciousIdentities = SuspiciousIdentityHome.getSuspiciousIdentitysList( processedRule.getCode( ), 0,
                        null );
                for ( final SuspiciousIdentity suspiciousIdentity : listSuspiciousIdentities )
                {
                    /* Ignore locked suspicions */
                    if ( suspiciousIdentity.getLock( ) == null || !suspiciousIdentity.getLock( ).isLocked( ) )
                    {
                        /* Get and sort identities to process */
                        final IdentityDto identity = identityDtoDelayedNetworkService.call(() -> DtoConverter.convertIdentityToDto( IdentityHome.findByCustomerId( suspiciousIdentity.getCustomerId( ) ) ), "Get qualified identity " + suspiciousIdentity.getCustomerId(), this);
                        final Map<String, QualifiedIdentitySearchResult> result = duplicateSearchResponseDelayedNetworkService.call(() -> SearchDuplicatesService.instance( ).findDuplicates( identity,
                                Collections.singletonList( processedRule ), Collections.emptyList( ) ), "Get duplicates for identity " + suspiciousIdentity.getCustomerId(), this );
                        final QualifiedIdentitySearchResult duplicates = result.get(processedRule.getCode());
                        final List<IdentityDto> processedIdentities = new ArrayList<>(duplicates.getQualifiedIdentities());
                        processedIdentities.add( identity );

                        if ( processedIdentities.size( ) >= 2 )
                        {
                            /* Order identity list by connected identities, then best quality */
                            final Comparator<IdentityDto> connectedComparator = Comparator.comparing( IdentityDto::isMonParisActive ).reversed( );
                            final Comparator<QualityDefinition> qualityComparator = Comparator.comparingDouble( QualityDefinition::getQuality ).reversed( );
                            final Comparator<IdentityDto> orderingComparator = connectedComparator.thenComparing( IdentityDto::getQuality, qualityComparator );

                            processedIdentities.sort( orderingComparator );

                            this.info( "Found " + processedIdentities.size( ) + " to process" );

                            /* The first identity of the list is the base identity */
                            final IdentityDto primaryIdentity = processedIdentities.get( 0 );
                            processedIdentities.remove( 0 );

                            /* Then find the first identity in the list that is not connected */
                            /* Try to merge */
                            for ( final IdentityDto candidate : processedIdentities )
                            {
                                this.merge( primaryIdentity, candidate, suspiciousIdentity.getCustomerId( ), author );
                            }
                        }
                        else
                        {
                            final String log = "There is no duplicates to process for suspicious identity with customer ID "
                                    + suspiciousIdentity.getCustomerId( ) + ". Suspicious identity removed from database";
                            this.info( log );
                            SuspiciousIdentityHome.remove( suspiciousIdentity.getId( ) );
                        }
                    }
                    else
                    {
                        this.info( "Suspicious identity with customer ID " + suspiciousIdentity.getCustomerId( ) + " is locked" );
                    }
                }
            }
            else
            {
                this.info( "No rule found with name " + ruleCode );
            }
        }
        catch( final ResourceNotFoundException e )
        {
            this.info( "Could not fetch rule " + ruleCode + " :" + e.getMessage( ) );
        }
        catch( final IdentityStoreException e )
        {
            this.info( "Could not resolve suspicious identity :" + e.getMessage( ) );
        }

        stopWatch.stop( );
        final String duration = DurationFormatUtils.formatDurationWords( stopWatch.getTime( ), true, true );
        this.info( nbIdentitiesMerged + " identities merged. Execution time " + duration );

    }

    private RequestAuthor buildAuthor( long time )
    {
        final RequestAuthor author = new RequestAuthor( );
        author.setType( AuthorType.application );
        author.setName( authorName + DateFormatUtils.ISO_8601_EXTENDED_DATETIME_FORMAT.format( time ) );
        return author;
    }

    private void merge( final IdentityDto primaryIdentity, final IdentityDto candidate, final String suspiciousCustomerId, final RequestAuthor author )
            throws IdentityStoreException
    {
        /* Cannot merge connected identity */
        if ( this.canMerge( primaryIdentity, candidate ) )
        {
            /* Lock current */
            SuspiciousIdentityHome.manageLock( suspiciousCustomerId, "IdentityDuplicatesResolutionDaemon", AuthorType.admin.name( ), true );
            this.info( "Lock suspicious identity with customer ID " + suspiciousCustomerId );

            /* Get all attributes of secondary that do not exist in primary */
            final Predicate<AttributeDto> selectNonExistingAttribute = candidateAttribute -> primaryIdentity.getAttributes( ).stream( )
                    .noneMatch( primaryAttribute -> Objects.equals( primaryAttribute.getKey( ), candidateAttribute.getKey( ) ) );
            final List<AttributeDto> attributesToCreate = candidate.getAttributes( ).stream( ).filter( selectNonExistingAttribute )
                    .collect( Collectors.toList( ) );
            if ( !attributesToCreate.isEmpty( ) )
            {
                final String log = "Attribute list to create " + attributesToCreate.stream( ).map( AttributeDto::getKey ).collect( Collectors.joining( "," ) );
                this.info( log );
            }

            /* Get all attributes of secondary that exist with higher certificate */
            final Predicate<AttributeDto> selectAttributesToOverride = candidateAttribute -> primaryIdentity.getAttributes( ).stream( )
                    .anyMatch( primaryAttribute -> primaryAttribute.getKey( ).equals( candidateAttribute.getKey( ) )
                            && primaryAttribute.getValue( ).equalsIgnoreCase( candidateAttribute.getValue( ) )
                            && primaryAttribute.getCertificationLevel( ) < candidateAttribute.getCertificationLevel( ) );
            final List<AttributeDto> attributesToOverride = candidate.getAttributes( ).stream( ).filter( selectAttributesToOverride )
                    .collect( Collectors.toList( ) );
            if ( !attributesToOverride.isEmpty( ) )
            {
                final String log = "Attribute list to create "
                        + attributesToOverride.stream( ).map( AttributeDto::getKey ).collect( Collectors.joining( "," ) );
                this.info( log );
            }

            final IdentityDto identity;
            if ( !attributesToCreate.isEmpty( ) || !attributesToOverride.isEmpty( ) )
            {
                identity = new IdentityDto( );
                identity.getAttributes( ).addAll( attributesToCreate );
                identity.getAttributes( ).addAll( attributesToOverride );
            } else {
                identity = null;
            }
            final Pair<Identity, List<AttributeStatus>> mergeResult =
                    IdentityService.instance().merge(DtoConverter.convertDtoToIdentity(primaryIdentity), DtoConverter.convertDtoToIdentity(candidate), identity,
                            ruleCode, author, clientCode, Collections.emptyList( ));
            nbIdentitiesMerged++;

            final boolean fullSuccess = mergeResult.getValue( ).stream( ).map( AttributeStatus::getStatus )
                    .allMatch( status -> status.getType( ) == AttributeChangeStatusType.SUCCESS );
            this.info( "Identities merged with status " + ( fullSuccess ? ResponseStatusType.SUCCESS : ResponseStatusType.INCOMPLETE_SUCCESS ) );

            /* Unlock current */
            SuspiciousIdentityHome.manageLock( suspiciousCustomerId, "IdentityDuplicatesResolutionDaemon", AuthorType.admin.name( ), false );
            this.info( "Unlock suspicious identity with customer ID " + suspiciousCustomerId );
        }
        else
        {
            final String err = "Candidate identity with customer ID " + candidate.getCustomerId( ) + " is not eligible to automatic merge.";
            this.info( err );
        }
    }

    private boolean canMerge( final IdentityDto primaryIdentity, final IdentityDto candidate )
    {
        if ( candidate.isMonParisActive( ) )
        {
            return false;
        }
        // LUT-28116 - If the primary identity is connected, it must have a minimum certification level (default : >= 500 (ORIG1))
        if (primaryIdentity.isMonParisActive() && !hasMinimumCertification(primaryIdentity))
        {
            return false;
        }
        return isStrictDuplicate( primaryIdentity, candidate );
    }

    private boolean isStrictDuplicate( final IdentityDto primaryIdentity, final IdentityDto candidate )
    {
        final Predicate<AttributeDto> selectNotEqualAttributes = primaryAttribute -> candidate.getAttributes( ).stream( )
                .anyMatch( candidateAttribute -> candidateAttribute.getKey( ).equals( primaryAttribute.getKey( ) )
                        && !candidateAttribute.getValue( ).equalsIgnoreCase( primaryAttribute.getValue( ) ) );
        return primaryIdentity.getAttributes( ).stream( ).noneMatch( selectNotEqualAttributes );
    }

    private boolean hasMinimumCertification(final IdentityDto primaryIdentity)
    {
        final int requiredCertificationLevel =
                AppPropertiesService.getPropertyInt("daemon.identityDuplicatesResolutionDaemon.primary.identity.connected.min.certification.level", 500);
        final List<String> pivotAttributeKeys =
                IdentityAttributeService.instance().getPivotAttributeKeys().stream().map(AttributeKey::getKeyName).collect(Collectors.toList());
        final int lowestPivotCertificationLevel =
                primaryIdentity.getAttributes().stream().filter(a -> pivotAttributeKeys.contains(a.getKey())).mapToInt(AttributeDto::getCertificationLevel)
                               .min().orElse(0);
        return lowestPivotCertificationLevel >= requiredCertificationLevel;
    }
}
