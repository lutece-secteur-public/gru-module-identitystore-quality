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
package fr.paris.lutece.plugins.identitystore.modules.quality.service;

import fr.paris.lutece.plugins.identitystore.business.application.ClientApplication;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.identity.IdentityAttribute;
import fr.paris.lutece.plugins.identitystore.business.identity.IdentityHome;
import fr.paris.lutece.plugins.identitystore.modules.quality.rs.SuspiciousIdentityMapper;
import fr.paris.lutece.plugins.identitystore.modules.quality.web.request.SuspiciousIdentityStoreCreateRequest;
import fr.paris.lutece.plugins.identitystore.service.contract.ServiceContractService;
import fr.paris.lutece.plugins.identitystore.service.search.ISearchIdentityService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.IdentityChangeStatus;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeResponse;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.portal.service.spring.SpringContextService;

import java.sql.Timestamp;
import java.time.Instant;

public class SuspiciousIdentityService
{

    protected ISearchIdentityService _searchIdentityService = SpringContextService.getBean( "identitystore.searchIdentityService" );
    private final ServiceContractService _serviceContractService = ServiceContractService.instance( );
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
     * Creates a new {@link Identity} according to the given {@link SuspiciousIdentityChangeRequest}
     *
     * @param identityChangeRequest
     *            the {@link SuspiciousIdentityChangeRequest} holding the parameters of the identity change request
     * @param clientCode
     *            code of the {@link ClientApplication} requesting the change
     * @param response
     *            the {@link SuspiciousIdentityChangeResponse} holding the status of the execution of the request
     * @return the created {@link Identity}
     * @throws IdentityStoreException
     *             in case of error
     */
    public SuspiciousIdentity create(final SuspiciousIdentityChangeRequest identityChangeRequest, final String clientCode,
                                     final SuspiciousIdentityChangeResponse response ) throws IdentityStoreException
    {
        // TODO check if the application has the right to create a suspicious identity
        /*
         * if ( !_serviceContractService.canCreateSuspiciousIdentity( clientCode ) ) { response.setStatus( IdentityChangeStatus.FAILURE ); response.setMessage(
         * "The client application is not authorized to create an identity." ); return null; }
         */

        Identity identity = IdentityHome.findByCustomerId( identityChangeRequest.getSuspiciousIdentity( ).getCustomerId( ) );
        final SuspiciousIdentity suspiciousIdentity = new SuspiciousIdentity( );
        if ( identity == null )
        {
            response.setStatus( IdentityChangeStatus.NOT_FOUND );
        }
        else
        {

            suspiciousIdentity.setIdDuplicateRule( identityChangeRequest.getSuspiciousIdentity( ).getIdDuplicateRule( ) );
            suspiciousIdentity.setCustomerId( identityChangeRequest.getSuspiciousIdentity( ).getCustomerId( ) );
            suspiciousIdentity.setCreationDate( Timestamp.from( Instant.now( ) ) );
            suspiciousIdentity.setLastUpdateDate( identity.getLastUpdateDate( ) );

            SuspiciousIdentityHome.create( suspiciousIdentity );

            response.setSuspiciousIdentity( SuspiciousIdentityMapper.toDto( suspiciousIdentity ) );
            response.setStatus( IdentityChangeStatus.CREATE_SUCCESS );
        }
        return suspiciousIdentity;
    }

    /**
     * Updates an existing {@link Identity} according to the given {@link SuspiciousIdentityStoreCreateRequest} and following the given rules: <br>
     * <ul>
     * <li>The {@link Identity} must exist in te database. If not, NOT_FOUND status is returned in the execution response</li>
     * <li>The {@link Identity} must not be merged or deleted. In case of merged/deleted identity, the update is not performed and the customer ID of the
     * primary identity is returned in the execution response with a CONFLICT status</li>
     * <li>If the {@link Identity} can be updated, its {@link IdentityAttribute} list is updated following the given rule:
     * <ul>
     * <li>If the {@link IdentityAttribute} exists, it is updated if the value is different, and if the process level given in the request is higher than the
     * existing one. If the value cannot be updated, the NOT_UPDATED status, associated with the attribute key, is returned in the execution response.</li>
     * <li>If the {@link IdentityAttribute} does not exist, it is created. The CREATED status, associated with the attribute key, is returned in the execution
     * response.</li>
     * <li>CUID and GUID attributes cannot be modified.</li>
     * </ul>
     * </li>
     * </ul>
     *
     * @param customerId
     *            the id of the updated {@link Identity}
     * @param identityChangeRequest
     *            the {@link SuspiciousIdentityChangeRequest} holding the parameters of the identity change request
     * @param clientCode
     *            code of the {@link ClientApplication} requesting the change
     * @param response
     *            the {@link SuspiciousIdentityChangeResponse} holding the status of the execution of the request
     * @return the updated {@link Identity}
     * @throws IdentityStoreException
     *             in case of error
     */
    public SuspiciousIdentity update( final String customerId, final SuspiciousIdentityChangeRequest identityChangeRequest, final String clientCode,
            final SuspiciousIdentityChangeResponse response ) throws IdentityStoreException
    {

        final SuspiciousIdentity identity = SuspiciousIdentityHome.selectByCustomerID( customerId );

        return identity;
    }

}
