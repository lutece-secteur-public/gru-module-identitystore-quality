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

import fr.paris.lutece.plugins.identitystore.business.identity.Identity;
import fr.paris.lutece.plugins.identitystore.business.identity.IdentityHome;
import fr.paris.lutece.plugins.identitystore.modules.quality.business.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.modules.quality.business.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.modules.quality.web.request.SuspiciousIdentityStoreCreateRequest;
import fr.paris.lutece.plugins.identitystore.service.IdentityStoreService;
import fr.paris.lutece.plugins.identitystore.v3.web.request.DuplicateRuleGetRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.IdentityMapper;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.ResponseDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchStatusType;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.duplicate.DuplicateRuleSummaryDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.duplicate.DuplicateRuleSummarySearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.swagger.SwaggerConstants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityNotFoundException;
import fr.paris.lutece.plugins.rest.service.RestConstants;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.util.json.JsonResponse;
import fr.paris.lutece.util.json.JsonUtil;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.web.bind.annotation.PathVariable;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SuspiciousIdentityRest
 */
@Path( RestConstants.BASE_PATH + Constants.PLUGIN_PATH + Constants.VERSION_PATH_V3 + Constants.QUALITY_PATH )
public class SuspiciousIdentityRest
{
    protected static final String ERROR_NO_OBJECT_FOUND = "No object found";
    protected static final String ERROR_DURING_TREATMENT = "An error occured during the treatment.";

    private Response getSuspiciousIdentityListResponse( int max, Integer page, Integer size, List<SuspiciousIdentity> listSuspiciousIdentities )
    {
        final SuspiciousIdentitySearchResponse searchResponse = new SuspiciousIdentitySearchResponse( );
        if ( listSuspiciousIdentities.isEmpty( ) )
        {
            searchResponse.setStatus( SuspiciousIdentitySearchStatusType.NOT_FOUND );
            searchResponse.setSuspiciousIdentities( Collections.emptyList( ) );
        }
        else
        {
            searchResponse.setStatus( SuspiciousIdentitySearchStatusType.SUCCESS );
            if ( page != null && size != null )
            {
                int start = page * size;
                int end = Math.min( start + size, listSuspiciousIdentities.size( ) );
                searchResponse.setSuspiciousIdentities(
                        listSuspiciousIdentities.subList( start, end ).stream( ).map( SuspiciousIdentityMapper::toDto ).collect( Collectors.toList( ) ) );
            }
            else
            {
                searchResponse
                        .setSuspiciousIdentities( listSuspiciousIdentities.stream( ).map( SuspiciousIdentityMapper::toDto ).collect( Collectors.toList( ) ) );
            }
        }
        return Response.status( searchResponse.getStatus( ).getCode( ) ).entity( searchResponse ).build( );
    }

    /**
     * Get SuspiciousIdentity List
     *
     * @return the SuspiciousIdentity List
     */
    @GET
    @Path( Constants.SUSPICIONS_PATH )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Get a list of suspicions, limited to max" )
    public Response getAllSuspiciousIdentityList( @ApiParam( name = "max", value = "Maximum number of " ) @QueryParam( Constants.PARAM_MAX ) final int max,
            @ApiParam( name = "page", value = "Page to return" ) @QueryParam( Constants.PARAM_PAGE ) Integer page,
            @ApiParam( name = "size", value = "number of suspicious identity to return " ) @QueryParam( Constants.PARAM_SIZE ) Integer size )
    {
        final List<SuspiciousIdentity> listSuspiciousIdentities = SuspiciousIdentityHome.getSuspiciousIdentitysList( null, max );
        return this.getSuspiciousIdentityListResponse( max, page, size, listSuspiciousIdentities );
    }

    /**
     * Get SuspiciousIdentity List
     *
     * @return the SuspiciousIdentity List
     */
    @GET
    @Path( Constants.SUSPICIONS_PATH + "/{rule}" )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Get a list of suspicions, limited to max" )
    public Response getSuspiciousIdentityList( @ApiParam( name = "rule", value = "The rule id to filter with" ) @PathVariable( required = false ) Integer rule,
            @ApiParam( name = "max", value = "Maximum number of " ) @QueryParam( Constants.PARAM_MAX ) final int max,
            @ApiParam( name = "page", value = "Page to return" ) @QueryParam( Constants.PARAM_PAGE ) Integer page,
            @ApiParam( name = "size", value = "number of suspicious identity to return " ) @QueryParam( Constants.PARAM_SIZE ) Integer size )
    {
        final List<SuspiciousIdentity> listSuspiciousIdentities = SuspiciousIdentityHome.getSuspiciousIdentitysList( rule, max );

        return this.getSuspiciousIdentityListResponse( max, page, size, listSuspiciousIdentities );
    }

    @POST
    @Path( Constants.SUSPICIONS_PATH )
    @Consumes( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Create a new Suspicious Identity", notes = "The creation is conditioned by the service contract definition associated to the client application code." )
    @ApiResponses( value = {
            @ApiResponse( code = 201, message = "Success" ), @ApiResponse( code = 400, message = ERROR_DURING_TREATMENT + " with explanation message" ),
            @ApiResponse( code = 403, message = "Failure" ), @ApiResponse( code = 409, message = "Conflict" )
    } )
    public Response createSuspiciousIdentity(
            @ApiParam( name = "Request body", value = "An Identity Change Request" ) SuspiciousIdentityChangeRequest suspiciousIdentityChangeRequest,
            @ApiParam( name = Constants.PARAM_CLIENT_CODE, value = SwaggerConstants.CLIENT_CLIENT_CODE_DESCRIPTION ) @HeaderParam( Constants.PARAM_CLIENT_CODE ) final String strHeaderClientCode,
            @QueryParam( Constants.PARAM_CLIENT_CODE ) final String strQueryClientCode )
    {
        try
        {
            final String trustedClientCode = IdentityStoreService.getTrustedClientCode( strHeaderClientCode, strQueryClientCode );
            final SuspiciousIdentityStoreCreateRequest suspiciousIdentityStoreRequest = new SuspiciousIdentityStoreCreateRequest(
                    suspiciousIdentityChangeRequest, trustedClientCode );

            if ( suspiciousIdentityChangeRequest.getSuspiciousIdentity( ) != null
                    && SuspiciousIdentityHome.selectByCustomerID( suspiciousIdentityChangeRequest.getSuspiciousIdentity( ).getCustomerId( ) ) != null )
            {
                final ResponseDto duplicateResponse = new ResponseDto( );
                duplicateResponse.setStatus( Response.Status.CONFLICT.toString( ) );
                duplicateResponse.setMessage( "already reported" );
                return Response.status( Response.Status.CONFLICT ).type( MediaType.APPLICATION_JSON ).entity( duplicateResponse ).build( );
            }
            SuspiciousIdentityChangeResponse suspiciousIdentity = (SuspiciousIdentityChangeResponse) suspiciousIdentityStoreRequest.doRequest( );
            return Response.status( Response.Status.OK ).entity( suspiciousIdentity ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
        }
        catch( Exception exception )
        {
            return getErrorResponse( exception );
        }

    }

    @PUT
    @Path( Constants.RAPPROCHEMENT_PATH + "/{master_customer_id}" )
    @Consumes( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Consolidate 2 entities", notes = "The consolidation is conditioned by the service contract definition associated to the client application code." )
    @ApiResponses( value = {
            @ApiResponse( code = 201, message = "Success" ), @ApiResponse( code = 400, message = ERROR_DURING_TREATMENT + " with explanation message" ),
            @ApiResponse( code = 403, message = "Failure" ), @ApiResponse( code = 409, message = "Conflict" )
    } )
    public Response createSuspiciousIdentity(
            @ApiParam( name = "master_customer_id", value = "the id of the master csutomer" ) @PathParam( "master_customer_id" ) String master_customer_id,
            @ApiParam( name = "Request body", value = "An Identity Change Request that we will attach to the master" ) SuspiciousIdentityChangeRequest suspiciousIdentityChangeRequest2,
            @ApiParam( name = Constants.PARAM_CLIENT_CODE, value = SwaggerConstants.CLIENT_CLIENT_CODE_DESCRIPTION ) @HeaderParam( Constants.PARAM_CLIENT_CODE ) final String strHeaderClientCode,
            @QueryParam( Constants.PARAM_CLIENT_CODE ) final String strQueryClientCode )
    {

        final String trustedClientCode = IdentityStoreService.getTrustedClientCode( strHeaderClientCode, strQueryClientCode );

        Identity identity1 = IdentityHome.findByCustomerId( master_customer_id );
        Identity identity2 = IdentityHome.findByCustomerId( suspiciousIdentityChangeRequest2.getSuspiciousIdentity( ).getCustomerId( ) );
        // consolidate entities if they are not already consolidated with another one
        if ( identity2.getMasterIdentityId( ) == 0 )
        {
            identity2.setMasterIdentityId( identity1.getId( ) );
            IdentityHome.update( identity2 );
        }
        else
        {
            return Response.status( Response.Status.CONFLICT ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
        }
        // clean the consolidated identities from suspicious identities
        SuspiciousIdentity suspicious1 = SuspiciousIdentityHome.selectByCustomerID( identity1.getCustomerId( ) );
        SuspiciousIdentity suspicious2 = SuspiciousIdentityHome.selectByCustomerID( identity2.getCustomerId( ) );
        SuspiciousIdentityHome.remove( suspicious1.getId( ) );
        SuspiciousIdentityHome.remove( suspicious2.getId( ) );
        return Response.status( Response.Status.OK ).entity( identity2 ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
    }

    @PUT
    @Path( Constants.EXCLUSION_PATH + "/{master_customer_id}" )
    @Consumes( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Excluse entities", notes = "The consolidation is conditioned by the service contract definition associated to the client application code." )
    @ApiResponses( value = {
            @ApiResponse( code = 201, message = "Success" ), @ApiResponse( code = 400, message = ERROR_DURING_TREATMENT + " with explanation message" ),
            @ApiResponse( code = 403, message = "Failure" ), @ApiResponse( code = 409, message = "Conflict" )
    } )
    public Response excludeSuspiciousIdentity(
            @ApiParam( name = "master_customer_id", value = "the id of the master csutomer" ) @PathParam( "master_customer_id" ) String master_customer_id,
            @ApiParam( name = "Request body", value = "An Identity Change Request that will be the master identity" ) SuspiciousIdentityChangeRequest suspiciousIdentityChangeRequest,
            @QueryParam( "ruleId" ) final int ruleId,
            @ApiParam( name = Constants.PARAM_CLIENT_CODE, value = SwaggerConstants.CLIENT_CLIENT_CODE_DESCRIPTION ) @HeaderParam( Constants.PARAM_CLIENT_CODE ) final String strHeaderClientCode,
            @QueryParam( Constants.PARAM_CLIENT_CODE ) final String strQueryClientCode )
    {

        SuspiciousIdentity suspicious1 = SuspiciousIdentityHome.selectByCustomerID( master_customer_id );
        SuspiciousIdentity suspicious2 = SuspiciousIdentityHome.selectByCustomerID( suspiciousIdentityChangeRequest.getSuspiciousIdentity( ).getCustomerId( ) );
        if ( suspicious1 != null && suspicious2 != null )
        {
            // flag the 2 identities: manage the list of identities to exlude (suposed to be a field at the identitiy level)
            SuspiciousIdentityHome.exclude( suspicious1, suspicious2, ruleId );
            // clean the consolidated identities from suspicious identities
            SuspiciousIdentityHome.remove( suspicious1.getId( ) );
            SuspiciousIdentityHome.remove( suspicious2.getId( ) );
            return Response.status( Response.Status.OK ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
        }
        return Response.status( Response.Status.NOT_FOUND ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
    }

    /**
     * Get SuspiciousIdentity List
     *
     * @return the SuspiciousIdentity List
     */
    @GET
    @Path( Constants.RULES_PATH )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Get full list of duplicate rules", response = DuplicateRuleSummarySearchResponse.class )
    @ApiResponses( value = {
            @ApiResponse( code = 200, message = "Rules Found" ), @ApiResponse( code = 400, message = ERROR_DURING_TREATMENT + " with explanation message" ),
            @ApiResponse( code = 403, message = "Failure" ), @ApiResponse( code = 404, message = ERROR_NO_OBJECT_FOUND )
    } )
    public Response getDuplicateRules( @HeaderParam( Constants.PARAM_CLIENT_CODE ) final String strHeaderClientCode,
            @QueryParam( Constants.PARAM_CLIENT_CODE ) final String strQueryClientCode )
    {
        try
        {
            final String trustedClientCode = IdentityStoreService.getTrustedClientCode( strHeaderClientCode, strQueryClientCode );
            final DuplicateRuleGetRequest request = new DuplicateRuleGetRequest( trustedClientCode );
            final DuplicateRuleSummarySearchResponse entity = (DuplicateRuleSummarySearchResponse) request.doRequest( );
            entity.setDuplicateRuleSummaries( entity.getDuplicateRuleSummaries( ).stream( ).map( rule -> {
                rule.setDuplicateCount( SuspiciousIdentityHome.countSuspiciousIdentity( rule.getId( ) ) );
                return rule;
            } ).collect( Collectors.toList( ) ) );
            return Response.status( entity.getStatus( ).getCode( ) ).entity( entity ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
        }
        catch( final Exception exception )
        {
            return getErrorResponse( exception );
        }
    }

    /**
     * build error response from exception
     *
     * @param exception
     *            the exception
     * @return ResponseDto from exception
     */
    private Response getErrorResponse( final Exception exception )
    {
        // For security purpose, send a generic message
        String strMessage;
        Response.StatusType status;

        AppLogService.error( "IdentityQualityRestService getErrorResponse : " + exception, exception );

        if ( exception instanceof IdentityNotFoundException )
        {
            strMessage = ERROR_NO_OBJECT_FOUND;
            status = Response.Status.NOT_FOUND;
        }
        else
        {
            strMessage = ERROR_DURING_TREATMENT + " : " + exception.getMessage( );
            status = Response.Status.BAD_REQUEST;
        }

        return buildResponse( strMessage, status );
    }

    /**
     * Builds a {@code Response} object from the specified message and status
     *
     * @param strMessage
     *            the message
     * @param status
     *            the status
     * @return the {@code Response} object
     */
    private Response buildResponse( final String strMessage, final Response.StatusType status )
    {
        final ResponseDto response = new ResponseDto( );
        response.setStatus( status.toString( ) );
        response.setMessage( strMessage );
        return Response.status( status ).type( MediaType.APPLICATION_JSON ).entity( response ).build( );
    }

}
