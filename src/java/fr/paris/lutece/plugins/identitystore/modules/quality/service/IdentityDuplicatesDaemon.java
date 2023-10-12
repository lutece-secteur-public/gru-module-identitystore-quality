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

import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRuleHome;
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
import fr.paris.lutece.portal.service.daemon.Daemon;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.log4j.Logger;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This task identifies {@link Identity} with potential duplicates. The best quality identity is saved in the database to be processed later.
 */
public class IdentityDuplicatesDaemon extends Daemon
{
    private final static Logger _logger = Logger.getLogger(IdentityDuplicatesDaemon.class);
    private final String authorName = AppPropertiesService.getProperty( "daemon.identityDuplicatesDaemon.author.name" );
    private final String clientCode = AppPropertiesService.getProperty( "daemon.identityDuplicatesDaemon.client.code" );

    /**
     * {@inheritDoc}
     */
    @Override
    public void run( )
    {
        final StopWatch stopWatch = new StopWatch( );
        stopWatch.start( );
        final RequestAuthor author = this.buildAuthor( stopWatch.getStartTime( ) );
        final StringBuilder logs = new StringBuilder( );
        final String startingMessage = "Starting IdentityDuplicatesDaemon...";
        _logger.info( startingMessage );
        logs.append( startingMessage ).append( "\n" );
        setLastRunLogs( logs.toString( ) );
        final List<DuplicateRule> rules;
        try
        {
            rules = DuplicateRuleService.instance( ).findAll( ).stream( ).filter( DuplicateRule::isDaemon ).collect( Collectors.toList( ) );
        }
        catch( final DuplicateRuleNotFoundException e )
        {
            final String error = "No duplicate rules found in database. Stopping daemon.";
            _logger.error( error, e );
            logs.append( error ).append( e.getMessage( ) ).append( "\n" );
            setLastRunLogs( logs.toString( ) );
            return;
        }
        if ( CollectionUtils.isEmpty( rules ) )
        {
            final String empty = "No existing duplicate rules marked to be used in daemon. Stopping daemon.";
            _logger.info( empty );
            logs.append( empty );
            setLastRunLogs( logs.toString( ) );
            return;
        }

        final String starting = rules.size( ) + " applicable detection rules found. Starting process...";
        _logger.info( starting );
        logs.append( starting ).append( "\n" );
        setLastRunLogs( logs.toString( ) );
        rules.sort( Comparator.comparingInt( DuplicateRule::getPriority ) );

        for ( final DuplicateRule rule : rules )
        {
            try
            {
                this.search( rule, logs, author );
            }
            catch( IdentityStoreException e )
            {
                _logger.error( e );
                logs.append( e.getMessage( ) ).append( "\n" );
                setLastRunLogs( logs.toString( ) );
            }

            rule.setDaemonLastExecDate( Timestamp.from( ZonedDateTime.now( ZoneId.systemDefault( ) ).toInstant( ) ) );
            DuplicateRuleHome.update( rule );
        }

        stopWatch.stop( );
        final String duration = DurationFormatUtils.formatDurationWords( stopWatch.getTime( ), true, true );
        final String log = "Execution time " + duration;
        _logger.info( log );
        logs.append( log );
        setLastRunLogs( logs.toString( ) );
    }

    /**
     * Search for potential duplicates according to the provided rule.
     * 
     * @param rule
     *            the rule used to search duplicates
     */
    private void search( final DuplicateRule rule, final StringBuilder logs, final RequestAuthor author ) throws IdentityStoreException
    {
        final String processing = "-- Processing Rule id = [" + rule.getId( ) + "] code = [" + rule.getCode( ) + "] priority = [" + rule.getPriority( )
                + "] ...";
        _logger.info( processing );
        logs.append( processing ).append( "\n" );
        setLastRunLogs( logs.toString( ) );
        final Batch<String> cuidList = IdentityService.instance( ).getIdentitiesBatchForPotentialDuplicate( rule, 200 );
        if ( cuidList == null || cuidList.isEmpty( ) )
        {
            final String error = "No identities having required attributes and not already suspicious found.";
            _logger.info( error );
            logs.append( error ).append( "\n" );
            setLastRunLogs( logs.toString( ) );
            return;
        }
        final String found = cuidList.totalSize( ) + " identities found. Searching for potential duplicates on those...";
        _logger.info( found );
        logs.append( found ).append( "\n" );
        setLastRunLogs( logs.toString( ) );
        int markedSuspicious = 0;
        for ( final List<String> cuids : cuidList )
        {
            for ( final String cuid : cuids )
            {
                if ( processIdentity( cuid, rule, author ) )
                {
                    markedSuspicious++;
                }
            }
        }
        final String marked = markedSuspicious + " identities have been marked as suspicious.";
        _logger.info( marked );
        logs.append( marked ).append( "\n" );
        setLastRunLogs( logs.toString( ) );
    }

    private boolean processIdentity( final String cuid, final DuplicateRule rule, final RequestAuthor author )
    {
        try
        {
            final IdentityDto identity = IdentityService.instance( ).getQualifiedIdentity( cuid );
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
                    final String bestQualityCuid = processedIdentities.stream( ).sorted( Comparator.comparingDouble( i -> i.getQuality( ).getQuality( ) ) )
                            .map( IdentityDto::getCustomerId ).findFirst( ).get( );

                    final SuspiciousIdentityChangeResponse response = new SuspiciousIdentityChangeResponse( );
                    final SuspiciousIdentityChangeRequest request = new SuspiciousIdentityChangeRequest( );
                    request.setSuspiciousIdentity( new SuspiciousIdentityDto( ) );
                    request.getSuspiciousIdentity( ).setCustomerId( bestQualityCuid );
                    request.getSuspiciousIdentity( ).setDuplicationRuleCode( rule.getCode( ) );
                    request.getSuspiciousIdentity( ).getMetadata( ).putAll( duplicates.getMetadata( ) );
                    SuspiciousIdentityService.instance( ).create( request, clientCode, author, response );
                    return true;
                }
            }
        }
        catch( Exception e )
        {
            _logger.error( e.getMessage( ), e );
        }
        return false;
    }

    private RequestAuthor buildAuthor( long time )
    {
        final RequestAuthor author = new RequestAuthor( );
        author.setType( AuthorType.application );
        author.setName( authorName + DateFormatUtils.ISO_8601_EXTENDED_DATETIME_FORMAT.format( time ) );
        return author;
    }
}
