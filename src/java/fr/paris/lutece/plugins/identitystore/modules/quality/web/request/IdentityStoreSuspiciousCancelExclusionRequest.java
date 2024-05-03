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
import fr.paris.lutece.plugins.identitystore.modules.quality.web.validator.ExcludeSuspiciousIdentityValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.request.AbstractIdentityStoreAppCodeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.AbstractIdentityStoreRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.SuspiciousIdentityRequestValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityExcludeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityExcludeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.ResponseStatusFactory;
import fr.paris.lutece.plugins.identitystore.web.exception.ClientAuthorizationException;
import fr.paris.lutece.plugins.identitystore.web.exception.DuplicatesConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestContentFormattingException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestFormatException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceNotFoundException;

/**
 * This class represents a create request for IdentityStoreRestServive
 */
public class IdentityStoreSuspiciousCancelExclusionRequest extends AbstractIdentityStoreAppCodeRequest
{
    private final SuspiciousIdentityExcludeRequest _request;

    private Identity firstIdentity;
    private Identity secondIdentity;

    /**
     * Constructor of IdentityStoreCreateRequest
     *
     * @param request
     *            the dto of identity's change
     */
    public IdentityStoreSuspiciousCancelExclusionRequest( final SuspiciousIdentityExcludeRequest request, final String strClientCode,
            final String strAppCode, final String authorName, final String authorType ) throws IdentityStoreException
    {
        super( strClientCode, strAppCode, authorName, authorType );
        if ( request == null )
        {
            throw new RequestFormatException( "The provided request is null or empty.", Constants.PROPERTY_REST_ERROR_SUSPICIOUS_EXCLUDE_REQUEST_NULL_OR_EMPTY );
        }
        this._request = request;
    }

    @Override
    protected void fetchResources( ) throws ResourceNotFoundException
    {
        firstIdentity = IdentityHome.findByCustomerId( _request.getIdentityCuid1( ) );
        if ( firstIdentity == null )
        {
            throw new ResourceNotFoundException( "Cannot find identity with cuid " + _request.getIdentityCuid1( ),
                    Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND );
        }

        secondIdentity = IdentityHome.findByCustomerId( _request.getIdentityCuid2( ) );
        if ( secondIdentity == null )
        {
            throw new ResourceNotFoundException( "Cannot find identity with cuid " + _request.getIdentityCuid2( ),
                    Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND );
        }
    }

    @Override
    protected void validateRequestFormat( ) throws RequestFormatException
    {
        SuspiciousIdentityRequestValidator.instance( ).checkSuspiciousIdentityExclude( _request );
    }

    @Override
    protected void validateClientAuthorization( ) throws ClientAuthorizationException
    {
        // Do nothing
    }

    @Override
    protected void validateResourcesConsistency( ) throws ResourceConsistencyException
    {
        ExcludeSuspiciousIdentityValidator.instance( ).validateExclusionCancelation( _request );
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
    protected SuspiciousIdentityExcludeResponse doSpecificRequest( ) throws IdentityStoreException
    {

        final SuspiciousIdentityExcludeResponse response = new SuspiciousIdentityExcludeResponse( );

        SuspiciousIdentityService.instance( ).cancelExclusion( _request, firstIdentity, secondIdentity, _strClientCode, _author );

        response.setStatus( ResponseStatusFactory.success( ).setMessage( "Identities exclusion has been cancelled." )
                .setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );

        return response;
    }

}
