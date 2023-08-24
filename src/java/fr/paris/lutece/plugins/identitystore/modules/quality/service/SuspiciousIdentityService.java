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
import fr.paris.lutece.plugins.identitystore.service.indexer.elastic.index.listener.IndexIdentityChange;
import fr.paris.lutece.plugins.identitystore.service.listeners.IdentityStoreNotifyListenerService;
import fr.paris.lutece.plugins.identitystore.service.user.InternalUserService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.*;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.history.IdentityChange;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.history.IdentityChangeType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockStatus;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityNotFoundException;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.portal.service.security.AccessLogService;
import fr.paris.lutece.portal.service.security.AccessLoggerConstants;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.sql.TransactionManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class SuspiciousIdentityService
{
    // EVENTS FOR ACCESS LOGGING
    private static final String CREATE_SUSPICIOUS_IDENTITY_EVENT_CODE = "CREATE_SUSPICIOUS_IDENTITY";
    private static final String LOCK_SUSPICIOUS_IDENTITY_EVENT_CODE = "LOCK_SUSPICIOUS_IDENTITY";
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
    public void create( final SuspiciousIdentityChangeRequest request, final String clientCode, final SuspiciousIdentityChangeResponse response )
            throws IdentityStoreException
    {
        // TODO check if the application has the right to create a suspicious identity
        /*
         * if ( !_serviceContractService.canCreateSuspiciousIdentity( clientCode ) ) { response.setStatus( IdentityChangeStatus.FAILURE ); response.setMessage(
         * "The client application is not authorized to create an identity." ); return null; }
         */
        TransactionManager.beginTransaction( null );
        try
        {
            final Identity identity = IdentityHome.findByCustomerId( request.getSuspiciousIdentity( ).getCustomerId( ) );
            if ( identity == null )
            {
                response.setStatus( IdentityChangeStatus.NOT_FOUND );
                response.setI18nMessageKey( Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND );
            }
            else
            {
                final SuspiciousIdentity suspiciousIdentity = new SuspiciousIdentity( );
                final String requestRuleCode = request.getSuspiciousIdentity( ).getDuplicationRuleCode( );
                final String ruleCode = requestRuleCode != null ? requestRuleCode : externalDeclarationRuleCode;
                final DuplicateRule duplicateRule = DuplicateRuleService.instance( ).get( ruleCode );
                suspiciousIdentity.setDuplicateRuleCode( ruleCode );
                suspiciousIdentity.setIdDuplicateRule( duplicateRule.getId( ) );
                suspiciousIdentity.setCustomerId( request.getSuspiciousIdentity( ).getCustomerId( ) );
                suspiciousIdentity.setCreationDate( Timestamp.from( Instant.now( ) ) );
                suspiciousIdentity.setLastUpdateDate( identity.getLastUpdateDate( ) );

                SuspiciousIdentityHome.create( suspiciousIdentity );

                response.setSuspiciousIdentity( SuspiciousIdentityMapper.toDto( suspiciousIdentity ) );
                response.setStatus( IdentityChangeStatus.CREATE_SUCCESS );
                response.setI18nMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION );

                final IdentityChange identityChange = IdentityStoreNotifyListenerService.buildIdentityChange( IdentityChangeType.MARKED_SUSPICIOUS, identity,
                        response.getStatus( ).name( ), response.getMessage( ), request.getOrigin( ), clientCode );
                identityChange.getMetadata( ).put( Constants.METADATA_DUPLICATE_RULE_CODE, ruleCode );
                _identityStoreNotifyListenerService.notifyListenersIdentityChange( identityChange );
            }
            TransactionManager.commitTransaction( null );
            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_CREATE, CREATE_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( request, clientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( Exception e )
        {
            TransactionManager.rollBack( null );
            response.setStatus( IdentityChangeStatus.FAILURE );
            response.setI18nMessageKey( Constants.PROPERTY_REST_ERROR_DURING_TREATMENT );
            response.setMessage( e.getMessage( ) );
        }
    }

    public void lock( SuspiciousIdentityLockRequest request, String strClientCode, SuspiciousIdentityLockResponse response )
    {
        TransactionManager.beginTransaction( null );
        try
        {
            final boolean locked = SuspiciousIdentityHome.manageLock( request.getCustomerId( ), request.getOrigin( ).getName( ),
                    request.getOrigin( ).getType( ).name( ), request.isLocked( ) );
            response.setLocked( locked );
            response.setStatus( SuspiciousIdentityLockStatus.SUCCESS );
            response.setI18nMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION );
            TransactionManager.commitTransaction( null );
            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_MODIFY, LOCK_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( request, strClientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( SuspiciousIdentityLockedException e )
        {
            response.setLocked( false );
            response.setStatus( SuspiciousIdentityLockStatus.CONFLICT );
            response.setMessage( e.getMessage( ) );
            response.setI18nMessageKey( Constants.PROPERTY_REST_ERROR_UNAUTHORIZED_OPERATION );
            TransactionManager.rollBack( null );
        }
        catch( IdentityNotFoundException e )
        {
            response.setLocked( false );
            response.setStatus( SuspiciousIdentityLockStatus.NOT_FOUND );
            response.setMessage( e.getMessage( ) );
            response.setI18nMessageKey( Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND );
            TransactionManager.rollBack( null );
        }
        catch( Exception e )
        {
            response.setLocked( false );
            response.setStatus( SuspiciousIdentityLockStatus.FAILURE );
            response.setMessage( e.getMessage( ) );
            response.setI18nMessageKey( Constants.PROPERTY_REST_ERROR_DURING_TREATMENT );
            TransactionManager.rollBack( null );
        }
    }

    public void exclude( final SuspiciousIdentityExcludeRequest request, final String clientCode, final SuspiciousIdentityExcludeResponse response )
            throws IdentityStoreException
    {
        TransactionManager.beginTransaction( null );
        try
        {
            final Identity firstIdentity = IdentityHome.findByCustomerId( request.getIdentityCuid1( ) );
            final Identity secondIdentity = IdentityHome.findByCustomerId( request.getIdentityCuid2( ) );

            if ( firstIdentity == null )
            {
                throw new IdentityStoreException( "Cannot find identity with cuid " + request.getIdentityCuid1( ) );
            }

            if ( secondIdentity == null )
            {
                throw new IdentityStoreException( "Cannot find identity with cuid " + request.getIdentityCuid2( ) );
            }

            if ( SuspiciousIdentityHome.excluded( request.getIdentityCuid1( ), request.getIdentityCuid2( ) ) )
            {
                response.setStatus( SuspiciousIdentityExcludeStatus.CONFLICT );
                response.setMessage( "Identities are already excluded from duplicate suspicions." );
                response.setI18nMessageKey( Constants.PROPERTY_REST_ERROR_ALREADY_EXCLUDED );
                return;
            }

            // flag the 2 identities: manage the list of identities to exclude (supposed to be a field at the identity level)
            SuspiciousIdentityHome.exclude( request.getIdentityCuid1( ), request.getIdentityCuid2( ), request.getOrigin( ).getType( ).name( ),
                    request.getOrigin( ).getName( ) );
            // clean the consolidated identities from suspicious identities
            SuspiciousIdentityHome.remove( request.getIdentityCuid1( ) );
            SuspiciousIdentityHome.remove( request.getIdentityCuid2( ) );

            response.setStatus( SuspiciousIdentityExcludeStatus.SUCCESS );
            response.setMessage( "Identities excluded from duplicate suspicions." );
            response.setI18nMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION );
            // First identity history
            final IdentityChange identityChange1 = IdentityStoreNotifyListenerService.buildIdentityChange( IdentityChangeType.EXCLUDED, firstIdentity,
                    response.getStatus( ).name( ), response.getMessage( ), request.getOrigin( ), clientCode );
            identityChange1.getMetadata( ).put( Constants.METADATA_EXCLUDED_CUID_KEY, secondIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( new IndexIdentityChange( identityChange1, firstIdentity ) );
            // Second identity history
            final IdentityChange identityChange2 = IdentityStoreNotifyListenerService.buildIdentityChange( IdentityChangeType.EXCLUDED, secondIdentity,
                    response.getStatus( ).name( ), response.getMessage( ), request.getOrigin( ), clientCode );
            identityChange2.getMetadata( ).put( Constants.METADATA_EXCLUDED_CUID_KEY, firstIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( new IndexIdentityChange( identityChange2, secondIdentity ) );
            TransactionManager.commitTransaction( null );
            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_MODIFY, EXCLUDE_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( request, clientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( Exception e )
        {
            TransactionManager.rollBack( null );
            response.setMessage( e.getMessage( ) );
            response.setI18nMessageKey( Constants.PROPERTY_REST_ERROR_DURING_TREATMENT );
            response.setStatus( SuspiciousIdentityExcludeStatus.FAILURE );
        }
    }

    public void cancelExclusion( final SuspiciousIdentityExcludeRequest request, final String clientCode, final SuspiciousIdentityExcludeResponse response )
            throws IdentityStoreException
    {
        TransactionManager.beginTransaction( null );
        try
        {
            final Identity firstIdentity = IdentityHome.findByCustomerId( request.getIdentityCuid1( ) );
            final Identity secondIdentity = IdentityHome.findByCustomerId( request.getIdentityCuid2( ) );

            if ( firstIdentity == null )
            {
                throw new IdentityStoreException( "Cannot find identity with cuid " + request.getIdentityCuid1( ) );
            }

            if ( secondIdentity == null )
            {
                throw new IdentityStoreException( "Cannot find identity with cuid " + request.getIdentityCuid2( ) );
            }

            if ( !SuspiciousIdentityHome.excluded( request.getIdentityCuid1( ), request.getIdentityCuid2( ) ) )
            {
                response.setStatus( SuspiciousIdentityExcludeStatus.CONFLICT );
                response.setMessage( "Identities are not excluded from duplicate suspicions." );
                response.setI18nMessageKey( Constants.PROPERTY_REST_ERROR_NOT_EXCLUDED );
                return;
            }

            // remove the exclusion
            SuspiciousIdentityHome.removeExcludedIdentities( request.getIdentityCuid1( ), request.getIdentityCuid2( ) );
            response.setStatus( SuspiciousIdentityExcludeStatus.SUCCESS );
            response.setMessage( "Identities exclusion has been cancelled." );
            response.setI18nMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION );
            // First identity history
            final IdentityChange identityChange1 = IdentityStoreNotifyListenerService.buildIdentityChange( IdentityChangeType.EXCLUSION_CANCELLED,
                    firstIdentity, response.getStatus( ).name( ), response.getMessage( ), request.getOrigin( ), clientCode );
            identityChange1.getMetadata( ).put( Constants.METADATA_EXCLUDED_CUID_KEY, secondIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( new IndexIdentityChange( identityChange1, firstIdentity ) );
            // Second identity history
            final IdentityChange identityChange2 = IdentityStoreNotifyListenerService.buildIdentityChange( IdentityChangeType.EXCLUSION_CANCELLED,
                    secondIdentity, response.getStatus( ).name( ), response.getMessage( ), request.getOrigin( ), clientCode );
            identityChange2.getMetadata( ).put( Constants.METADATA_EXCLUDED_CUID_KEY, firstIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( new IndexIdentityChange( identityChange2, secondIdentity ) );
            TransactionManager.commitTransaction( null );
            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_MODIFY, EXCLUDE_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( request, clientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( Exception e )
        {
            TransactionManager.rollBack( null );
            response.setMessage( e.getMessage( ) );
            response.setI18nMessageKey( Constants.PROPERTY_REST_ERROR_DURING_TREATMENT );
            response.setStatus( SuspiciousIdentityExcludeStatus.FAILURE );
        }
    }

    public boolean hasSuspicious( final List<String> customerIds )
    {
        return SuspiciousIdentityHome.hasSuspicious( customerIds );
    }
}
