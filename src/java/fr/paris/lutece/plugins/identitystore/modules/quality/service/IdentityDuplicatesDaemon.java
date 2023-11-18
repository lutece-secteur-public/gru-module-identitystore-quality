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
import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRuleHome;
import fr.paris.lutece.plugins.identitystore.service.daemon.LoggingDaemon;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleNotFoundException;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityService;
import fr.paris.lutece.plugins.identitystore.utils.Batch;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AuthorType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
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
import java.util.stream.Collectors;

/**
 * This task identifies {@link Identity} with potential duplicates. The best quality identity is saved in the database to be processed later.<br/>
 * This daemon is also deleting expired suspicions.
 */
public class IdentityDuplicatesDaemon extends LoggingDaemon
{
    private static final String clientCode = AppPropertiesService.getProperty( "daemon.identityDuplicatesDaemon.client.code" );

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
            this.error( "Error occured while purging expired suspicions : " + e.getMessage( ) );
            this.info( "Continuing..." );
        }
        final List<DuplicateRule> rules;
        try
        {
            rules = DuplicateRuleService.instance( ).findAll( ).stream( ).filter( DuplicateRule::isDaemon ).collect( Collectors.toList( ) );
        }
        catch( final DuplicateRuleNotFoundException e )
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
            try
            {
                this.search( rule );
            }
            catch( IdentityStoreException e )
            {
                this.error( "An error occured during treatment: " + e.getMessage( ) );
            }

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
    private void search( final DuplicateRule rule ) throws IdentityStoreException
    {
        final String processing = "-- Processing Rule id = [" + rule.getId( ) + "] code = [" + rule.getCode( ) + "] priority = [" + rule.getPriority( )
                + "] ...";
        this.info( processing );
        final Batch<IdentityDto> identityBatch = IdentityService.instance( ).getIdentitiesBatchForPotentialDuplicate( rule, 200 );
        if ( identityBatch == null || identityBatch.isEmpty( ) )
        {
            this.error( "No identities having required attributes and not already suspicious found." );
            return;
        }

        this.info( identityBatch.totalSize( ) + " identities found. Searching for potential duplicates on those..." );
        int markedSuspicious = 0;
        for ( final List<IdentityDto> identities : identityBatch )
        {
            for ( final IdentityDto identity : identities )
            {
                if ( processIdentity( identity, rule, author ) )
                {
                    markedSuspicious++;
                }
            }
        }
        this.info( markedSuspicious + " identities have been marked as suspicious." );
    }

    private boolean processIdentity( final IdentityDto identity, final DuplicateRule rule, final RequestAuthor author )
    {
        try
        {
            final DuplicateSearchResponse duplicates = SearchDuplicatesService.instance( ).findDuplicates( identity,
                    Collections.singletonList( rule.getCode( ) ) );
            final int duplicateCount = duplicates != null ? duplicates.getIdentities( ).size( ) : 0;
            if ( duplicateCount > 0 )
            {
                final List<IdentityDto> processedIdentities = new ArrayList<>( duplicates.getIdentities( ) );
                processedIdentities.add( identity );
                final List<String> customerIds = processedIdentities.stream( ).map( IdentityDto::getCustomerId ).collect( Collectors.toList( ) );
                if ( !SuspiciousIdentityService.instance( ).hasSuspicious( customerIds ) )
                {
                    final SuspiciousIdentityChangeResponse response = new SuspiciousIdentityChangeResponse( );
                    final SuspiciousIdentityChangeRequest request = new SuspiciousIdentityChangeRequest( );
                    request.setSuspiciousIdentity( new SuspiciousIdentityDto( ) );
                    request.getSuspiciousIdentity( ).setCustomerId( identity.getCustomerId( ) );
                    request.getSuspiciousIdentity( ).setDuplicationRuleCode( rule.getCode( ) );
                    request.getSuspiciousIdentity( ).getMetadata( ).putAll( duplicates.getMetadata( ) );
                    SuspiciousIdentityService.instance( ).create( request, clientCode, author, response );
                    return true;
                }
            }
        }
        catch( final Exception e )
        {
            this.error( "An error occurred during duplicate search for identity" + identity.getCustomerId( ) + " and rule " + rule.getCode( ) + " : "
                    + e.getMessage( ) );
        }
        return false;
    }

    /**
     * Purges the existing suspicious identities by deleting those that don't have duplicates anymore.
     */
    private void purgeExpiredSuspicions( ) throws IdentityStoreException
    {
        this.info( "Starting purge suspicions process..." );

        final List<SuspiciousIdentity> suspiciousIdentitysList = SuspiciousIdentityHome.getSuspiciousIdentitysList( null, 500, null );
        int purgeCount = 0;
        for ( final SuspiciousIdentity suspicious : suspiciousIdentitysList )
        {
            final IdentityDto identity = IdentityService.instance( ).search( suspicious.getCustomerId( ) );
            final DuplicateSearchResponse duplicates = SearchDuplicatesService.instance( ).findDuplicates( identity,
                    Collections.singletonList( suspicious.getDuplicateRuleCode( ) ) );
            if ( duplicates.getIdentities( ).isEmpty( ) )
            {
                SuspiciousIdentityHome.remove( suspicious.getCustomerId( ) );
                purgeCount++;
            }
        }

        this.info( "Purge process ended with " + purgeCount + " deleted suspicions." );
    }
}
