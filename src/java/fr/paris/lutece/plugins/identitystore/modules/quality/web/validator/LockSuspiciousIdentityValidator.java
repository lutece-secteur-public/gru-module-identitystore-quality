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

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.ClientAuthorizationException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceConsistencyException;

import java.util.Objects;

public class LockSuspiciousIdentityValidator
{
    private static LockSuspiciousIdentityValidator instance;

    public static LockSuspiciousIdentityValidator instance( )
    {
        if ( instance == null )
        {
            instance = new LockSuspiciousIdentityValidator( );
        }
        return instance;
    }

    private LockSuspiciousIdentityValidator( )
    {
    }

    public void validateLockRequestAuthorization( final SuspiciousIdentity suspiciousIdentity, final SuspiciousIdentityLockRequest request,
            final RequestAuthor author ) throws ClientAuthorizationException
    {
        final boolean lock = request.isLocked( );
        final boolean isAlreadyLocked = suspiciousIdentity.getLock( ).isLocked( );
        final boolean sameAuthorName = Objects.equals( suspiciousIdentity.getLock( ).getAuthorName( ), author.getName( ) );
        final boolean sameAuthorType = Objects.equals( suspiciousIdentity.getLock( ).getAuthorType( ), author.getType( ).name( ) );
        final boolean sameAuthor = sameAuthorName && sameAuthorType;
        if ( lock && isAlreadyLocked && !sameAuthor )
        {
            throw new ClientAuthorizationException( "Suspicious identity with customerId " + suspiciousIdentity.getCustomerId( ) + " is locked by "
                    + suspiciousIdentity.getLock( ).getAuthorName( ) + ".", Constants.PROPERTY_REST_ERROR_UNAUTHORIZED_OPERATION );
        }
        if ( !lock && isAlreadyLocked && !sameAuthor )
        {
            throw new ClientAuthorizationException(
                    "Suspicious identity with customerId " + suspiciousIdentity.getCustomerId( ) + " is locked by "
                            + suspiciousIdentity.getLock( ).getAuthorName( ) + ". User " + author.getName( ) + " is not allowed to unlock.",
                    Constants.PROPERTY_REST_ERROR_UNAUTHORIZED_OPERATION );
        }
    }

    public void validateLockRequestConsistency( final SuspiciousIdentity suspiciousIdentity, final SuspiciousIdentityLockRequest request )
            throws ResourceConsistencyException
    {
        final boolean lock = request.isLocked( );
        final boolean isAlreadyLocked = suspiciousIdentity.getLock( ).isLocked( );
        if ( !lock && !isAlreadyLocked )
        {
            throw new ResourceConsistencyException( "Suspicious identity with customerId " + suspiciousIdentity.getCustomerId( ) + " is already unlocked.",
                    Constants.PROPERTY_REST_ERROR_UPDATE_CONFLICT );
        }
    }
}
