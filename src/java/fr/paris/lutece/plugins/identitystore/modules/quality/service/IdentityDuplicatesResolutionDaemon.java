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
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.QualifiedIdentity;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.portal.service.daemon.Daemon;
import fr.paris.lutece.portal.service.util.AppLogService;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * This task attempts to automatically resolve duplicates.
 */
public class IdentityDuplicatesResolutionDaemon extends Daemon
{
    @Override
    public void run( )
    {
        final StopWatch stopWatch = new StopWatch( );
        stopWatch.start( );
        final StringBuilder logs = new StringBuilder( );
        final String startingMessage = "Starting IdentityDuplicatesResolutionDaemon...";
        AppLogService.info( startingMessage );
        logs.append( startingMessage ).append( "\n" );

        try
        {
            /* Get rule that identifies strict duplicates */
            final DuplicateRule rgGenStrictDoublon01 = DuplicateRuleService.instance( ).get( "RG_GEN_StrictDoublon_01" );
            /* Get a batch of suspicious identities that match the rule */
            final List<SuspiciousIdentity> listSuspiciousIdentities = SuspiciousIdentityHome.getSuspiciousIdentitysList( rgGenStrictDoublon01.getId( ), 10 );
            for ( final SuspiciousIdentity suspiciousIdentity : listSuspiciousIdentities )
            {
                /* Ignore locked suspicions */
                if ( suspiciousIdentity.getLock( ) == null || !suspiciousIdentity.getLock( ).isLocked( ) )
                {
                    /* Get and sort identities to process */
                    final QualifiedIdentity identity = IdentityService.instance( ).getQualifiedIdentity( suspiciousIdentity.getCustomerId( ) );
                    final DuplicateSearchResponse duplicateSearchResponse = IdentityService.instance( ).findDuplicates( identity,
                            rgGenStrictDoublon01.getId( ) );
                    final List<QualifiedIdentity> processedIdentities = new ArrayList<>( );
                    processedIdentities.addAll( duplicateSearchResponse.getIdentities( ) );
                    processedIdentities.add( identity );

                    /* Order identity list by connected identities, then best quality */
                    final Comparator<QualifiedIdentity> connectedComparator = Comparator.comparing( QualifiedIdentity::isMonParisActive ).reversed( );
                    final Comparator<QualifiedIdentity> qualityComparator = Comparator.comparingDouble( QualifiedIdentity::getQuality ).reversed( );
                    final Comparator<QualifiedIdentity> orderingComparator = connectedComparator.thenComparing( qualityComparator );
                    processedIdentities.sort( orderingComparator );

                    final String log = "Found " + processedIdentities.size( ) + " to process";
                    AppLogService.info( log );
                    logs.append( log ).append( "\n" );
                    /* Lock current */
                    // SuspiciousIdentityHome.manageLock( suspiciousIdentity.getCustomerId( ), "IdentityDuplicatesResolutionDaemon", AuthorType.admin.name( ),
                    // true );

                }
            }
        }
        catch( DuplicateRuleNotFoundException e )
        {
            final String log = "Could not fetch rule RG_GEN_StrictDoublon_01 :" + e.getMessage( );
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
        final String log = "Execution time " + duration;
        AppLogService.info( log );
        logs.append( log );
        setLastRunLogs( logs.toString( ) );
    }
}
