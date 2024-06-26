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
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SearchDuplicatesService;
import fr.paris.lutece.plugins.identitystore.service.attribute.IdentityAttributeFormatterService;
import fr.paris.lutece.plugins.identitystore.service.contract.ServiceContractService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityQualityService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.AbstractIdentityStoreRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.SuspiciousIdentityRequestValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AttributeStatus;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;

import java.util.Collections;
import java.util.stream.Collectors;

public class IdentityStoreSearchDuplicatesRequest extends AbstractIdentityStoreRequest
{

    private final DuplicateSearchRequest _request;

    public IdentityStoreSearchDuplicatesRequest( String strClientCode, DuplicateSearchRequest request, String authorName, String authorType )
            throws IdentityStoreException
    {
        super( strClientCode, authorName, authorType );
        this._request = request;
    }

    @Override
    protected void validateSpecificRequest( ) throws IdentityStoreException
    {
        SuspiciousIdentityRequestValidator.instance( ).checkDuplicateSearch( _request );
    }

    @Override
    protected DuplicateSearchResponse doSpecificRequest( ) throws IdentityStoreException
    {
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

        final DuplicateSearchResponse duplicateSearchResponse = SearchDuplicatesService.instance( ).findDuplicates( _request.getAttributes( ),
                _request.getRuleCodes( ), Collections.emptyList( ) );
        if (formatStatus != null) {
            duplicateSearchResponse.getStatus().getAttributeStatuses().add(formatStatus);
        }
        final ServiceContract serviceContract = ServiceContractService.instance( ).getActiveServiceContract( _strClientCode );
        duplicateSearchResponse.getIdentities( )
                .forEach( identityDto -> IdentityQualityService.instance( ).enrich( null, identityDto, serviceContract, null, false ) );
        return duplicateSearchResponse;
    }
}
