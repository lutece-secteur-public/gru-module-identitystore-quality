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

import fr.paris.lutece.plugins.identitystore.modules.quality.web.request.IdentityStoreFindDuplicatesRequest;
import fr.paris.lutece.plugins.identitystore.modules.quality.web.request.IdentityStoreSuspiciousCancelExclusionRequest;
import fr.paris.lutece.plugins.identitystore.modules.quality.web.request.IdentityStoreSuspiciousCreateRequest;
import fr.paris.lutece.plugins.identitystore.modules.quality.web.request.IdentityStoreSuspiciousExcludeRequest;
import fr.paris.lutece.plugins.identitystore.modules.quality.web.request.IdentityStoreSuspiciousLockRequest;
import fr.paris.lutece.plugins.identitystore.modules.quality.web.request.IdentityStoreSuspiciousSearchRequest;
import fr.paris.lutece.plugins.identitystore.service.IdentityStoreService;
import fr.paris.lutece.plugins.identitystore.v3.web.request.DuplicateRuleGetRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityExcludeRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityExcludeResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentitySearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.duplicate.DuplicateRuleSummarySearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.swagger.SwaggerConstants;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.plugins.rest.service.RestConstants;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * SuspiciousIdentityRest
 */
@Path( RestConstants.BASE_PATH + Constants.PLUGIN_PATH + Constants.VERSION_PATH_V3 + Constants.QUALITY_PATH )
public class SuspiciousIdentityRest
{
    protected static final String ERROR_NO_OBJECT_FOUND = "No object found";
    protected static final String ERROR_DURING_TREATMENT = "An error occured during the treatment.";

    /**
     * Get SuspiciousIdentity List
     *
     * @return the SuspiciousIdentity List
     */
    @POST
    @Path( Constants.SUSPICIONS_PATH + Constants.SEARCH_IDENTITIES_PATH )
    @Consumes( MediaType.APPLICATION_JSON )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Get a list of suspicions, limited to max", response = SuspiciousIdentitySearchResponse.class )
    public Response getSuspiciousIdentityList(
            @ApiParam( name = "Request body.", value = "The suspicious identity search request", type = "SuspiciousIdentitySearchRequest" ) SuspiciousIdentitySearchRequest searchRequest,
            @ApiParam( name = Constants.PARAM_CLIENT_CODE, value = SwaggerConstants.CLIENT_CLIENT_CODE_DESCRIPTION ) @HeaderParam( Constants.PARAM_CLIENT_CODE ) String strHeaderClientAppCode,
            @ApiParam( name = Constants.PARAM_MAX, value = "Maximum number of " ) @QueryParam( Constants.PARAM_MAX ) final int max,
            @ApiParam( name = Constants.PARAM_PAGE, value = "Page to return" ) @QueryParam( Constants.PARAM_PAGE ) Integer page,
            @ApiParam( name = Constants.PARAM_SIZE, value = "number of suspicious identity to return " ) @QueryParam( Constants.PARAM_SIZE ) Integer size )
            throws IdentityStoreException
    {
        final IdentityStoreSuspiciousSearchRequest request = new IdentityStoreSuspiciousSearchRequest( searchRequest, max, page, size, strHeaderClientAppCode );
        final SuspiciousIdentitySearchResponse searchResponse = (SuspiciousIdentitySearchResponse) request.doRequest( );

        return Response.status( searchResponse.getStatus( ).getHttpCode( ) ).entity( searchResponse ).build( );
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
            @QueryParam( Constants.PARAM_CLIENT_CODE ) final String strQueryClientCode ) throws IdentityStoreException
    {
        final String trustedClientCode = IdentityStoreService.getTrustedClientCode( strHeaderClientCode, strQueryClientCode );
        final IdentityStoreSuspiciousCreateRequest suspiciousIdentityStoreRequest = new IdentityStoreSuspiciousCreateRequest( suspiciousIdentityChangeRequest,
                trustedClientCode );
        final SuspiciousIdentityChangeResponse response = (SuspiciousIdentityChangeResponse) suspiciousIdentityStoreRequest.doRequest( );
        return Response.status( response.getStatus( ).getHttpCode( ) ).entity( response ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
    }

    @PUT
    @Path( Constants.EXCLUSION_PATH )
    @Consumes( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Exclude identities", notes = "Exclude identities from duplicate suspicions.", response = SuspiciousIdentityExcludeResponse.class )
    @ApiResponses( value = {
            @ApiResponse( code = 201, message = "Success" ), @ApiResponse( code = 403, message = "Failure" )
    } )
    public Response excludeSuspiciousIdentity(
            @ApiParam( name = "Request body", value = "An Identity exclusion request" ) SuspiciousIdentityExcludeRequest suspiciousIdentityExcludeRequest,
            @ApiParam( name = Constants.PARAM_CLIENT_CODE, value = SwaggerConstants.CLIENT_CLIENT_CODE_DESCRIPTION ) @HeaderParam( Constants.PARAM_CLIENT_CODE ) final String strHeaderClientCode )
            throws IdentityStoreException
    {
        final String trustedClientCode = IdentityStoreService.getTrustedClientCode( strHeaderClientCode, null );
        final IdentityStoreSuspiciousExcludeRequest request = new IdentityStoreSuspiciousExcludeRequest( suspiciousIdentityExcludeRequest, trustedClientCode );
        final SuspiciousIdentityExcludeResponse response = (SuspiciousIdentityExcludeResponse) request.doRequest( );
        return Response.status( response.getStatus( ).getHttpCode( ) ).entity( response ).type( MediaType.APPLICATION_JSON_TYPE ).build( );

    }

    @POST
    @Path( Constants.CANCEL_IDENTITIES_EXCLUSION_PATH )
    @Consumes( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Cancel identities exclusion", notes = "Cancel identities exclusion from duplicate suspicions.", response = SuspiciousIdentityExcludeResponse.class )
    @ApiResponses( value = {
            @ApiResponse( code = 201, message = "Success" ), @ApiResponse( code = 403, message = "Failure" )
    } )
    public Response cancelSuspiciousIdentityExclusion(
            @ApiParam( name = "Request body", value = "An Identity exclusion cancel request" ) SuspiciousIdentityExcludeRequest suspiciousIdentityExcludeRequest,
            @ApiParam( name = Constants.PARAM_CLIENT_CODE, value = SwaggerConstants.CLIENT_CLIENT_CODE_DESCRIPTION ) @HeaderParam( Constants.PARAM_CLIENT_CODE ) final String strHeaderClientCode )
            throws IdentityStoreException
    {
        final String trustedClientCode = IdentityStoreService.getTrustedClientCode( strHeaderClientCode, null );
        final IdentityStoreSuspiciousCancelExclusionRequest request = new IdentityStoreSuspiciousCancelExclusionRequest( suspiciousIdentityExcludeRequest,
                trustedClientCode );
        final SuspiciousIdentityExcludeResponse response = (SuspiciousIdentityExcludeResponse) request.doRequest( );
        return Response.status( response.getStatus( ).getHttpCode( ) ).entity( response ).type( MediaType.APPLICATION_JSON_TYPE ).build( );

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
            @QueryParam( Constants.PARAM_CLIENT_CODE ) final String strQueryClientCode, @QueryParam( Constants.PARAM_RULE_PRIORITY ) final Integer priority )
            throws IdentityStoreException
    {
        final String trustedClientCode = IdentityStoreService.getTrustedClientCode( strHeaderClientCode, strQueryClientCode );
        final DuplicateRuleGetRequest request = new DuplicateRuleGetRequest( trustedClientCode, priority );
        final DuplicateRuleSummarySearchResponse entity = (DuplicateRuleSummarySearchResponse) request.doRequest( );
        return Response.status( entity.getStatus( ).getHttpCode( ) ).entity( entity ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
    }

    /**
     * Get SuspiciousIdentity List
     *
     * @return the SuspiciousIdentity List
     */
    @GET
    @Path( Constants.DUPLICATE_PATH + "/{customer_id}" )
    @Consumes( MediaType.APPLICATION_JSON )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Get list of identities that are duplicates of the provided customer_id's identity, according to the provided rule ID.", response = DuplicateSearchResponse.class )
    public Response findDuplicates( @ApiParam( name = "customer_id", value = "the id of the customer" ) @PathParam( "customer_id" ) final String customer_id,
            @ApiParam( name = Constants.PARAM_RULE_CODE, value = "the code of the rule" ) @QueryParam( Constants.PARAM_RULE_CODE ) final String ruleCode,
            @HeaderParam( Constants.PARAM_CLIENT_CODE ) final String strHeaderClientAppCode ) throws IdentityStoreException
    {
        final IdentityStoreFindDuplicatesRequest request = new IdentityStoreFindDuplicatesRequest( strHeaderClientAppCode, ruleCode, customer_id );
        final DuplicateSearchResponse duplicateSearchResponse = (DuplicateSearchResponse) request.doRequest( );
        return Response.status( Response.Status.OK ).entity( duplicateSearchResponse ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
    }

    @POST
    @Path( Constants.LOCK_PATH )
    @Consumes( MediaType.APPLICATION_JSON )
    @ApiOperation( value = "Lock an existing Suspicious Identity", notes = "The suspicious identity must exist." )
    @ApiResponses( value = {
            @ApiResponse( code = 201, message = "Success" ), @ApiResponse( code = 400, message = ERROR_DURING_TREATMENT + " with explanation message" ),
            @ApiResponse( code = 403, message = "Failure" ), @ApiResponse( code = 409, message = "Conflict" )
    } )
    public Response lock(
            @ApiParam( name = "Request body", value = "An Identity exclusion request" ) fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.lock.SuspiciousIdentityLockRequest request,
            @ApiParam( name = Constants.PARAM_CLIENT_CODE, value = SwaggerConstants.CLIENT_CLIENT_CODE_DESCRIPTION ) @HeaderParam( Constants.PARAM_CLIENT_CODE ) final String strHeaderClientCode )
            throws IdentityStoreException
    {
        final String trustedClientCode = IdentityStoreService.getTrustedClientCode( strHeaderClientCode, null );
        final IdentityStoreSuspiciousLockRequest suspiciousIdentityStoreLockRequest = new IdentityStoreSuspiciousLockRequest( trustedClientCode, request );
        final SuspiciousIdentityLockResponse suspiciousIdentityLockResponse = (SuspiciousIdentityLockResponse) suspiciousIdentityStoreLockRequest.doRequest( );
        return Response.status( Response.Status.OK ).entity( suspiciousIdentityLockResponse ).type( MediaType.APPLICATION_JSON_TYPE ).build( );
    }

}
