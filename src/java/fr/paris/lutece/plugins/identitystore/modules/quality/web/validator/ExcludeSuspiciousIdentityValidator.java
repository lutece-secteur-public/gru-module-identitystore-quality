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
package fr.paris.lutece.plugins.identitystore.modules.quality.web.validator;

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityExcludeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceConsistencyException;

public class ExcludeSuspiciousIdentityValidator
{
    private static ExcludeSuspiciousIdentityValidator instance;

    public static ExcludeSuspiciousIdentityValidator instance( )
    {
        if ( instance == null )
        {
            instance = new ExcludeSuspiciousIdentityValidator( );
        }
        return instance;
    }

    private ExcludeSuspiciousIdentityValidator( )
    {
    }

    public void validateExclusion( final SuspiciousIdentityExcludeRequest request ) throws ResourceConsistencyException
    {
        if ( SuspiciousIdentityHome.excluded( request.getIdentityCuid1( ), request.getIdentityCuid2( ) ) )
        {
            throw new ResourceConsistencyException( "Identities are already excluded from duplicate suspicions.",
                    Constants.PROPERTY_REST_ERROR_ALREADY_EXCLUDED );
        }
    }

    public void validateExclusionCancelation( final SuspiciousIdentityExcludeRequest request ) throws ResourceConsistencyException
    {
        if ( !SuspiciousIdentityHome.excluded( request.getIdentityCuid1( ), request.getIdentityCuid2( ) ) )
        {
            throw new ResourceConsistencyException( "Identities are not excluded from duplicate suspicions.", Constants.PROPERTY_REST_ERROR_NOT_EXCLUDED );
        }
    }

}
