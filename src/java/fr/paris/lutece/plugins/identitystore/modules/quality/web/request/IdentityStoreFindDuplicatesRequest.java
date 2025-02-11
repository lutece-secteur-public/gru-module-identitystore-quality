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

import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SearchDuplicatesService;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SuspiciousIdentityService;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.AbstractIdentityStoreRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.SuspiciousIdentityRequestValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class IdentityStoreFindDuplicatesRequest extends AbstractIdentityStoreRequest
{

    private final String _strRuleCode;
    private final String _strCustomerId;

    public IdentityStoreFindDuplicatesRequest( String strClientCode, String ruleCode, String customerId, String authorName, String authorType )
            throws IdentityStoreException
    {
        super( strClientCode, authorName, authorType );
        this._strRuleCode = ruleCode;
        this._strCustomerId = customerId;
    }

    @Override
    protected void validateSpecificRequest( ) throws IdentityStoreException
    {
        SuspiciousIdentityRequestValidator.instance( ).checkCustomerId( _strCustomerId );
        SuspiciousIdentityRequestValidator.instance( ).checkRuleCode( _strRuleCode );
    }

    @Override
    protected DuplicateSearchResponse doSpecificRequest( ) throws IdentityStoreException
    {
        final IdentityDto identity = IdentityService.instance( ).getQualifiedIdentity( _strCustomerId );
        final DuplicateRule rule = DuplicateRuleService.instance().get(_strRuleCode);
        final DuplicateSearchResponse duplicateResponse =
                SearchDuplicatesService.instance().findDuplicates(identity, Collections.singletonList(_strRuleCode), Collections.emptyList());

        // LUT-29120 - Si des doublons potentiels sont trouvés, et que l'identité n'est pas déjà marquée suspecte, on la marque.
        if (rule.isDaemon() && CollectionUtils.isNotEmpty(duplicateResponse.getIdentities())) {
            final List<IdentityDto> processedIdentities = new ArrayList<>(duplicateResponse.getIdentities() );
            processedIdentities.add( identity );
            final List<String> customerIds = processedIdentities.stream( ).map( IdentityDto::getCustomerId ).collect(Collectors.toList());
            if ( !SuspiciousIdentityService.instance().hasSuspicious(customerIds) )
            {
                final SuspiciousIdentityChangeResponse response = new SuspiciousIdentityChangeResponse( );
                final SuspiciousIdentityChangeRequest request = new SuspiciousIdentityChangeRequest( );
                request.setSuspiciousIdentity( new SuspiciousIdentityDto( ));
                request.getSuspiciousIdentity( ).setCustomerId( _strCustomerId );
                request.getSuspiciousIdentity( ).setDuplicationRuleCode( _strRuleCode );
                request.getSuspiciousIdentity( ).getMetadata( ).putAll( duplicateResponse.getMetadata( ) );
                SuspiciousIdentityService.instance( ).create( request, this._strClientCode, this._author, response );
                duplicateResponse.getMetadata().put(Constants.METADATA_MARKED_SUSPICIOUS, _strCustomerId);
            }
        }
        return duplicateResponse;
    }
}
