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
package fr.paris.lutece.plugins.identitystore.modules.quality.service;

import fr.paris.lutece.plugins.identitystore.service.duplicate.IDuplicateService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AttributeDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.portal.service.spring.SpringContextService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchDuplicatesService
{
    private static SearchDuplicatesService instance;
    private final IDuplicateService _duplicateServiceElasticSearch = SpringContextService.getBean( "identitystore.duplicateService.elasticsearch" );

    public static SearchDuplicatesService instance( )
    {
        if ( instance == null )
        {
            instance = new SearchDuplicatesService( );
        }
        return instance;
    }

    public final DuplicateSearchResponse findDuplicates( final IdentityDto identity, final List<String> ruleCodes, final List<String> attributesFilter )
            throws IdentityStoreException
    {
        final Map<String, String> attributeMap = identity.getAttributes( ).stream( )
                .collect( Collectors.toMap( AttributeDto::getKey, AttributeDto::getValue ) );
        return _duplicateServiceElasticSearch.findDuplicates( attributeMap, identity.getCustomerId( ), ruleCodes, attributesFilter );
    }

    public final DuplicateSearchResponse findDuplicates( final Map<String, String> attributeValues, final List<String> ruleCodes,
            final List<String> attributesFilter ) throws IdentityStoreException
    {
        return _duplicateServiceElasticSearch.findDuplicates( attributeValues, "", ruleCodes, attributesFilter );
    }
}
