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

import fr.paris.lutece.plugins.identitystore.business.application.ClientApplication;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityLockedException;
import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.identity.IdentityHome;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.modules.quality.rs.SuspiciousIdentityMapper;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.listeners.IdentityStoreNotifyListenerService;
import fr.paris.lutece.plugins.identitystore.service.user.InternalUserService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityExcludeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityExcludeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.history.IdentityChangeType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.ResponseStatusFactory;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityNotFoundException;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.portal.service.security.AccessLogService;
import fr.paris.lutece.portal.service.security.AccessLoggerConstants;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.sql.TransactionManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SuspiciousIdentityService
{
    // EVENTS FOR ACCESS LOGGING
    private static final String CREATE_SUSPICIOUS_IDENTITY_EVENT_CODE = "CREATE_SUSPICIOUS_IDENTITY";
    private static final String SEARCH_SUSPICIOUS_IDENTITY_EVENT_CODE = "SEARCH_SUSPICIOUS_IDENTITY";
    private static final String LOCK_SUSPICIOUS_IDENTITY_EVENT_CODE = "LOCK_SUSPICIOUS_IDENTITY";
    private static final String UNLOCK_SUSPICIOUS_IDENTITY_EVENT_CODE = "UNLOCK_SUSPICIOUS_IDENTITY";
    private static final String EXCLUDE_SUSPICIOUS_IDENTITY_EVENT_CODE = "EXCLUDE_SUSPICIOUS_IDENTITY";
    private static final String SPECIFIC_ORIGIN = "BO";

    // SERVICES
    private final String externalDeclarationRuleCode = AppPropertiesService.getProperty( "identitystore-quality.external.duplicates.rule.code" );
    private final IdentityStoreNotifyListenerService _identityStoreNotifyListenerService = IdentityStoreNotifyListenerService.instance( );
    private final InternalUserService _internalUserService = InternalUserService.getInstance( );
    private static SuspiciousIdentityService _instance;

    public static SuspiciousIdentityService instance( )
    {
        if ( _instance == null )
        {
            _instance = new SuspiciousIdentityService( );
        }
        return _instance;
    }

    /**
     * Creates a new {@link SuspiciousIdentity} according to the given {@link SuspiciousIdentityChangeRequest}
     *
     * @param request
     *            the {@link SuspiciousIdentityChangeRequest} holding the parameters of the suspicious identity change request
     * @param clientCode
     *            code of the {@link ClientApplication} requesting the change
     * @param response
     *            the {@link SuspiciousIdentityChangeResponse} holding the status of the execution of the request
     * @return the created {@link SuspiciousIdentity}
     * @throws IdentityStoreException
     *             in case of error
     */
    public void create( final SuspiciousIdentityChangeRequest request, final String clientCode, final RequestAuthor author,
            final SuspiciousIdentityChangeResponse response )
    {
        // TODO check if the application has the right to create a suspicious identity
        /*
         * if ( !_serviceContractService.canCreateSuspiciousIdentity( clientCode ) ) { response.setStatus( IdentityChangeStatus.FAILURE ); response.setMessage(
         * "The client application is not authorized to create an identity." ); return null; }
         */
        TransactionManager.beginTransaction( null );
        boolean marked = false;
        try
        {
            final SuspiciousIdentity suspiciousIdentity = new SuspiciousIdentity( );
            final String requestRuleCode = request.getSuspiciousIdentity( ).getDuplicationRuleCode( );
            final String ruleCode = requestRuleCode != null ? requestRuleCode : externalDeclarationRuleCode;

            final Identity identity = IdentityHome.findByCustomerId( request.getSuspiciousIdentity( ).getCustomerId( ) );
            if ( identity == null )
            {
                response.setStatus( ResponseStatusFactory.notFound( ).setMessageKey( Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND ) );
            }
            else
            {
                final DuplicateRule duplicateRule = DuplicateRuleService.instance( ).get( ruleCode );
                suspiciousIdentity.setDuplicateRuleCode( ruleCode );
                suspiciousIdentity.setIdDuplicateRule( duplicateRule.getId( ) );
                suspiciousIdentity.setCustomerId( request.getSuspiciousIdentity( ).getCustomerId( ) );
                suspiciousIdentity.setCreationDate( Timestamp.from( Instant.now( ) ) );
                suspiciousIdentity.setLastUpdateDate( identity.getLastUpdateDate( ) );

                SuspiciousIdentityHome.create( suspiciousIdentity );

                response.setSuspiciousIdentity( SuspiciousIdentityMapper.toDto( suspiciousIdentity ) );
                response.setStatus( ResponseStatusFactory.success( ).setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );
                marked = true;
            }
            TransactionManager.commitTransaction( null );
            if ( marked )
            {
                final Map<String, String> metadata = new HashMap<>( request.getSuspiciousIdentity( ).getMetadata( ) );
                metadata.put( Constants.METADATA_DUPLICATE_RULE_CODE, ruleCode );
                _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.MARKED_SUSPICIOUS, identity,
                        response.getStatus( ).getType( ).name( ), response.getStatus( ).getMessage( ), author, clientCode, metadata );
            }
            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_CREATE, CREATE_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, clientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( Exception e )
        {
            TransactionManager.rollBack( null );
            response.setStatus(
                    ResponseStatusFactory.failure( ).setMessage( e.getMessage( ) ).setMessageKey( Constants.PROPERTY_REST_ERROR_DURING_TREATMENT ) );
        }
    }

    public void search( final SuspiciousIdentitySearchRequest request, String clientCode, final RequestAuthor author,
            final SuspiciousIdentitySearchResponse response ) throws IdentityStoreException
    {
        // TODO check if the application has the right to search a suspicious identity
        final List<SuspiciousIdentity> suspiciousIdentitysList = SuspiciousIdentityHome.getSuspiciousIdentitysList( request.getRuleCode( ),
                request.getAttributes( ), request.getMax( ), request.getRulePriority( ) );

        if ( suspiciousIdentitysList == null || suspiciousIdentitysList.isEmpty( ) )
        {
            response.setStatus( ResponseStatusFactory.noResult( ).setMessageKey( Constants.PROPERTY_REST_ERROR_NO_SUSPICIOUS_IDENTITY_FOUND ) );
            response.setSuspiciousIdentities( Collections.emptyList( ) );
        }
        else
        {
            response.setStatus( ResponseStatusFactory.ok( ).setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );
            response.setSuspiciousIdentities( suspiciousIdentitysList.stream( ).map( SuspiciousIdentityMapper::toDto ).collect( Collectors.toList( ) ) );
        }
        for ( final SuspiciousIdentityDto suspiciousIdentity : response.getSuspiciousIdentities( ) )
        {
            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_READ, SEARCH_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, clientCode ), suspiciousIdentity, SPECIFIC_ORIGIN );
        }
    }

    public void lock( SuspiciousIdentityLockRequest request, String strClientCode, final RequestAuthor author, SuspiciousIdentityLockResponse response )
    {
        TransactionManager.beginTransaction( null );
        try
        {
            final boolean locked = SuspiciousIdentityHome.manageLock( request.getCustomerId( ), author.getName( ), author.getType( ).name( ),
                    request.isLocked( ) );
            response.setLocked( locked );
            response.setStatus( ResponseStatusFactory.success( ).setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );
            TransactionManager.commitTransaction( null );
            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_MODIFY,
                    request.isLocked( ) ? LOCK_SUSPICIOUS_IDENTITY_EVENT_CODE : UNLOCK_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, strClientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( final SuspiciousIdentityLockedException e )
        {
            response.setLocked( false );
            response.setStatus(
                    ResponseStatusFactory.conflict( ).setMessage( e.getMessage( ) ).setMessageKey( Constants.PROPERTY_REST_ERROR_UNAUTHORIZED_OPERATION ) );
            TransactionManager.rollBack( null );
        }
        catch( final IdentityNotFoundException e )
        {
            response.setLocked( false );
            response.setStatus(
                    ResponseStatusFactory.notFound( ).setMessage( e.getMessage( ) ).setMessageKey( Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND ) );
            TransactionManager.rollBack( null );
        }
        catch( final Exception e )
        {
            response.setLocked( false );
            response.setStatus(
                    ResponseStatusFactory.failure( ).setMessage( e.getMessage( ) ).setMessageKey( Constants.PROPERTY_REST_ERROR_DURING_TREATMENT ) );
            TransactionManager.rollBack( null );
        }
    }

    public void exclude( final SuspiciousIdentityExcludeRequest request, final String clientCode, final RequestAuthor author,
            final SuspiciousIdentityExcludeResponse response )
    {
        TransactionManager.beginTransaction( null );
        try
        {
            final Identity firstIdentity = IdentityHome.findByCustomerId( request.getIdentityCuid1( ) );
            final Identity secondIdentity = IdentityHome.findByCustomerId( request.getIdentityCuid2( ) );

            if ( firstIdentity == null )
            {
                response.setStatus( ResponseStatusFactory.notFound( ).setMessage( "Cannot find identity with cuid " + request.getIdentityCuid1( ) )
                        .setMessageKey( Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND ) );
                return;
            }

            if ( secondIdentity == null )
            {
                response.setStatus( ResponseStatusFactory.notFound( ).setMessage( "Cannot find identity with cuid " + request.getIdentityCuid2( ) )
                        .setMessageKey( Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND ) );
                return;
            }

            if ( SuspiciousIdentityHome.excluded( request.getIdentityCuid1( ), request.getIdentityCuid2( ) ) )
            {
                response.setStatus( ResponseStatusFactory.conflict( ).setMessage( "Identities are already excluded from duplicate suspicions." )
                        .setMessageKey( Constants.PROPERTY_REST_ERROR_ALREADY_EXCLUDED ) );
                return;
            }

            // flag the 2 identities: manage the list of identities to exclude (supposed to be a field at the identity level)
            SuspiciousIdentityHome.exclude( request.getIdentityCuid1( ), request.getIdentityCuid2( ), author.getType( ).name( ), author.getName( ) );

            response.setStatus( ResponseStatusFactory.success( ).setMessage( "Identities excluded from duplicate suspicions." )
                    .setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );
            TransactionManager.commitTransaction( null );

            // First identity history
            final Map<String, String> metadata = new HashMap<>( );
            metadata.put( Constants.METADATA_EXCLUDED_CUID_KEY, secondIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.EXCLUDED, firstIdentity,
                    response.getStatus( ).getType( ).name( ), response.getStatus( ).getMessage( ), author, clientCode, metadata );

            // Second identity history
            final Map<String, String> metadata2 = new HashMap<>( );
            metadata2.put( Constants.METADATA_EXCLUDED_CUID_KEY, firstIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.EXCLUDED, secondIdentity,
                    response.getStatus( ).getType( ).name( ), response.getStatus( ).getMessage( ), author, clientCode, metadata2 );

            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_MODIFY, EXCLUDE_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, clientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( Exception e )
        {
            TransactionManager.rollBack( null );
            response.setStatus(
                    ResponseStatusFactory.failure( ).setMessage( e.getMessage( ) ).setMessageKey( Constants.PROPERTY_REST_ERROR_DURING_TREATMENT ) );
        }
    }

    public void cancelExclusion( final SuspiciousIdentityExcludeRequest request, final String clientCode, final RequestAuthor author,
            final SuspiciousIdentityExcludeResponse response )
    {
        TransactionManager.beginTransaction( null );
        try
        {
            final Identity firstIdentity = IdentityHome.findByCustomerId( request.getIdentityCuid1( ) );
            final Identity secondIdentity = IdentityHome.findByCustomerId( request.getIdentityCuid2( ) );

            if ( firstIdentity == null )
            {
                response.setStatus( ResponseStatusFactory.notFound( ).setMessage( "Cannot find identity with cuid " + request.getIdentityCuid1( ) )
                        .setMessageKey( Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND ) );
                return;
            }

            if ( secondIdentity == null )
            {
                response.setStatus( ResponseStatusFactory.notFound( ).setMessage( "Cannot find identity with cuid " + request.getIdentityCuid2( ) )
                        .setMessageKey( Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND ) );
                return;
            }

            if ( !SuspiciousIdentityHome.excluded( request.getIdentityCuid1( ), request.getIdentityCuid2( ) ) )
            {
                response.setStatus( ResponseStatusFactory.conflict( ).setMessage( "Identities are not excluded from duplicate suspicions." )
                        .setMessageKey( Constants.PROPERTY_REST_ERROR_NOT_EXCLUDED ) );
                return;
            }

            // remove the exclusion
            SuspiciousIdentityHome.removeExcludedIdentities( request.getIdentityCuid1( ), request.getIdentityCuid2( ) );
            response.setStatus( ResponseStatusFactory.success( ).setMessage( "Identities exclusion has been cancelled." )
                    .setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );
            TransactionManager.commitTransaction( null );

            // First identity history
            final Map<String, String> metadata = new HashMap<>( );
            metadata.put( Constants.METADATA_EXCLUDED_CUID_KEY, secondIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.EXCLUSION_CANCELLED, firstIdentity,
                    response.getStatus( ).getType( ).name( ), response.getStatus( ).getMessage( ), author, clientCode, metadata );

            // Second identity history
            final Map<String, String> metadata2 = new HashMap<>( );
            metadata2.put( Constants.METADATA_EXCLUDED_CUID_KEY, firstIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.EXCLUSION_CANCELLED, secondIdentity,
                    response.getStatus( ).getType( ).name( ), response.getStatus( ).getMessage( ), author, clientCode, metadata2 );

            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_MODIFY, EXCLUDE_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, clientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( Exception e )
        {
            TransactionManager.rollBack( null );
            response.setStatus(
                    ResponseStatusFactory.failure( ).setMessage( e.getMessage( ) ).setMessageKey( Constants.PROPERTY_REST_ERROR_DURING_TREATMENT ) );
        }
    }

    public boolean hasSuspicious( final List<String> customerIds )
    {
        return SuspiciousIdentityHome.hasSuspicious( customerIds );
    }
}
