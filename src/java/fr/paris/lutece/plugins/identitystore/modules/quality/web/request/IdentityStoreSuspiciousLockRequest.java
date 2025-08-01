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
package fr.paris.lutece.plugins.identitystore.modules.quality.web.request;

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.identity.IdentityHome;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SuspiciousIdentityService;
import fr.paris.lutece.plugins.identitystore.modules.quality.web.validator.LockSuspiciousIdentityValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.request.AbstractIdentityStoreAppCodeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.AbstractIdentityStoreRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.SuspiciousIdentityRequestValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.ResponseStatusFactory;
import fr.paris.lutece.plugins.identitystore.web.exception.ClientAuthorizationException;
import fr.paris.lutece.plugins.identitystore.web.exception.DuplicatesConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestContentFormattingException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestFormatException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * This class represents a create request for IdentityStoreRestServive
 */
public class IdentityStoreSuspiciousLockRequest extends AbstractIdentityStoreAppCodeRequest
{
    private final SuspiciousIdentityLockRequest _request;

    private List<SuspiciousIdentity> suspiciousIdentityList;

    /**
     * Constructor of IdentityStoreCreateRequest
     *
     * @param request
     *            the dto of lock's change
     */
    public IdentityStoreSuspiciousLockRequest( final SuspiciousIdentityLockRequest request, final String strClientCode, final String strAppCode,
            final String authorName, final String authorType ) throws IdentityStoreException
    {
        super( strClientCode, strAppCode, authorName, authorType );
        if ( request == null )
        {
            throw new RequestFormatException( "Provided Suspicious Lock request is null or empty", Constants.PROPERTY_REST_ERROR_SUSPICIOUS_LOCK_REQUEST_NULL_OR_EMPTY );
        }
        this._request = request;
    }

    @Override
    protected void fetchResources( ) throws ResourceNotFoundException
    {
        final Identity identity = IdentityHome.findByCustomerId( _request.getCustomerId( ) );
        if ( identity == null )
        {
            throw new ResourceNotFoundException( "Could not find identity with customerId " + _request.getCustomerId( ),
                    Constants.PROPERTY_REST_ERROR_NO_MATCHING_IDENTITY );
        }
        suspiciousIdentityList = SuspiciousIdentityHome.selectByCustomerIDs(List.of(_request.getCustomerId()) );
        if ( suspiciousIdentityList == null || suspiciousIdentityList.isEmpty() )
        {
            throw new ResourceNotFoundException( "Could not find suspicious identity with customerId " + _request.getCustomerId( ),
                    Constants.PROPERTY_REST_ERROR_NO_SUSPICIOUS_IDENTITY_FOUND );
        }

    }

    @Override
    protected void validateRequestFormat( ) throws RequestFormatException
    {
        SuspiciousIdentityRequestValidator.instance( ).checkLockRequest( _request );
    }

    @Override
    protected void validateClientAuthorization( ) throws ClientAuthorizationException
    {
        final ListIterator<SuspiciousIdentity> it = suspiciousIdentityList.listIterator();
        final List<ClientAuthorizationException> exceptionList = new ArrayList<>( );
        while(it.hasNext()) {
            final SuspiciousIdentity suspiciousIdentity = it.next();
            try {
                LockSuspiciousIdentityValidator.instance( ).validateLockRequestAuthorization( suspiciousIdentity, _request, _author );
            } catch (final ClientAuthorizationException e) {
                it.remove();
                exceptionList.add(e);
            }
        }
        if (suspiciousIdentityList.isEmpty() && !exceptionList.isEmpty()) {
            throw exceptionList.get( 0 );
        }
    }

    @Override
    protected void validateResourcesConsistency( ) throws ResourceConsistencyException
    {
        final ListIterator<SuspiciousIdentity> it = suspiciousIdentityList.listIterator();
        final List<ResourceConsistencyException> exceptionList = new ArrayList<>( );
        while(it.hasNext()) {
            final SuspiciousIdentity suspiciousIdentity = it.next();
            try {
                LockSuspiciousIdentityValidator.instance( ).validateLockRequestConsistency( suspiciousIdentity, _request );
            } catch (final ResourceConsistencyException e) {
                it.remove();
                exceptionList.add(e);
            }
        }
        if (suspiciousIdentityList.isEmpty() && !exceptionList.isEmpty()) {
            throw exceptionList.get( 0 );
        }
    }

    @Override
    protected void formatRequestContent( ) throws RequestContentFormattingException
    {
        // Do nothing
    }

    @Override
    protected void checkDuplicatesConsistency( ) throws DuplicatesConsistencyException
    {
        // Do nothing
    }

    @Override
    protected SuspiciousIdentityLockResponse doSpecificRequest( ) throws IdentityStoreException
    {
        final SuspiciousIdentityLockResponse response = new SuspiciousIdentityLockResponse( );

        boolean locked = true;
        for( final SuspiciousIdentity suspiciousIdentity : suspiciousIdentityList ) {
            locked = locked && SuspiciousIdentityService.instance( ).lock( _request, suspiciousIdentity, _strClientCode, _author );
        }
        response.setLocked( locked );
        response.setStatus( ResponseStatusFactory.success( ).setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );

        return response;
    }

}
