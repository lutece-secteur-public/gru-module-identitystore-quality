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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.identity.IdentityHome;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SearchDuplicatesService;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SuspiciousIdentityService;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityQualityService;
import fr.paris.lutece.plugins.identitystore.utils.Maps;
import fr.paris.lutece.plugins.identitystore.v3.web.request.AbstractIdentityStoreAppCodeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.request.validator.DuplicateRuleValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.request.validator.IdentityAttributeValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.DtoConverter;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.SuspiciousIdentityRequestValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.QualifiedIdentitySearchResult;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.ResponseStatusFactory;
import fr.paris.lutece.plugins.identitystore.web.exception.ClientAuthorizationException;
import fr.paris.lutece.plugins.identitystore.web.exception.DuplicatesConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestContentFormattingException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestFormatException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceConsistencyException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceNotFoundException;

public class IdentityStoreFindDuplicatesRequest extends AbstractIdentityStoreAppCodeRequest
{

    private final String _strRuleCode;
    private final String _strCustomerId;

    private IdentityDto qualifiedIdentity;
    private DuplicateRule rule;

    public IdentityStoreFindDuplicatesRequest( final String strClientCode, final String strAppCode, final String ruleCode, final String customerId,
            final String authorName, final String authorType ) throws IdentityStoreException
    {
        super( strClientCode, strAppCode, authorName, authorType );
        this._strRuleCode = ruleCode;
        this._strCustomerId = customerId;
    }

    @Override
    protected void fetchResources( ) throws ResourceNotFoundException
    {
        final Identity identity = IdentityHome.findByCustomerId( _strCustomerId );
        if ( identity == null )
        {
            throw new ResourceNotFoundException( "Cannot find identity with CUID = " + _strCustomerId, Constants.PROPERTY_REST_ERROR_IDENTITY_NOT_FOUND );
        }
        qualifiedIdentity = DtoConverter.convertIdentityToDto( identity );
        IdentityQualityService.instance( ).computeQuality( qualifiedIdentity );

        rule = DuplicateRuleService.instance( ).get( _strRuleCode );
    }

    @Override
    protected void validateRequestFormat( ) throws RequestFormatException
    {
        SuspiciousIdentityRequestValidator.instance( ).checkCustomerId( _strCustomerId );
        SuspiciousIdentityRequestValidator.instance( ).checkRuleCode( _strRuleCode );
        IdentityAttributeValidator.instance( ).checkAttributeExistence( qualifiedIdentity );
    }

    @Override
    protected void validateClientAuthorization( ) throws ClientAuthorizationException
    {
        // Do nothing
    }

    @Override
    protected void validateResourcesConsistency( ) throws ResourceConsistencyException
    {
        DuplicateRuleValidator.instance( ).validateActive( rule );
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
        final DuplicateSearchResponse response = new DuplicateSearchResponse( );

        final Map<String, QualifiedIdentitySearchResult> duplicates = SearchDuplicatesService.instance( ).findDuplicates( qualifiedIdentity,
                Collections.singletonList( rule ), Collections.emptyList( ) );
        duplicates.values( ).stream( ).peek( r -> Maps.mergeStringMap( response.getMetadata( ), r.getMetadata( ) ) )
                .flatMap( r -> r.getQualifiedIdentities( ).stream( ) ).forEach( identity -> {
                    if ( response.getIdentities( ).stream( ).noneMatch( existing -> Objects.equals( existing.getCustomerId( ), identity.getCustomerId( ) ) ) )
                    {
                        response.getIdentities( ).add( identity );
                    }
                } );
        final List<String> matchingRuleCodes = duplicates.entrySet( ).stream( ).filter( e -> !e.getValue( ).getQualifiedIdentities( ).isEmpty( ) )
                .map( Map.Entry::getKey ).collect( Collectors.toList( ) );
        
        if ( duplicates.size ( ) == 0 )
        {
            response.setStatus( ResponseStatusFactory.ok( )
        	    .setMessage( "No potential duplicate found." )
                    .setMessageKey( Constants.PROPERTY_REST_INFO_NO_POTENTIAL_DUPLICATE_FOUND ) );
        }
        else
        {
            response.setStatus( ResponseStatusFactory.ok( )
        	    .setMessage( String.valueOf( duplicates.size ( ) ) +  " potential duplicate(s) found  with rule(s) : " + String.join( ",", matchingRuleCodes ) )
                    .setMessageKey( Constants.PROPERTY_REST_INFO_POTENTIAL_DUPLICATE_FOUND ) );
        
            // LUT-29120 - Si des doublons potentiels sont trouvés, et que l'identité n'est pas déjà marquée suspecte, on la marque.
            if ( rule.isDaemon( ) && rule.isActive( ) 
            	&& duplicates.values().stream().anyMatch(result -> !result.getQualifiedIdentities( ).isEmpty( ) ) ) 
            {
                final List<String> processedIdentitiesCuids =
                        duplicates.values().stream().flatMap( r -> r.getQualifiedIdentities( ).stream( ) ).map(IdentityDto::getCustomerId).collect(Collectors.toList());
                processedIdentitiesCuids.add( _strCustomerId );

                // remove all suspiciousIdentity with identity customerId, but without any duplicate_customer_id (old model)
                SuspiciousIdentityHome.remove(_strCustomerId , true);
                // create one suspicious identity for each detected customerId
                for(final String duplicateCuid : processedIdentitiesCuids) {
                    if(duplicateCuid.equals(_strCustomerId )) {
                        continue;
                    }
                    // if the suspicious pair doesn't exist, create it
                    if(!SuspiciousIdentityService.instance().existsSuspicious(_strCustomerId , duplicateCuid, rule.getId())) {
                        final SuspiciousIdentityChangeRequest request = new SuspiciousIdentityChangeRequest( );
                        request.setSuspiciousIdentity( new SuspiciousIdentityDto( ));
                        request.getSuspiciousIdentity( ).setCustomerId( _strCustomerId );
                        request.getSuspiciousIdentity( ).setDuplicateCuid( duplicateCuid );
                        request.getSuspiciousIdentity( ).setDuplicationRuleCode( _strRuleCode );
                        request.getSuspiciousIdentity( ).getMetadata( ).putAll( response.getMetadata( ) );

                        SuspiciousIdentityService.instance( ).create( request, DtoConverter.convertDtoToIdentity(qualifiedIdentity), duplicateCuid, rule, this._strClientCode, this._author );
                        response.getMetadata().put(Constants.METADATA_MARKED_SUSPICIOUS, _strCustomerId);
                    }
                }
            }
        }
        
        return response;
    }
}
