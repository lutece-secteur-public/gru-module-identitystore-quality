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
import fr.paris.lutece.plugins.identitystore.modules.quality.rs.SuspiciousIdentityMapper;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.*;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockStatus;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.util.sql.TransactionManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class SuspiciousIdentityService
{
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
     * @param identityChangeRequest
     *            the {@link SuspiciousIdentityChangeRequest} holding the parameters of the suspicious identity change request
     * @param clientCode
     *            code of the {@link ClientApplication} requesting the change
     * @param response
     *            the {@link SuspiciousIdentityChangeResponse} holding the status of the execution of the request
     * @return the created {@link SuspiciousIdentity}
     * @throws IdentityStoreException
     *             in case of error
     */
    public void create( final SuspiciousIdentityChangeRequest identityChangeRequest, final String clientCode, final SuspiciousIdentityChangeResponse response )
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
            final Identity identity = IdentityHome.findByCustomerId( identityChangeRequest.getSuspiciousIdentity( ).getCustomerId( ) );
            if ( identity == null )
            {
                response.setStatus( IdentityChangeStatus.NOT_FOUND );
            }
            else
            {
                final SuspiciousIdentity suspiciousIdentity = new SuspiciousIdentity( );
                suspiciousIdentity.setIdDuplicateRule( identityChangeRequest.getSuspiciousIdentity( ).getIdDuplicateRule( ) );
                suspiciousIdentity.setCustomerId( identityChangeRequest.getSuspiciousIdentity( ).getCustomerId( ) );
                suspiciousIdentity.setCreationDate( Timestamp.from( Instant.now( ) ) );
                suspiciousIdentity.setLastUpdateDate( identity.getLastUpdateDate( ) );

                SuspiciousIdentityHome.create( suspiciousIdentity );

                response.setSuspiciousIdentity( SuspiciousIdentityMapper.toDto( suspiciousIdentity ) );
                response.setStatus( IdentityChangeStatus.CREATE_SUCCESS );
            }
            TransactionManager.commitTransaction( null );
        }
        catch( Exception e )
        {
            TransactionManager.rollBack( null );
            response.setStatus( IdentityChangeStatus.FAILURE );
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
            TransactionManager.commitTransaction( null );
        }
        catch( SuspiciousIdentityLockedException e )
        {
            response.setLocked( false );
            response.setStatus( SuspiciousIdentityLockStatus.CONFLICT );
            response.setMessage( e.getMessage( ) );
            TransactionManager.rollBack( null );
        }
        catch( IdentityStoreException e )
        {
            response.setLocked( false );
            response.setStatus( SuspiciousIdentityLockStatus.NOT_FOUND );
            response.setMessage( e.getMessage( ) );
            TransactionManager.rollBack( null );
        }
    }

    public void exclude( final SuspiciousIdentityExcludeRequest request, final String clientCode, final SuspiciousIdentityExcludeResponse response )
    {
        TransactionManager.beginTransaction( null );
        try
        {
            // flag the 2 identities: manage the list of identities to exclude (supposed to be a field at the identity level)
            SuspiciousIdentityHome.exclude( request.getIdentityCuid1( ), request.getIdentityCuid2( ), request.getOrigin( ).getType( ).name( ),
                    request.getOrigin( ).getName( ) );
            // clean the consolidated identities from suspicious identities
            SuspiciousIdentityHome.remove( request.getIdentityCuid1( ) );
            SuspiciousIdentityHome.remove( request.getIdentityCuid2( ) );

            response.setStatus( SuspiciousIdentityExcludeStatus.EXCLUDE_SUCCESS );
            response.setMessage( "Identities excluded from duplicate suspicions." );
            TransactionManager.commitTransaction( null );
        }
        catch( Exception e )
        {
            TransactionManager.rollBack( null );
            response.setMessage( e.getMessage( ) );
            response.setStatus( SuspiciousIdentityExcludeStatus.EXCLUDE_FAILURE );
        }
    }

    public boolean hasSuspicious( final List<String> customerIds )
    {
        return SuspiciousIdentityHome.hasSuspicious( customerIds );
    }
}
