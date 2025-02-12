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

import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.identity.IdentityHome;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SuspiciousIdentityService;
import fr.paris.lutece.plugins.identitystore.v3.web.request.AbstractIdentityStoreAppCodeRequest;
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
import org.apache.commons.lang3.StringUtils;

/**
 * This class represents a create request for IdentityStoreRestServive
 */
public class IdentityStoreSuspiciousCheckLockRequest extends AbstractIdentityStoreAppCodeRequest
{
    private final String _customerId;

    /**
     * Constructor of IdentityStoreCreateRequest
     *
     * @param request
     *            the dto of lock's change
     */
    public IdentityStoreSuspiciousCheckLockRequest( String customer_id, String strClientAppCode, String strAppCode, String authorName, String authorType )
            throws IdentityStoreException
    {
        super( strClientAppCode, strAppCode, authorName, authorType );
        if (StringUtils.isBlank( customer_id ) )
        {
            throw new RequestFormatException( "The provided customer id is null or empty.", Constants.PROPERTY_REST_ERROR_LOCK_REQUEST_CUID_NULL_OR_EMPTY );
        }
        _customerId = customer_id ;
    }

    @Override
    protected void fetchResources() throws ResourceNotFoundException
    {
        final Identity identity = IdentityHome.findByCustomerId( _customerId );
        if ( identity == null )
        {
            throw new ResourceNotFoundException( "Could not find identity with customerId " + _customerId,
                    Constants.PROPERTY_REST_ERROR_NO_MATCHING_IDENTITY );
        }
    }

    @Override
    protected void validateRequestFormat() throws RequestFormatException
    {

    }

    @Override
    protected void validateClientAuthorization() throws ClientAuthorizationException
    {

    }

    @Override
    protected void validateResourcesConsistency() throws ResourceConsistencyException
    {

    }

    @Override
    protected void formatRequestContent() throws RequestContentFormattingException
    {

    }

    @Override
    protected void checkDuplicatesConsistency() throws DuplicatesConsistencyException
    {

    }

    @Override
    public SuspiciousIdentityLockResponse doSpecificRequest( ) throws IdentityStoreException
    {
        final SuspiciousIdentityLockResponse response = new SuspiciousIdentityLockResponse( );

        final boolean locked = SuspiciousIdentityService.instance( ).checkLock( _customerId, _strClientCode, _author );

        response.setCustomerId( _customerId );
        response.setLocked( locked );
        response.setStatus( ResponseStatusFactory.ok( ).setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );

        return response;
    }

}
