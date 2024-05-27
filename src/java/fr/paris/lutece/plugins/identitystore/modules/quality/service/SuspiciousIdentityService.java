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

import fr.paris.lutece.plugins.identitystore.business.application.ClientApplication;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.modules.quality.rs.SuspiciousIdentityMapper;
import fr.paris.lutece.plugins.identitystore.service.listeners.IdentityStoreNotifyListenerService;
import fr.paris.lutece.plugins.identitystore.service.user.InternalUserService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.Page;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.RequestAuthor;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.ResponseStatusType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityExcludeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.history.IdentityChangeType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.identitystore.web.exception.RequestFormatException;
import fr.paris.lutece.plugins.identitystore.web.exception.ResourceNotFoundException;
import fr.paris.lutece.portal.service.security.AccessLogService;
import fr.paris.lutece.portal.service.security.AccessLoggerConstants;
import fr.paris.lutece.util.sql.TransactionManager;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SuspiciousIdentityService
{
    // EVENTS FOR ACCESS LOGGING
    private static final String CREATE_SUSPICIOUS_IDENTITY_EVENT_CODE = "CREATE_SUSPICIOUS_IDENTITY";
    private static final String SEARCH_SUSPICIOUS_IDENTITY_EVENT_CODE = "SEARCH_SUSPICIOUS_IDENTITY";
    private static final String LOCK_SUSPICIOUS_IDENTITY_EVENT_CODE = "LOCK_SUSPICIOUS_IDENTITY";
    private static final String UNLOCK_SUSPICIOUS_IDENTITY_EVENT_CODE = "UNLOCK_SUSPICIOUS_IDENTITY";
    private static final String EXCLUDE_SUSPICIOUS_IDENTITY_EVENT_CODE = "EXCLUDE_SUSPICIOUS_IDENTITY";
    private static final String SPECIFIC_ORIGIN = "BO";

    // SERVICES
    private final IdentityStoreNotifyListenerService _identityStoreNotifyListenerService = IdentityStoreNotifyListenerService.instance( );
    private final InternalUserService _internalUserService = InternalUserService.getInstance( );
    private static SuspiciousIdentityService _instance;

    public static SuspiciousIdentityService instance( )
    {
        if ( _instance == null )
        {
            _instance = new SuspiciousIdentityService( );
        }
        return _instance;
    }

    /**
     * Creates a new {@link SuspiciousIdentity} according to the given parameters
     *
     * @param request
     *            the {@link SuspiciousIdentityChangeRequest} holding the parameters of the suspicious identity change request
     * @param identity
     *            the {@link Identity} wanted to be marked as suspicious
     * @param duplicateRule
     *            the {@link DuplicateRule} used to mark the suspicious identity
     * @param clientCode
     *            code of the {@link ClientApplication} requesting the change
     * @param author
     *            the author
     * @return the created {@link SuspiciousIdentityDto}
     * @throws IdentityStoreException
     *             in case of error
     */
    public SuspiciousIdentityDto create( final SuspiciousIdentityChangeRequest request, final Identity identity, final DuplicateRule duplicateRule,
            final String clientCode, final RequestAuthor author ) throws IdentityStoreException
    {
        TransactionManager.beginTransaction( null );
        try
        {
            final SuspiciousIdentity suspiciousIdentity = new SuspiciousIdentity( );

            suspiciousIdentity.setDuplicateRuleCode( duplicateRule.getCode( ) );
            suspiciousIdentity.setIdDuplicateRule( duplicateRule.getId( ) );
            suspiciousIdentity.setCustomerId( identity.getCustomerId( ) );
            suspiciousIdentity.setCreationDate( Timestamp.from( Instant.now( ) ) );
            suspiciousIdentity.setLastUpdateDate( identity.getLastUpdateDate( ) );

            SuspiciousIdentityHome.create( suspiciousIdentity );

            TransactionManager.commitTransaction( null );

            final Map<String, String> metadata = new HashMap<>( request.getSuspiciousIdentity( ).getMetadata( ) );
            metadata.put( Constants.METADATA_DUPLICATE_RULE_CODE, duplicateRule.getCode( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.MARKED_SUSPICIOUS, identity,
                    ResponseStatusType.SUCCESS.name( ), ResponseStatusType.SUCCESS.name( ), author, clientCode, metadata );

            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_CREATE, CREATE_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, clientCode ), request, SPECIFIC_ORIGIN );

            return SuspiciousIdentityMapper.toDto( suspiciousIdentity );
        }
        catch( final Exception e )
        {
            TransactionManager.rollBack( null );
            throw new IdentityStoreException( e.getMessage( ), Constants.PROPERTY_REST_ERROR_DURING_TREATMENT );
        }
    }

    public Pair<List<SuspiciousIdentityDto>, Page> search( final SuspiciousIdentitySearchRequest request, final String clientCode, final RequestAuthor author )
            throws IdentityStoreException
    {
        final List<SuspiciousIdentity> fullSuspiciousList = SuspiciousIdentityHome.getSuspiciousIdentitysList( request.getRuleCode( ), request.getAttributes( ),
                request.getMax( ), request.getRulePriority( ) );

        if ( fullSuspiciousList == null || fullSuspiciousList.isEmpty( ) )
        {
            throw new ResourceNotFoundException( "No suspicious identity found", Constants.PROPERTY_REST_ERROR_NO_SUSPICIOUS_IDENTITY_FOUND );
        }

        final List<SuspiciousIdentityDto> suspiciousIdentitiesToReturn;
        final Page pagination;
        if ( request.getPage( ) != null && request.getSize( ) != null )
        {
            final int totalRecords = fullSuspiciousList.size( );
            final int totalPages = (int) Math.ceil( (double) totalRecords / request.getSize( ) );

            if ( totalPages > 0 && request.getPage( ) > totalPages )
            {
                throw new RequestFormatException( "Pagination index should not exceed total number of pages.", Constants.PROPERTY_REST_PAGINATION_END_ERROR );
            }

            final int start = ( request.getPage( ) - 1 ) * request.getSize( );
            final int end = Math.min( start + request.getSize( ), totalRecords );
            suspiciousIdentitiesToReturn = fullSuspiciousList.subList( start, end ).stream( ).map( SuspiciousIdentityMapper::toDto )
                    .collect( Collectors.toList( ) );

            pagination = new Page( );
            pagination.setTotalPages( totalPages );
            pagination.setTotalRecords( totalRecords );
            pagination.setCurrentPage( request.getPage( ) );
            if( totalPages > 0 )
            {
                pagination.setNextPage( request.getPage() == totalPages ? null : request.getPage() + 1 );
                pagination.setPreviousPage( request.getPage() > 1 ? request.getPage() - 1 : null );
            }
        }
        else
        {
            suspiciousIdentitiesToReturn = fullSuspiciousList.stream( ).map( SuspiciousIdentityMapper::toDto ).collect( Collectors.toList( ) );
            pagination = null;
        }

        for ( final SuspiciousIdentityDto suspiciousIdentity : suspiciousIdentitiesToReturn )
        {
            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_READ, SEARCH_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, clientCode ), suspiciousIdentity, SPECIFIC_ORIGIN );
        }
        return Pair.of( suspiciousIdentitiesToReturn, pagination );
    }

    public boolean lock( final SuspiciousIdentityLockRequest request, final SuspiciousIdentity suspiciousIdentity, final String strClientCode,
            final RequestAuthor author ) throws IdentityStoreException
    {
        TransactionManager.beginTransaction( null );
        try
        {
            final boolean locked = SuspiciousIdentityHome.manageLock( suspiciousIdentity.getCustomerId(), author.getName( ), author.getType( ).name( ), request.isLocked( ) );
            TransactionManager.commitTransaction( null );
            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_MODIFY,
                    request.isLocked( ) ? LOCK_SUSPICIOUS_IDENTITY_EVENT_CODE : UNLOCK_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, strClientCode ), request, SPECIFIC_ORIGIN );
            return locked;
        }
        catch( final Exception e )
        {
            TransactionManager.rollBack( null );
            throw new IdentityStoreException( e.getMessage( ), Constants.PROPERTY_REST_ERROR_DURING_TREATMENT );
        }
    }

    public void exclude( final SuspiciousIdentityExcludeRequest request, final Identity firstIdentity, final Identity secondIdentity, final String clientCode,
            final RequestAuthor author ) throws IdentityStoreException
    {
        TransactionManager.beginTransaction( null );
        try
        {
            // flag the 2 identities: manage the list of identities to exclude (supposed to be a field at the identity level)
            SuspiciousIdentityHome.exclude( firstIdentity.getCustomerId( ), secondIdentity.getCustomerId( ), author.getType( ).name( ), author.getName( ) );

            TransactionManager.commitTransaction( null );

            // First identity history
            final Map<String, String> metadata = new HashMap<>( );
            metadata.put( Constants.METADATA_EXCLUDED_CUID_KEY, secondIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.EXCLUDED, firstIdentity, ResponseStatusType.SUCCESS.name( ),
                    ResponseStatusType.SUCCESS.name( ), author, clientCode, metadata );

            // Second identity history
            final Map<String, String> metadata2 = new HashMap<>( );
            metadata2.put( Constants.METADATA_EXCLUDED_CUID_KEY, firstIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.EXCLUDED, secondIdentity, ResponseStatusType.SUCCESS.name( ),
                    ResponseStatusType.SUCCESS.name( ), author, clientCode, metadata2 );

            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_MODIFY, EXCLUDE_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, clientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( final Exception e )
        {
            TransactionManager.rollBack( null );
            throw new IdentityStoreException( e.getMessage( ), Constants.PROPERTY_REST_ERROR_DURING_TREATMENT );
        }
    }

    public void cancelExclusion( final SuspiciousIdentityExcludeRequest request, final Identity firstIdentity, final Identity secondIdentity,
            final String clientCode, final RequestAuthor author ) throws IdentityStoreException
    {
        TransactionManager.beginTransaction( null );
        try
        {
            // remove the exclusion
            SuspiciousIdentityHome.removeExcludedIdentities( firstIdentity.getCustomerId( ), secondIdentity.getCustomerId( ) );

            TransactionManager.commitTransaction( null );

            // First identity history
            final Map<String, String> metadata = new HashMap<>( );
            metadata.put( Constants.METADATA_EXCLUDED_CUID_KEY, secondIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.EXCLUSION_CANCELLED, firstIdentity,
                    ResponseStatusType.SUCCESS.name( ), ResponseStatusType.SUCCESS.name( ), author, clientCode, metadata );

            // Second identity history
            final Map<String, String> metadata2 = new HashMap<>( );
            metadata2.put( Constants.METADATA_EXCLUDED_CUID_KEY, firstIdentity.getCustomerId( ) );
            _identityStoreNotifyListenerService.notifyListenersIdentityChange( IdentityChangeType.EXCLUSION_CANCELLED, secondIdentity,
                    ResponseStatusType.SUCCESS.name( ), ResponseStatusType.SUCCESS.name( ), author, clientCode, metadata2 );

            AccessLogService.getInstance( ).info( AccessLoggerConstants.EVENT_TYPE_MODIFY, EXCLUDE_SUSPICIOUS_IDENTITY_EVENT_CODE,
                    _internalUserService.getApiUser( author, clientCode ), request, SPECIFIC_ORIGIN );
        }
        catch( final Exception e )
        {
            TransactionManager.rollBack( null );
            throw new IdentityStoreException( e.getMessage( ), Constants.PROPERTY_REST_ERROR_DURING_TREATMENT );
        }
    }

    public boolean hasSuspicious( final List<String> customerIds )
    {
        return SuspiciousIdentityHome.hasSuspicious( customerIds );
    }

    public List<SuspiciousIdentityDto> getSuspiciousIdentity( final List<String> customerIds )
    {
        final List<SuspiciousIdentity> suspiciousIdentities = SuspiciousIdentityHome.selectByCustomerIDs(customerIds);
        return suspiciousIdentities.stream( ).map( SuspiciousIdentityMapper::toDto ).collect( Collectors.toList( ) );
    }
}
