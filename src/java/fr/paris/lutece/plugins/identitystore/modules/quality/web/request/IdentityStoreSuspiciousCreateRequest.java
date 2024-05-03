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
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SuspiciousIdentityService;
import fr.paris.lutece.plugins.identitystore.modules.quality.web.validator.CreateSuspiciousIdentityValidator;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.v3.web.request.AbstractIdentityStoreAppCodeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.SuspiciousIdentityRequestValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.ResponseStatusFactory;
import fr.paris.lutece.plugins.identitystore.web.exception.ClientAuthorizationException;
import fr.paris.lutece.plugins.identitystore.web.exception.DuplicatesConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestContentFormattingException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestFormatException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceNotFoundException;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import org.apache.commons.lang3.StringUtils;

/**
 * This class represents a create request for IdentityStoreRestServive
 */
public class IdentityStoreSuspiciousCreateRequest extends AbstractIdentityStoreAppCodeRequest
{
    private final SuspiciousIdentityChangeRequest _request;

    private DuplicateRule duplicateRule;
    private Identity identity;

    /**
     * Constructor of IdentityStoreCreateRequest
     *
     * @param request
     *            the dto of identity's change
     */
    public IdentityStoreSuspiciousCreateRequest( final SuspiciousIdentityChangeRequest request, final String strClientCode,
            final String strAppCode, final String authorName, final String authorType ) throws IdentityStoreException
    {
        super( strClientCode, strAppCode, authorName, authorType );
        if ( request == null || request.getSuspiciousIdentity( ) == null )
        {
            throw new RequestFormatException( "Provided Suspicious Identity Change request is null or empty", Constants.PROPERTY_REST_ERROR_SUSPICIOUS_CHANGE_REQUEST_NULL_OR_EMPTY );
        }
        this._request = request;
    }

    @Override
    protected void fetchResources( ) throws ResourceNotFoundException
    {
        if ( StringUtils.isNotBlank( _request.getSuspiciousIdentity( ).getDuplicationRuleCode( ) ) )
        {
            duplicateRule = DuplicateRuleService.instance( ).get( _request.getSuspiciousIdentity( ).getDuplicationRuleCode( ) );
        }
        else
        {
            duplicateRule = DuplicateRuleService.instance( ).get( AppPropertiesService.getProperty( "identitystore-quality.external.duplicates.rule.code" ) );
        }

        identity = IdentityHome.findByCustomerId( _request.getSuspiciousIdentity( ).getCustomerId( ) );
        if ( identity == null )
        {
            throw new ResourceNotFoundException( "Identity not found", Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND );
        }
    }

    @Override
    protected void validateRequestFormat( ) throws RequestFormatException
    {
        SuspiciousIdentityRequestValidator.instance( ).checkSuspiciousIdentityChange( _request );
        SuspiciousIdentityRequestValidator.instance( ).checkCustomerId( _request.getSuspiciousIdentity( ).getCustomerId( ) );
    }

    @Override
    protected void validateClientAuthorization( ) throws ClientAuthorizationException
    {
        // TODO check if the application has the right to create a suspicious identity
    }

    @Override
    protected void validateResourcesConsistency( ) throws ResourceConsistencyException
    {
        CreateSuspiciousIdentityValidator.instance( ).checkIfNotAlreadyReported( _request );
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
    protected SuspiciousIdentityChangeResponse doSpecificRequest( ) throws IdentityStoreException
    {
        final SuspiciousIdentityChangeResponse response = new SuspiciousIdentityChangeResponse( );

        final SuspiciousIdentityDto result = SuspiciousIdentityService.instance( ).create( _request, identity, duplicateRule, _strClientCode, _author );
        response.setSuspiciousIdentity( result );
        response.setStatus( ResponseStatusFactory.success( ).setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );

        return response;
    }

}
