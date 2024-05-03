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
package fr.paris.lutece.plugins.identitystore.modules.quality.listener;

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SearchDuplicatesService;
import fr.paris.lutece.plugins.identitystore.service.IdentityChangeListener;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.DtoConverter;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.history.IdentityChangeType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.QualifiedIdentitySearchResult;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.util.sql.TransactionManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The aim of this listener is to handle {@link fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity} modifications.
 */
public class SuspiciousIdentityListener implements IdentityChangeListener
{
    private static final String SERVICE_NAME = "Elastic Search identity change listener";

    @Override
    public void processIdentityChange( IdentityChangeType identityChangeType, Identity identity, String statusCode, String statusMessage, RequestAuthor author,
            String clientCode, Map<String, String> metadata )
    {
        switch( identityChangeType )
        {
            case UPDATE:
            case CONSOLIDATED:
            case DELETE:
            case MERGED:
                this.handleSuspicion( identity );
            default:
                break;
        }
    }

    /**
     * Check if the given {@link Identity} is a {@link fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity} or not. <br>
     * If it is suspicious, delete from suspicions.
     * 
     * @param identity
     *            the {@link Identity} to handle
     */
    private void handleSuspicion( final Identity identity )
    {
        final boolean suspicious = SuspiciousIdentityHome.hasSuspicious( Collections.singletonList( identity.getCustomerId( ) ) );
        if ( suspicious )
        {
            try
            {
                final SuspiciousIdentity suspiciousIdentity = SuspiciousIdentityHome.selectByCustomerID( identity.getCustomerId( ) );
                final Map<String, QualifiedIdentitySearchResult> duplicateResult = SearchDuplicatesService.instance( ).findDuplicates(
                        DtoConverter.convertIdentityToDto( identity ),
                        Collections.singletonList( DuplicateRuleService.instance( ).get( suspiciousIdentity.getDuplicateRuleCode( ) ) ),
                        Collections.emptyList( ) );
                TransactionManager.beginTransaction( null );
                if ( duplicateResult == null
                        || duplicateResult.values( ).stream( ).map( QualifiedIdentitySearchResult::getQualifiedIdentities ).allMatch( List::isEmpty ) )
                {
                    SuspiciousIdentityHome.remove( identity.getCustomerId( ) );
                }
                TransactionManager.commitTransaction( null );
            }
            catch( final Exception e )
            {
                TransactionManager.rollBack( null );
                AppLogService.error( "SuspiciousIdentityListener :: Could not handle identity " + identity.getCustomerId( ) + " : " + e.getMessage( ) );
            }
        }
    }

    @Override
    public String getName( )
    {
        return SERVICE_NAME;
    }
}
