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
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleNotFoundException;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityService;
import fr.paris.lutece.plugins.identitystore.utils.Batch;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.QualifiedIdentity;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.portal.service.daemon.Daemon;
import fr.paris.lutece.portal.service.util.AppLogService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This task identifies {@link Identity} with potential duplicates. The best quality identity is saved in the database to be processed later.
 */
public class IdentityDuplicatesDaemon extends Daemon
{
    /**
     * {@inheritDoc}
     */
    @Override
    public void run( )
    {
        final StopWatch stopWatch = new StopWatch( );
        stopWatch.start( );
        final StringBuilder logs = new StringBuilder( );
        final String startingMessage = "Starting IdentityDuplicatesDaemon...";
        AppLogService.info( startingMessage );
        logs.append( startingMessage ).append( "\n" );
        final List<DuplicateRule> rules;
        try
        {
            rules = DuplicateRuleService.instance( ).findAll( ).stream( ).filter( DuplicateRule::isDaemon ).collect( Collectors.toList( ) );
        }
        catch( final DuplicateRuleNotFoundException e )
        {
            final String error = "No duplicate rules found in database. Stopping daemon.";
            AppLogService.error( error, e );
            logs.append( error ).append( e.getMessage( ) ).append( "\n" );
            return;
        }
        if ( CollectionUtils.isEmpty( rules ) )
        {
            final String empty = "No existing duplicate rules marked to be used in daemon. Stopping daemon.";
            AppLogService.info( empty );
            logs.append( empty );
            return;
        }

        final String starting = rules.size( ) + " applicable detection rules found. Starting process...";
        AppLogService.info( starting );
        logs.append( starting ).append( "\n" );
        rules.sort( Comparator.comparingInt( DuplicateRule::getPriority ) );

        for ( final DuplicateRule rule : rules )
        {
            try
            {
                this.search( rule, logs );
            }
            catch( IdentityStoreException e )
            {
                AppLogService.error( e );
                logs.append( e.getMessage( ) ).append( "\n" );
            }
        }

        stopWatch.stop( );
        final String duration = DurationFormatUtils.formatDurationWords( stopWatch.getTime( ), true, true );
        final String log = "Execution time " + duration;
        AppLogService.info( log );
        logs.append( log );
        setLastRunLogs( logs.toString( ) );
    }

    /**
     * Search for potential duplicates according to the provided rule.
     * 
     * @param rule
     *            the rule used to search duplicates
     */
    private void search( final DuplicateRule rule, final StringBuilder logs ) throws IdentityStoreException
    {
        final String processing = "-- Processing Rule id = [" + rule.getId( ) + "] code = [" + rule.getCode( ) + "] priority = [" + rule.getPriority( )
                + "] ...";
        AppLogService.info( processing );
        logs.append( processing ).append( "\n" );
        final Batch<String> cuidList = IdentityService.instance( ).getIdentitiesBatchForPotentialDuplicate( rule, 200 );
        if ( cuidList == null || cuidList.isEmpty( ) )
        {
            final String error = "No identities having required attributes and not already suspicious found.";
            AppLogService.info( error );
            logs.append( error ).append( "\n" );
            return;
        }
        final String found = cuidList.totalSize( ) + " identities found. Searching for potential duplicates on those...";
        AppLogService.info( found );
        logs.append( found ).append( "\n" );
        int markedSuspicious = 0;
        for ( final List<String> cuids : cuidList )
        {
            for ( final String cuid : cuids )
            {
                final QualifiedIdentity identity = IdentityService.instance( ).getQualifiedIdentity( cuid );
                final DuplicateSearchResponse duplicates = IdentityService.instance( ).findDuplicates( identity, rule.getCode( ) );
                final int duplicateCount = duplicates != null ? duplicates.getIdentities( ).size( ) : 0;
                if ( duplicateCount > 0 )
                {
                    final List<QualifiedIdentity> processedIdentities = new ArrayList<>( duplicates.getIdentities( ) );
                    processedIdentities.add( identity );
                    final List<String> customerIds = processedIdentities.stream( ).map( QualifiedIdentity::getCustomerId ).collect( Collectors.toList( ) );
                    if ( !SuspiciousIdentityService.instance( ).hasSuspicious( customerIds ) )
                    {
                        final SuspiciousIdentityChangeResponse response = new SuspiciousIdentityChangeResponse( );
                        final SuspiciousIdentityChangeRequest request = new SuspiciousIdentityChangeRequest( );
                        // final QualifiedIdentity bestIdentity = processedIdentities.stream( ).max( Comparator.comparing( QualifiedIdentity::getQuality ) )
                        // .orElseThrow( ( ) -> new IdentityStoreException( "Could not find best quality" ) );
                        request.setSuspiciousIdentity( new SuspiciousIdentityDto( ) );
                        // request.getSuspiciousIdentity( ).setCustomerId( bestIdentity.getCustomerId( ) );
                        request.getSuspiciousIdentity( ).setCustomerId( cuid );
                        request.getSuspiciousIdentity( ).setDuplicationRuleCode( rule.getCode( ) );
                        SuspiciousIdentityService.instance( ).create( request, null, response );
                        markedSuspicious++;
                    }
                }
            }
        }
        final String marked = markedSuspicious + " identities have been marked as suspicious.";
        AppLogService.info( marked );
        logs.append( marked ).append( "\n" );
    }
}
