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
package fr.paris.lutece.plugins.identitystore.modules.quality.web.request;

import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.AbstractIdentityStoreRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.duplicate.DuplicateRuleSummaryDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.duplicate.DuplicateRuleSummarySearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.ResponseStatusFactory;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import org.apache.commons.collections.CollectionUtils;

import java.util.Collections;
import java.util.List;

public class IdentityStoreDuplicateRuleGetRequest extends AbstractIdentityStoreRequest
{
    private final Integer _nPriority;

    /**
     * Constructor.
     * 
     * @param strClientCode
     *            the client application Code
     */
    public IdentityStoreDuplicateRuleGetRequest( final String strClientCode, final Integer _nPriority, final String authorName, final String authorType )
            throws IdentityStoreException
    {
        super( strClientCode, authorName, authorType );
        this._nPriority = _nPriority;
    }

    @Override
    protected void validateSpecificRequest( )
    {
    }

    @Override
    protected DuplicateRuleSummarySearchResponse doSpecificRequest( ) throws IdentityStoreException
    {
        final DuplicateRuleSummarySearchResponse response = new DuplicateRuleSummarySearchResponse( );

        final List<DuplicateRuleSummaryDto> rules = DuplicateRuleService.instance( ).findSummaries( _nPriority );
        if ( CollectionUtils.isEmpty( rules ) )
        {
            response.setDuplicateRuleSummaries( Collections.emptyList( ) );
            response.setStatus( ResponseStatusFactory.noResult( ).setMessageKey( Constants.PROPERTY_REST_ERROR_NO_DUPLICATE_RULE_FOUND ) );
        }
        else
        {
            response.setDuplicateRuleSummaries( rules );
            response.setStatus( ResponseStatusFactory.ok( ).setMessageKey( Constants.PROPERTY_REST_INFO_SUCCESSFUL_OPERATION ) );
        }
        return response;
    }
}
