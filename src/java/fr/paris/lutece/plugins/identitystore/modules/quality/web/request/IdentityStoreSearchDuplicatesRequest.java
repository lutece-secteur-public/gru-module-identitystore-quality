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

import fr.paris.lutece.plugins.identitystore.business.contract.ServiceContract;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SearchDuplicatesService;
import fr.paris.lutece.plugins.identitystore.service.attribute.IdentityAttributeFormatterService;
import fr.paris.lutece.plugins.identitystore.service.contract.ServiceContractService;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityQualityService;
import fr.paris.lutece.plugins.identitystore.utils.Maps;
import fr.paris.lutece.plugins.identitystore.v3.web.request.AbstractIdentityStoreAppCodeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.request.validator.DuplicateRuleValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.request.validator.IdentityAttributeValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.SuspiciousIdentityRequestValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AttributeStatus;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.QualifiedIdentitySearchResult;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.ResponseStatusFactory;
import fr.paris.lutece.plugins.identitystore.web.exception.ClientAuthorizationException;
import fr.paris.lutece.plugins.identitystore.web.exception.DuplicatesConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestContentFormattingException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestFormatException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class IdentityStoreSearchDuplicatesRequest extends AbstractIdentityStoreAppCodeRequest
{

    private final DuplicateSearchRequest _request;
    private final List<DuplicateRule> rules = new ArrayList<>( );

    private ServiceContract serviceContract;

    public IdentityStoreSearchDuplicatesRequest( String strClientCode, final String strAppCode, DuplicateSearchRequest request, String authorName,
            String authorType ) throws IdentityStoreException
    {
        super( strClientCode, strAppCode, authorName, authorType );
        if ( request == null )
        {
            throw new RequestFormatException( "Provided duplicate search request is null", Constants.PROPERTY_REST_ERROR_DUPLICATE_SEARCH_REQUEST_NULL );
        }
        this._request = request;
    }

    @Override
    protected void fetchResources( ) throws ResourceNotFoundException
    {
        for ( final String ruleCode : _request.getRuleCodes( ) )
        {
            rules.add( DuplicateRuleService.instance( ).get( ruleCode ) );
        }
        serviceContract = ServiceContractService.instance( ).getActiveServiceContract( _strClientCode );
    }

    @Override
    protected void validateRequestFormat( ) throws RequestFormatException
    {
        SuspiciousIdentityRequestValidator.instance( ).checkDuplicateSearch( _request );
        IdentityAttributeValidator.instance( ).checkAttributeExistence( _request.getAttributes( ).keySet( ) );
    }

    @Override
    protected void validateClientAuthorization( ) throws ClientAuthorizationException
    {
        // Do nothing
    }

    @Override
    protected void validateResourcesConsistency( ) throws ResourceConsistencyException
    {
        for ( final DuplicateRule rule : rules )
        {
            DuplicateRuleValidator.instance( ).validateActive( rule );
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
    protected DuplicateSearchResponse doSpecificRequest( ) throws IdentityStoreException
    {
        //TODO à refactorer selon le refactoring en cours
        // #28070 - la date doit être formatée avant de lancer la recherche
        AttributeStatus formatStatus = null;
        final String birthdateValue = _request.getAttributes().get(Constants.PARAM_BIRTH_DATE);
        if (birthdateValue != null) {
            final String formattedBirthdate = IdentityAttributeFormatterService.instance().formatDateValue(birthdateValue);
            if (!formattedBirthdate.equals(birthdateValue)) {
                formatStatus = IdentityAttributeFormatterService.instance().buildAttributeValueFormattedStatus(Constants.PARAM_BIRTH_DATE, birthdateValue, formattedBirthdate);
                _request.getAttributes().put(Constants.PARAM_BIRTH_DATE, formattedBirthdate);
            }
        }

        final DuplicateSearchResponse response = new DuplicateSearchResponse( );

        final Map<String, QualifiedIdentitySearchResult> duplicates = SearchDuplicatesService.instance( ).findDuplicates( _request.getAttributes( ), rules,
                Collections.emptyList( ) );
        duplicates.values( ).stream( ).peek( r -> Maps.mergeStringMap( response.getMetadata( ), r.getMetadata( ) ) )
                .flatMap( r -> r.getQualifiedIdentities( ).stream( ) ).forEach( identity -> {
                    if ( response.getIdentities( ).stream( ).noneMatch( existing -> Objects.equals( existing.getCustomerId( ), identity.getCustomerId( ) ) ) )
                    {
                        IdentityQualityService.instance( ).enrich( null, identity, serviceContract, null, false );
                        response.getIdentities( ).add( identity );
                    }
                } );
        final List<String> matchingRuleCodes = duplicates.entrySet( ).stream( ).filter( e -> !e.getValue( ).getQualifiedIdentities( ).isEmpty( ) )
                .map( Map.Entry::getKey ).collect( Collectors.toList( ) );

        response.setStatus( ResponseStatusFactory.ok( ).setMessage( "Potential duplicate(s) found with rule(s) : " + String.join( ",", matchingRuleCodes ) )
                .setMessageKey( Constants.PROPERTY_REST_INFO_POTENTIAL_DUPLICATE_FOUND ) );

        if (formatStatus != null) {
            response.getStatus().getAttributeStatuses().add(formatStatus);
        }
        return response;
    }
}
