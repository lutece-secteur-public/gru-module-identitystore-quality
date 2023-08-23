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

import fr.paris.lutece.plugins.identitystore.modules.quality.service.SuspiciousIdentityService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.SuspiciousIdentityRequestValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchResponse;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;

public class IdentityStoreSuspiciousSearchRequest extends AbstractSuspiciousIdentityStoreRequest
{

    private final SuspiciousIdentitySearchRequest _request;
    private final int max;

    private final Integer page;
    private final Integer size;

    public IdentityStoreSuspiciousSearchRequest( final SuspiciousIdentitySearchRequest request, final int max, final Integer page, final Integer size,
            final String strClientAppCode )
    {
        super( strClientAppCode );
        this._request = request;
        this.max = max;
        this.page = page;
        this.size = size;
    }

    @Override
    protected void validRequest( ) throws IdentityStoreException
    {
        SuspiciousIdentityRequestValidator.instance( ).checkClientCode( _strClientCode );
        SuspiciousIdentityRequestValidator.instance( ).checkSuspiciousIdentitySearch( _request );
    }

    @Override
    protected SuspiciousIdentitySearchResponse doSpecificRequest( ) throws IdentityStoreException
    {
        final SuspiciousIdentitySearchResponse response = new SuspiciousIdentitySearchResponse( );
        SuspiciousIdentityService.instance( ).search( _request, _strClientCode, max, response );

        if ( page != null && size != null )
        {
            int start = page * size;
            int end = Math.min( start + size, response.getSuspiciousIdentities( ).size( ) );
            response.setSuspiciousIdentities( response.getSuspiciousIdentities( ).subList( start, end ) );
        }
        return response;
    }
}
