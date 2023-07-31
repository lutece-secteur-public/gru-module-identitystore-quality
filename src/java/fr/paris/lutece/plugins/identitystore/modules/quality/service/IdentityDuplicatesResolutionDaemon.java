/*
 * Copyright (c) 2002-2023, City of Paris
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

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleNotFoundException;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AuthorType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.Identity;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.merge.IdentityMergeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.merge.IdentityMergeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.CertifiedAttribute;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.QualifiedIdentity;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.portal.service.daemon.Daemon;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This task attempts to automatically resolve duplicates.
 */
public class IdentityDuplicatesResolutionDaemon extends Daemon
{
    private final String clientCode = AppPropertiesService.getProperty( "daemon.identityDuplicatesResolutionDaemon.client.code" );
    private final String authorName = AppPropertiesService.getProperty( "daemon.identityDuplicatesResolutionDaemon.author.name" );
    private int nbIdentitiesMerged = 0;

    @Override
    public void run( )
    {
        final StopWatch stopWatch = new StopWatch( );
        stopWatch.start( );
        final RequestAuthor author = this.buildAuthor( stopWatch.getStartTime( ) );
        final StringBuilder logs = new StringBuilder( );
        final String startingMessage = "Starting IdentityDuplicatesResolutionDaemon...";
        AppLogService.info( startingMessage );
        logs.append( startingMessage ).append( "\n" );

        nbIdentitiesMerged = 0;
        final String ruleCode = AppPropertiesService.getProperty( "daemon.identityDuplicatesResolutionDaemon.rule.code" );
        final int limit = AppPropertiesService.getPropertyInt( "daemon.identityDuplicatesResolutionDaemon.suspicious.limite", 1 );

        try
        {
            /* Get rule that identifies strict duplicates */
            final DuplicateRule processedRule = DuplicateRuleService.instance( ).get( ruleCode );
            if ( processedRule != null )
            {
                final String ruleMessage = "Processing rule " + ruleCode;
                AppLogService.info( ruleMessage );
                logs.append( ruleMessage ).append( "\n" );

                /* Get a batch of suspicious identities that match the rule */
                final List<SuspiciousIdentity> listSuspiciousIdentities = SuspiciousIdentityHome.getSuspiciousIdentitysList( processedRule.getCode( ), limit,
                        null );
                for ( final SuspiciousIdentity suspiciousIdentity : listSuspiciousIdentities )
                {
                    /* Ignore locked suspicions */
                    if ( suspiciousIdentity.getLock( ) == null || !suspiciousIdentity.getLock( ).isLocked( ) )
                    {
                        /* Get and sort identities to process */
                        final QualifiedIdentity identity = IdentityService.instance( ).getQualifiedIdentity( suspiciousIdentity.getCustomerId( ) );
                        final DuplicateSearchResponse duplicateSearchResponse = IdentityService.instance( ).findDuplicates( identity,
                                processedRule.getCode( ) );
                        final List<QualifiedIdentity> processedIdentities = new ArrayList<>( duplicateSearchResponse.getIdentities( ) );
                        processedIdentities.add( identity );

                        if ( processedIdentities.size( ) >= 2 )
                        {
                            /* Order identity list by connected identities, then best quality */
                            final Comparator<QualifiedIdentity> connectedComparator = Comparator.comparing( QualifiedIdentity::isMonParisActive ).reversed( );
                            final Comparator<QualifiedIdentity> qualityComparator = Comparator.comparingDouble( QualifiedIdentity::getQuality ).reversed( );
                            final Comparator<QualifiedIdentity> orderingComparator = connectedComparator.thenComparing( qualityComparator );
                            processedIdentities.sort( orderingComparator );

                            final String log = "Found " + processedIdentities.size( ) + " to process";
                            AppLogService.info( log );
                            logs.append( log ).append( "\n" );

                            /* The first identity of the list is the base identity */
                            final QualifiedIdentity primaryIdentity = processedIdentities.get( 0 );
                            processedIdentities.remove( 0 );

                            /* Then find the first identity in the list that is not connected */
                            /* Try to merge */
                            for ( final QualifiedIdentity candidate : processedIdentities )
                            {
                                logs.append( this.merge( primaryIdentity, candidate, suspiciousIdentity.getCustomerId( ), author ) );
                            }
                        }
                        else
                        {
                            final String log = "There is no duplicates to process for suspicious identity with customer ID "
                                    + suspiciousIdentity.getCustomerId( ) + ". Suspicious identity removed from database";
                            AppLogService.info( log );
                            logs.append( log ).append( "\n" );
                            SuspiciousIdentityHome.remove( suspiciousIdentity.getId( ) );
                        }
                    }
                    else
                    {
                        final String log = "Suspicious identity with customer ID " + suspiciousIdentity.getCustomerId( ) + " is locked";
                        AppLogService.info( log );
                        logs.append( log ).append( "\n" );
                    }
                }
            }
            else
            {
                final String ruleMessage = "No rule found with name " + ruleCode;
                AppLogService.info( ruleMessage );
                logs.append( ruleMessage ).append( "\n" );
            }
        }
        catch( DuplicateRuleNotFoundException e )
        {
            final String log = "Could not fetch rule " + ruleCode + " :" + e.getMessage( );
            AppLogService.info( log );
            logs.append( log );
        }
        catch( IdentityStoreException e )
        {
            final String log = "Could not resolve suspicious identity :" + e.getMessage( );
            AppLogService.info( log );
            logs.append( log );
        }

        stopWatch.stop( );
        final String duration = DurationFormatUtils.formatDurationWords( stopWatch.getTime( ), true, true );
        final String log = nbIdentitiesMerged + " identities merged. Execution time " + duration;
        AppLogService.info( log );
        logs.append( log );
        setLastRunLogs( logs.toString( ) );
    }

    private RequestAuthor buildAuthor( long time )
    {
        final RequestAuthor author = new RequestAuthor( );
        author.setType( AuthorType.application );
        author.setName( authorName + DateFormatUtils.ISO_8601_EXTENDED_DATETIME_FORMAT.format( time ) );
        return author;
    }

    private String merge( final QualifiedIdentity primaryIdentity, final QualifiedIdentity candidate, final String suspiciousCustomerId,
            final RequestAuthor author ) throws IdentityStoreException
    {
        final StringBuilder logs = new StringBuilder( );
        /* Cannot merge connected identity */
        if ( this.canMerge( primaryIdentity, candidate ) )
        {
            /* Lock current */
            SuspiciousIdentityHome.manageLock( suspiciousCustomerId, "IdentityDuplicatesResolutionDaemon", AuthorType.admin.name( ), true );
            final String lock = "Lock suspicious identity with customer ID " + suspiciousCustomerId;
            AppLogService.info( lock );
            logs.append( lock ).append( "\n" );

            final IdentityMergeRequest request = new IdentityMergeRequest( );
            request.setOrigin( author );
            request.setPrimaryCuid( primaryIdentity.getCustomerId( ) );
            request.setSecondaryCuid( candidate.getCustomerId( ) );

            /* Get all attributes of secondary that do not exist in primary */
            final Predicate<CertifiedAttribute> selectNonExistingAttribute = candidateAttribute -> primaryIdentity.getAttributes( ).stream( )
                    .noneMatch( primaryAttribute -> Objects.equals( primaryAttribute.getKey( ), candidateAttribute.getKey( ) ) );
            final List<CertifiedAttribute> attributesToCreate = candidate.getAttributes( ).stream( ).filter( selectNonExistingAttribute )
                    .collect( Collectors.toList( ) );
            if ( !attributesToCreate.isEmpty( ) )
            {
                final String log = "Attribute list to create "
                        + attributesToCreate.stream( ).map( CertifiedAttribute::getKey ).collect( Collectors.joining( "," ) );
                AppLogService.info( log );
                logs.append( log ).append( "\n" );
            }

            /* Get all attributes of secondary that exist with higher certificate */
            final Predicate<CertifiedAttribute> selectAttributesToOverride = candidateAttribute -> primaryIdentity.getAttributes( ).stream( )
                    .anyMatch( primaryAttribute -> primaryAttribute.getKey( ).equals( candidateAttribute.getKey( ) )
                            && primaryAttribute.getValue( ).equalsIgnoreCase( candidateAttribute.getValue( ) )
                            && primaryAttribute.getCertificationLevel( ) < candidateAttribute.getCertificationLevel( ) );
            final List<CertifiedAttribute> attributesToOverride = candidate.getAttributes( ).stream( ).filter( selectAttributesToOverride )
                    .collect( Collectors.toList( ) );
            if ( !attributesToOverride.isEmpty( ) )
            {
                final String log = "Attribute list to create "
                        + attributesToOverride.stream( ).map( CertifiedAttribute::getKey ).collect( Collectors.joining( "," ) );
                AppLogService.info( log );
                logs.append( log ).append( "\n" );
            }

            if ( !attributesToCreate.isEmpty( ) || !attributesToOverride.isEmpty( ) )
            {
                final Identity identity = new Identity( );
                request.setIdentity( identity );
                identity.getAttributes( ).addAll( this.convertAttributeList( attributesToCreate ) );
                identity.getAttributes( ).addAll( this.convertAttributeList( attributesToOverride ) );
            }
            final IdentityMergeResponse response = new IdentityMergeResponse( );
            IdentityService.instance( ).merge( request, clientCode, response );
            nbIdentitiesMerged++;
            final String log = "Identities merged with status " + response.getStatus( ).name( );
            AppLogService.info( log );
            logs.append( log ).append( "\n" );

            /* Unlock current */
            SuspiciousIdentityHome.manageLock( suspiciousCustomerId, "IdentityDuplicatesResolutionDaemon", AuthorType.admin.name( ), false );
            final String unlock = "Unlock suspicious identity with customer ID " + suspiciousCustomerId;
            AppLogService.info( unlock );
            logs.append( unlock ).append( "\n" );
        }
        else
        {
            final String err = "Candidate identity with customer ID " + candidate.getCustomerId( ) + " is not eligible to automatic merge.";
            AppLogService.info( err );
            logs.append( err ).append( "\n" );
        }
        return logs.toString( );
    }

    private boolean canMerge( final QualifiedIdentity primaryIdentity, final QualifiedIdentity candidate )
    {
        if ( candidate.isMonParisActive( ) )
        {
            return false;
        }
        return isStrictDuplicate( primaryIdentity, candidate );
    }

    private boolean isStrictDuplicate( final QualifiedIdentity primaryIdentity, final QualifiedIdentity candidate )
    {
        final Predicate<CertifiedAttribute> selectNotEqualAttributes = primaryAttribute -> candidate.getAttributes( ).stream( )
                .anyMatch( candidateAttribute -> candidateAttribute.getKey( ).equals( primaryAttribute.getKey( ) )
                        && !candidateAttribute.getValue( ).equalsIgnoreCase( primaryAttribute.getValue( ) ) );
        return primaryIdentity.getAttributes( ).stream( ).noneMatch( selectNotEqualAttributes );
    }

    private List<fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.CertifiedAttribute> convertAttributeList(
            List<CertifiedAttribute> attributesToOverride )
    {
        return attributesToOverride.stream( ).map( attributeToOverride -> {
            final fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.CertifiedAttribute requestAttribute = new fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.CertifiedAttribute( );
            requestAttribute.setKey( attributeToOverride.getKey( ) );
            requestAttribute.setValue( attributeToOverride.getValue( ) );
            requestAttribute.setCertificationProcess( attributeToOverride.getCertifier( ) );
            requestAttribute.setCertificationDate( attributeToOverride.getCertificationDate( ) );
            return requestAttribute;
        } ).collect( Collectors.toList( ) );
    }
}
