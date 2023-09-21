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
package fr.paris.lutece.plugins.identitystore.modules.quality.rs;

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityLock;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AuthorType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityLockDto;

/**
 * This is the business class for the object SuspiciousIdentity
 */
public class SuspiciousIdentityMapper
{
    public static SuspiciousIdentityDto toDto( final SuspiciousIdentity suspiciousIdentity )
    {
        if ( suspiciousIdentity == null )
        {
            return null;
        }
        final SuspiciousIdentityDto dto = new SuspiciousIdentityDto( );
        dto.setCreationDate( suspiciousIdentity.getCreationDate( ) );
        dto.setCustomerId( suspiciousIdentity.getCustomerId( ) );
        dto.setLastUpdateDate( suspiciousIdentity.getLastUpdateDate( ) );
        dto.setDuplicationRuleCode( suspiciousIdentity.getDuplicateRuleCode( ) );
        final SuspiciousIdentityLockDto lock = new SuspiciousIdentityLockDto( );
        final SuspiciousIdentityLock beanLock = suspiciousIdentity.getLock( );
        final boolean isLocked = beanLock != null && beanLock.isLocked( );
        lock.setLocked( isLocked );
        if ( isLocked )
        {
            lock.setLockEndDate( beanLock.getLockEndDate( ) );
            final RequestAuthor author = new RequestAuthor( );
            author.setType( AuthorType.valueOf( beanLock.getAuthorType( ) ) );
            author.setName( beanLock.getAuthorName( ) );
            lock.setAuthor( author );
        }
        dto.setLock( lock );
        dto.getMetadata( ).putAll( suspiciousIdentity.getMetadata( ) );
        return dto;
    }

}
