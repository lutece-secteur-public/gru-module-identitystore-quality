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
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRuleHome;
import fr.paris.lutece.plugins.identitystore.service.daemon.LoggingDaemon;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityService;
import fr.paris.lutece.plugins.identitystore.utils.Batch;
import fr.paris.lutece.plugins.identitystore.utils.Maps;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.DtoConverter;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AuthorType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.QualifiedIdentitySearchResult;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceNotFoundException;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This task identifies {@link Identity} with potential duplicates. The best quality identity is saved in the database to be processed later.<br/>
 * This daemon is also deleting expired suspicions.
 */
public class IdentityDuplicatesDaemon extends LoggingDaemon
{
    private static final String clientCode = AppPropertiesService.getProperty( "daemon.identityDuplicatesDaemon.client.code" );
    private static final Integer batchSize = AppPropertiesService.getPropertyInt( "daemon.identityDuplicatesDaemon.batch.size", 10 );
    private static final Integer purgeSize = AppPropertiesService.getPropertyInt( "daemon.identityDuplicatesDaemon.purge.size", 500 );

    private static final RequestAuthor author;
    static
    {
        author = new RequestAuthor( );
        author.setType( AuthorType.application );
        author.setName( AppPropertiesService.getProperty( "daemon.identityDuplicatesDaemon.author.name" ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doTask( )
    {
        final StopWatch stopWatch = new StopWatch( );
        stopWatch.start( );
        this.info( "Starting IdentityDuplicatesDaemon..." );

        try
        {
            this.purgeExpiredSuspicions( );
        }
        catch( final IdentityStoreException e )
        {
            this.error( "Error occurred while purging expired suspicions : " + e.getMessage( ) );
            this.info( "Continuing..." );
        }
        final List<DuplicateRule> rules;
        try
        {
            rules = DuplicateRuleService.instance( ).findAll( ).stream( ).filter( rule -> rule != null && rule.isDaemon() && rule.isActive() ).collect( Collectors.toList( ) );
        }
        catch( final ResourceNotFoundException e )
        {
            this.error( "No duplicate rules found in database: " + e.getMessage( ) );
            this.info( "Stopping daemon." );
            return;
        }
        if ( CollectionUtils.isEmpty( rules ) )
        {
            this.error( "No existing duplicate rules marked to be used in daemon. Stopping daemon." );
            return;
        }

        this.info( rules.size( ) + " applicable detection rules found. Starting process..." );
        rules.sort( Comparator.comparingInt( DuplicateRule::getPriority ) );

        for ( final DuplicateRule rule : rules )
        {
            this.processRule( rule );
            rule.setDaemonLastExecDate( Timestamp.from( ZonedDateTime.now( ZoneId.systemDefault( ) ).toInstant( ) ) );
            DuplicateRuleHome.update( rule );
        }

        stopWatch.stop( );
        final String duration = DurationFormatUtils.formatDurationWords( stopWatch.getTime( ), true, true );
        this.info( "Execution time " + duration );
    }

    /**
     * Search for potential duplicates according to the provided rule.
     *
     * @param rule
     *            the rule used to search duplicates
     */
    private void processRule( final DuplicateRule rule )
    {
        try
        {
            final int bddSuspicious = SuspiciousIdentityHome.countSuspiciousIdentity( rule.getId() );
            final String processing = "-- Processing Rule id = [" + rule.getId( ) + "] code = [" + rule.getCode( ) + "] priority = [" + rule.getPriority( )
                    + "] ...";
            this.info( processing );

            if( rule.getDetectionLimit( ) > 0 && bddSuspicious >= rule.getDetectionLimit( ) ) {
                this.info("Rule detection limit (" + rule.getDetectionLimit( ) + ") limit exceeded. Detection count : " + bddSuspicious );
            } else {
                final RetryServiceQuality _retryService = new RetryServiceQuality();
                final Batch<String> cuidBatches = IdentityService.instance( ).getCUIDsBatchForPotentialDuplicate( rule, batchSize );
                if ( cuidBatches == null || cuidBatches.isEmpty( ) )
                {
                    this.error( "No identities having required attributes and not already suspicious found." );
                    return;
                }

                this.info( cuidBatches.totalSize( ) + " identities found. Searching for potential duplicates on those..." );
                int markedSuspicious = bddSuspicious;
                final List<String> enhancerFilter = new ArrayList<>( ); // holds cuids that have been detected as duplicates to reduce iteration
                final List<String> attributesFilter = rule.getCheckedAttributes( ).stream( ).map( AttributeKey::getKeyName ).collect( Collectors.toList( ) );
                detection_loop: for( final List<String> cuids : cuidBatches ) {
                    final List<IdentityDto> identities = IdentityService.instance().search(cuids, attributesFilter).stream().filter( Objects::nonNull ).collect(Collectors.toList());
                    for ( final IdentityDto identity : identities )
                    {
                        if ( !enhancerFilter.contains( identity.getCustomerId( ) ) )
                        {
                            try {
                                final Map<String, QualifiedIdentitySearchResult> result = _retryService.callSearchDuplicateWithRetry(identity,
                                                                                                                                         Collections.singletonList( rule ), Collections.singletonList( "customerId" ));
                                final List<IdentityDto> duplicateList = new ArrayList<>( );
                                if ( result != null )
                                {
                                    result.values( ).stream( ).flatMap( r -> r.getQualifiedIdentities( ).stream( ) ).forEach( duplicate -> {
                                        if ( duplicateList.stream( ).noneMatch( i -> i.getCustomerId( ).equals( duplicate.getCustomerId( ) ) ) )
                                        {
                                            duplicateList.add( duplicate );
                                        }
                                    } );
                                }
                                if ( !duplicateList.isEmpty() )
                                {
                                    this.info( "Identity " + identity.getCustomerId( ) + " has " + duplicateList.size() + " duplicates." );
                                    final List<IdentityDto> processedIdentities = new ArrayList<>( duplicateList );
                                    processedIdentities.add( identity );
                                    final List<String> customerIds = processedIdentities.stream( ).map( IdentityDto::getCustomerId ).collect( Collectors.toList( ) );
                                    if ( !SuspiciousIdentityService.instance( ).hasSuspicious( customerIds ) )
                                    {
                                        final SuspiciousIdentityChangeRequest request = new SuspiciousIdentityChangeRequest( );
                                        request.setSuspiciousIdentity( new SuspiciousIdentityDto( ) );
                                        request.getSuspiciousIdentity( ).setCustomerId( identity.getCustomerId( ) );
                                        request.getSuspiciousIdentity( ).setDuplicationRuleCode( rule.getCode( ) );
                                        result.values( ).forEach( r -> Maps.mergeStringMap(request.getSuspiciousIdentity().getMetadata(), r.getMetadata()));

                                        SuspiciousIdentityService.instance( ).create(request, DtoConverter.convertDtoToIdentity(identity), rule, clientCode, author);
                                        this.info( "Identity " + identity.getCustomerId( ) + " has been marked suspicious." );
                                        markedSuspicious++;
                                    }
                                    enhancerFilter.addAll( customerIds );
                                }

                                if ( rule.getDetectionLimit( ) > 0 && markedSuspicious >= rule.getDetectionLimit( ) )
                                {
                                    this.info("Rule detection limit (" + rule.getDetectionLimit( ) + ") limit exceeded. Detection count : " + markedSuspicious );
                                    break detection_loop;
                                }
                            }
                            catch ( final Exception e )
                            {
                                this.error( "An error occurred during duplicate search for identity" + identity.getCustomerId( ) + " and rule " + rule.getCode( )
                                        + " : " + e.getMessage( ) );
                            }
                        }
                    }
                }
                this.info( markedSuspicious + " identities have been marked as suspicious." );
            }
        }
        catch( final Exception e )
        {
            this.error( "An error occurred during processing of rule " + rule.getCode( ) + " : " + e.getMessage( ) );
        }
    }

    /**
     * Purges the existing suspicious identities by deleting those that don't have duplicates anymore.
     */
    private void purgeExpiredSuspicions( ) throws IdentityStoreException
    {
        this.info( "Starting purge suspicions process..." );

        final RetryServiceQuality _retryService = new RetryServiceQuality();
        final List<SuspiciousIdentity> suspiciousIdentitysList = SuspiciousIdentityHome.getSuspiciousIdentitysList( null, purgeSize, null );
        int purgeCount = 0;
        for ( final SuspiciousIdentity suspicious : suspiciousIdentitysList )
        {
            final IdentityDto identity = IdentityService.instance( ).search( suspicious.getCustomerId( ) );
            if(identity != null)
            {
                final Map<String, QualifiedIdentitySearchResult> result = _retryService.callSearchDuplicateWithRetry(identity,
                                                                                                      Collections.singletonList( DuplicateRuleService.instance( ).get( suspicious.getDuplicateRuleCode( ) ) ), Collections.emptyList());

                if ( result == null || result.values( ).stream( ).map( QualifiedIdentitySearchResult::getQualifiedIdentities ).allMatch( List::isEmpty ) )                {
                    SuspiciousIdentityHome.remove( suspicious.getCustomerId( ) );
                    purgeCount++;
                }
            }
        }

        this.info( "Purge process ended with " + purgeCount + " deleted suspicions." );
    }
}
