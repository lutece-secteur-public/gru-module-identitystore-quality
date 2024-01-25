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

import fr.paris.lutece.plugins.identitystore.modules.quality.service.SuspiciousIdentityService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.AbstractIdentityStoreRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.SuspiciousIdentityRequestValidator;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.Page;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.ResponseStatusFactory;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;

public class IdentityStoreSuspiciousSearchRequest extends AbstractIdentityStoreRequest
{
    private final SuspiciousIdentitySearchRequest _request;

    public IdentityStoreSuspiciousSearchRequest( final SuspiciousIdentitySearchRequest request, final String strClientAppCode, final String authorName,
            final String authorType ) throws IdentityStoreException
    {
        super( strClientAppCode, authorName, authorType );
        this._request = request;
    }

    @Override
    protected void validateSpecificRequest( ) throws IdentityStoreException
    {
        SuspiciousIdentityRequestValidator.instance( ).checkSuspiciousIdentitySearch( _request );
    }

    @Override
    protected SuspiciousIdentitySearchResponse doSpecificRequest( ) throws IdentityStoreException
    {
        final SuspiciousIdentitySearchResponse response = new SuspiciousIdentitySearchResponse( );

        if ( _request.getPage( ) != null && _request.getPage( ) < 1 )
        {
            response.setStatus( ResponseStatusFactory.badRequest( ) );
            response.getStatus( ).setMessage( "Pagination should start at index 1" );
            response.getStatus( ).setMessageKey( Constants.PROPERTY_REST_PAGINATION_START_ERROR );
            return response;
        }
        SuspiciousIdentityService.instance( ).search( _request, _strClientCode, _author, response );

        if ( _request.getPage( ) != null && _request.getSize( ) != null )
        {

            final int totalRecords = response.getSuspiciousIdentities( ).size( );
            final int totalPages = (int) Math.ceil( (double) totalRecords / _request.getSize( ) );

            if ( _request.getPage( ) > totalPages )
            {
                response.setStatus( ResponseStatusFactory.badRequest( ) );
                response.getStatus( ).setMessage( "Pagination index should not exceed total number of pages." );
                response.getStatus( ).setMessageKey( Constants.PROPERTY_REST_PAGINATION_END_ERROR );
                response.setSuspiciousIdentities( null );
                return response;
            }

            final int start = ( _request.getPage( ) - 1 ) * _request.getSize( );
            final int end = Math.min( start + _request.getSize( ), totalRecords );
            response.setSuspiciousIdentities( response.getSuspiciousIdentities( ).subList( start, end ) );

            final Page pagination = new Page( );
            pagination.setTotalPages( totalPages );
            pagination.setTotalRecords( totalRecords );
            pagination.setCurrentPage( _request.getPage( ) );
            pagination.setNextPage( _request.getPage( ) == totalPages ? null : _request.getPage( ) + 1 );
            pagination.setPreviousPage( _request.getPage( ) > 1 ? _request.getPage( ) - 1 : null );
            response.setPagination( pagination );
        }
        return response;
    }
}
