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

package fr.paris.lutece.plugins.identitystore.modules.quality.web;

import fr.paris.lutece.plugins.identitystore.business.attribute.AttributeKey;
import fr.paris.lutece.plugins.identitystore.business.attribute.AttributeKeyHome;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.ExcludedIdentities;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.plugins.identitystore.business.rules.duplicate.DuplicateRule;
import fr.paris.lutece.plugins.identitystore.modules.quality.service.SearchDuplicatesService;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleNotFoundException;
import fr.paris.lutece.plugins.identitystore.service.duplicate.DuplicateRuleService;
import fr.paris.lutece.plugins.identitystore.service.identity.IdentityService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.QualityDefinition;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.duplicate.DuplicateRuleSummaryDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.AttributeDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.portal.service.admin.AccessDeniedException;
import fr.paris.lutece.portal.service.message.AdminMessage;
import fr.paris.lutece.portal.service.message.AdminMessageService;
import fr.paris.lutece.portal.service.security.SecurityTokenService;
import fr.paris.lutece.portal.service.util.AppException;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.util.mvc.admin.annotations.Controller;
import fr.paris.lutece.portal.util.mvc.commons.annotations.Action;
import fr.paris.lutece.portal.util.mvc.commons.annotations.View;
import fr.paris.lutece.util.html.AbstractPaginator;
import fr.paris.lutece.util.url.UrlItem;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides the user interface to manage SuspiciousIdentity features ( manage, create, modify, remove )
 */
@Controller( controllerJsp = "ManageSuspiciousIdentitys.jsp", controllerPath = "jsp/admin/plugins/identitystore/modules/quality/", right = "IDENTITYSTORE_QUALITY_MANAGEMENT" )
public class ManageSuspiciousIdentitys extends AbstractManageQualityJspBean
{
    // Templates
    private static final String TEMPLATE_MANAGE_SUSPICIOUSIDENTITYS = "/admin/plugins/identitystore/modules/quality/manage_suspiciousidentitys.html";
    private static final String TEMPLATE_CREATE_SUSPICIOUSIDENTITY = "/admin/plugins/identitystore/modules/quality/create_suspiciousidentity.html";
    private static final String TEMPLATE_MODIFY_SUSPICIOUSIDENTITY = "/admin/plugins/identitystore/modules/quality/modify_suspiciousidentity.html";
    private static final String TEMPLATE_CHOOSE_DUPLICATE_TYPE = "/admin/plugins/identitystore/modules/quality/choose_duplicate_type.html";
    private static final String TEMPLATE_SEARCH_DUPLICATES = "/admin/plugins/identitystore/modules/quality/search_duplicates.html";
    private static final String TEMPLATE_SELECT_IDENTITIES = "/admin/plugins/identitystore/modules/quality/select_identities.html";
    private static final String TEMPLATE_MANAGE_EXCLUDED_IDENTITIES = "/admin/plugins/identitystore/modules/quality/manage_excluded_identities.html";
    private static final String TEMPLATE_DISPLAY_IDENTITIES = "/admin/plugins/identitystore/modules/quality/display_excluded_identities.html";

    // Parameters
    private static final String PARAMETER_ID_SUSPICIOUSIDENTITY = "id";
    private static final String PARAMETER_FIRST_CUSTOMER_ID = "first_customer_id";
    private static final String PARAMETER_SECOND_CUSTOMER_ID = "second_customer_id";

    // Properties for page titles
    private static final String PROPERTY_PAGE_TITLE_MANAGE_SUSPICIOUSIDENTITYS = "module.identitystore.quality.choose_duplicate_type.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_MODIFY_SUSPICIOUSIDENTITY = "module.identitystore.quality.modify_suspiciousidentity.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_CREATE_SUSPICIOUSIDENTITY = "module.identitystore.quality.create_suspiciousidentity.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_SEARCH_DUPLICATES = "module.identitystore.quality.search_duplicates.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_SELECT_IDENTITIES = "module.identitystore.quality.select_identities.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_MANAGE_EXCLUDED_IDENTITIES = "module.identitystore.quality.manage_excluded_identities.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_DISPLAY_IDENTITIES = "module.identitystore.quality.display_excluded_identities.pageTitle";

    // Markers
    private static final String MARK_SUSPICIOUSIDENTITY_LIST = "suspiciousidentity_list";
    private static final String MARK_EXCLUDED_IDENTITIES_LIST = "excluded_identities_list";
    private static final String MARK_SUSPICIOUSIDENTITY = "suspiciousidentity";
    private static final String MARK_DUPLICATE_RULE_LIST = "duplicate_rule_list";
    private static final String MARK_DUPLICATE_HOLDER_LIST = "duplicate_holder_list";
    private static final String MARK_READABLE_ATTRIBUTES = "readable_attribute_list";
    private static final String MARK_IDENTITY_LIST = "identity_list";
    private static final String MARK_FIRST_CUSTOMER_ID = "first_customer_id";
    private static final String MARK_SECOND_CUSTOMER_ID = "second_customer_id";
    private static final String MARK_DUPLICATE_RULE = "rule";
    private static final String MARK_SUSPICIOUS_IDENTITY_CUID = "suspicious_cuid";

    private static final String JSP_MANAGE_SUSPICIOUSIDENTITYS = "jsp/admin/plugins/identitystore/modules/quality/ManageSuspiciousIdentitys.jsp";

    // Properties
    private static final String MESSAGE_CONFIRM_REMOVE_SUSPICIOUSIDENTITY = "module.identitystore.quality.message.confirmRemoveSuspiciousIdentity";
    private static final String MESSAGE_CONFIRM_REMOVE_EXCLUDED_IDENTITIES = "module.identitystore.quality.manage_excluded_identities.message.removeExcludedIdentities";

    // Validations
    private static final String VALIDATION_ATTRIBUTES_PREFIX = "module.identitystore.quality.model.entity.suspiciousidentity.attribute.";

    // Views
    private static final String VIEW_MANAGE_SUSPICIOUSIDENTITYS = "manageSuspiciousIdentitys";
    private static final String VIEW_CREATE_SUSPICIOUSIDENTITY = "createSuspiciousIdentity";
    private static final String VIEW_MODIFY_SUSPICIOUSIDENTITY = "modifySuspiciousIdentity";
    private static final String VIEW_CHOOSE_DUPLICATE_TYPE = "chooseDuplicateType";
    private static final String VIEW_SEARCH_DUPLICATES = "searchDuplicates";
    private static final String VIEW_SELECT_IDENTITIES = "selectIdentities";
    private static final String VIEW_MANAGE_EXCLUDED_IDENTITIES = "manageExcludedIdentities";
    private static final String VIEW_DISPLAY_EXCLUDED_IDENTITIES = "displayExcludedIdentities";

    // Actions
    private static final String ACTION_CREATE_SUSPICIOUSIDENTITY = "createSuspiciousIdentity";
    private static final String ACTION_MODIFY_SUSPICIOUSIDENTITY = "modifySuspiciousIdentity";
    private static final String ACTION_REMOVE_SUSPICIOUSIDENTITY = "removeSuspiciousIdentity";
    private static final String ACTION_REMOVE_EXCLUDED_IDENTITIES = "removeExcludedIdentities";
    private static final String ACTION_CONFIRM_REMOVE_SUSPICIOUSIDENTITY = "confirmRemoveSuspiciousIdentity";
    private static final String ACTION_CONFIRM_REMOVE_EXCLUDED_IDENTITIES = "confirmRemoveExcludedIdentities";

    // Infos
    private static final String INFO_SUSPICIOUSIDENTITY_CREATED = "module.identitystore.quality.info.suspiciousidentity.created";
    private static final String INFO_SUSPICIOUSIDENTITY_UPDATED = "module.identitystore.quality.info.suspiciousidentity.updated";
    private static final String INFO_SUSPICIOUSIDENTITY_REMOVED = "module.identitystore.quality.info.suspiciousidentity.removed";

    // Errors
    private static final String ERROR_RESOURCE_NOT_FOUND = "Resource not found";
    private static final String PARAM_RULE_CODE = "rule-code";
    private static final String PARAM_CUID = "cuid";

    // Session variable to store working values
    private final Comparator<IdentityDto> connectedComparator = Comparator.comparing( IdentityDto::isMonParisActive ).reversed( );
    private final Comparator<QualityDefinition> qualityComparator = Comparator.comparingDouble( QualityDefinition::getQuality ).reversed( );
    private final Comparator<IdentityDto> orderingComparator = connectedComparator.thenComparing( IdentityDto::getQuality, qualityComparator );
    private SuspiciousIdentity _suspiciousidentity;
    private List<Integer> _listIdSuspiciousIdentitys;
    private String _currentRuleCode;
    private DuplicateRule _currentRule;

    /**
     *
     * @param request
     * @return
     */
    @View( value = VIEW_CHOOSE_DUPLICATE_TYPE, defaultView = true )
    public String getDuplicateTypes( final HttpServletRequest request )
    {
        _currentRule = null;
        final List<DuplicateRuleSummaryDto> duplicateRules = new ArrayList<>( );
        try
        {
            duplicateRules.addAll( DuplicateRuleService.instance( ).findSummaries( null ) );
            duplicateRules.sort( Comparator.comparingInt( DuplicateRuleSummaryDto::getPriority ).thenComparing( DuplicateRuleSummaryDto::getName ) );
        }
        catch( final IdentityStoreException e )
        {
            AppLogService.error( "Error while fetching duplicate calculation rules.", e );
            addError( e.getMessage( ) );
        }

        final Map<String, Object> model = getModel( );
        model.put( MARK_DUPLICATE_RULE_LIST, duplicateRules );

        return getPage( PROPERTY_PAGE_TITLE_MANAGE_SUSPICIOUSIDENTITYS, TEMPLATE_CHOOSE_DUPLICATE_TYPE, model );
    }

    /**
     * Process the data to send the search request and returns the duplicates search form and results
     *
     * @param request
     *            The Http request
     * @return the html code of the duplicate form
     */
    @View( value = VIEW_SEARCH_DUPLICATES )
    public String getSearchDuplicates( final HttpServletRequest request )
    {
        _currentRule = null;
        _currentRuleCode = request.getParameter( PARAM_RULE_CODE );
        if ( StringUtils.isBlank( _currentRuleCode ) )
        {
            AppLogService.error( "Rule code must be specified in request." );
            return getDuplicateTypes( request );
        }

        final List<IdentityDto> identities = new ArrayList<>( );
        final List<AttributeKey> readableAttributes = new ArrayList<>( );
        try
        {
            _currentRule = DuplicateRuleService.instance( ).get( _currentRuleCode );
            final List<String> listSuspiciousIdentities = SuspiciousIdentityHome.getSuspiciousIdentityCuidsList( _currentRuleCode );
            final List<IdentityDto> qualifiedIdentityList = new ArrayList<>( );
            for ( final String cuid : listSuspiciousIdentities )
            {
                qualifiedIdentityList.add( IdentityService.instance( ).getQualifiedIdentity( cuid ) );
            }
            identities.addAll( qualifiedIdentityList );
            if ( CollectionUtils.isEmpty( identities ) )
            {
                addInfo( "No suspicous identities found for rule id " + _currentRuleCode );
                return getDuplicateTypes( request );
            }
            identities.sort( getAttributeComparator( Constants.PARAM_FAMILY_NAME ).thenComparing( getAttributeComparator( Constants.PARAM_FIRST_NAME ) )
                    .thenComparing( getAttributeComparator( Constants.PARAM_BIRTH_DATE ) ) );

            final List<AttributeKey> pivotAttributes = AttributeKeyHome.getAttributeKeysList( ).stream( ).filter( AttributeKey::getPivot )
                    .collect( Collectors.toList( ) );
            readableAttributes.addAll( pivotAttributes );
            readableAttributes.sort( Comparator.comparingInt( AttributeKey::getId ) );
        }
        catch( final IdentityStoreException e )
        {
            AppLogService.error( "Error while fetching potential identity duplicates.", e );
            addError( "Error while fetching potential identity duplicates: " + e.getMessage( ) );
        }

        final Map<String, String> parameters = new HashMap<>( );
        parameters.put( PARAM_RULE_CODE, _currentRuleCode );
        parameters.put( "view_" + VIEW_SEARCH_DUPLICATES, "" );

        final Map<String, Object> model = getPaginatedListModel( request, MARK_DUPLICATE_HOLDER_LIST, identities, JSP_MANAGE_SUSPICIOUSIDENTITYS, parameters );
        model.put( MARK_READABLE_ATTRIBUTES, readableAttributes );
        model.put( MARK_DUPLICATE_RULE, _currentRule );

        return getPage( PROPERTY_PAGE_TITLE_SEARCH_DUPLICATES, TEMPLATE_SEARCH_DUPLICATES, model );
    }

    private Comparator<IdentityDto> getAttributeComparator( final String attributeKey )
    {
        return Comparator.comparing( qualifiedIdentity -> {
            final AttributeDto attribute = qualifiedIdentity.getAttributes( ).stream( ).filter( attr -> attr.getKey( ).equals( attributeKey ) ).findFirst( )
                    .orElse( null );
            return attribute != null ? attribute.getValue( ) : "";
        } );
    }

    /**
     * Returns the form to select which identities to process
     *
     * @param request
     * @return
     */
    @View( value = VIEW_SELECT_IDENTITIES )
    public String getSelectIdentities( final HttpServletRequest request )
    {
        _currentRule = null;
        final IdentityDto suspiciousIdentity;
        final List<AttributeKey> readableAttributes = new ArrayList<>( );
        try
        {
            final String customerId = request.getParameter( PARAM_CUID );
            if ( StringUtils.isEmpty( customerId ) )
            {
                addError( "Customer ID must be specified in request " + customerId );
                return getDuplicateTypes( request );
            }

            suspiciousIdentity = IdentityService.instance( ).getQualifiedIdentity( customerId );
            if ( suspiciousIdentity == null )
            {
                addError( "Could not find suspiciousIdentity with customer ID " + customerId );
                return getDuplicateTypes( request );
            }
            readableAttributes.addAll( AttributeKeyHome.getAttributeKeysList( ) );
            final Comparator<AttributeKey> sortById = Comparator.comparing( AttributeKey::getId );
            final Comparator<AttributeKey> sortByPivot = ( o1, o2 ) -> Boolean.compare( o2.getPivot( ), o1.getPivot( ) );
            readableAttributes.sort( sortByPivot.thenComparing( sortById ) );
        }
        catch( final IdentityStoreException e )
        {
            addError( "An error occurred when fetching suspiciousIdentity : " + e.getMessage( ) );
            return getDuplicateTypes( request );
        }

        final List<IdentityDto> identityList = new ArrayList<>( );
        try
        {
            _currentRule = DuplicateRuleService.instance( ).get( _currentRuleCode );
            final DuplicateSearchResponse duplicateSearchResponse = SearchDuplicatesService.instance( ).findDuplicates( suspiciousIdentity,
                    Collections.singletonList( _currentRuleCode ) );

            if ( CollectionUtils.isEmpty( duplicateSearchResponse.getIdentities( ) ) )
            {
                addError( "No duplicate could be found." );
                return getDuplicateTypes( request );
            }
            identityList.addAll( duplicateSearchResponse.getIdentities( ) );
            identityList.add( suspiciousIdentity );
            /* Order identity list by connected identities, then best quality */
            identityList.sort( orderingComparator );
        }
        catch( final IdentityStoreException e )
        {
            addError( "An error occurred when fetching duplicate: " + e.getMessage( ) );
            return getDuplicateTypes( request );
        }

        final Map<String, Object> model = getModel( );
        model.put( MARK_IDENTITY_LIST, identityList );
        model.put( MARK_READABLE_ATTRIBUTES, readableAttributes );
        model.put( MARK_DUPLICATE_RULE, _currentRule );
        model.put( MARK_SUSPICIOUS_IDENTITY_CUID, suspiciousIdentity.getCustomerId( ) );

        return getPage( PROPERTY_PAGE_TITLE_SELECT_IDENTITIES, TEMPLATE_SELECT_IDENTITIES, model );
    }

    /**
     * Build the Manage View
     *
     * @param request
     *            The HTTP request
     * @return The page
     */
    @View( value = VIEW_MANAGE_EXCLUDED_IDENTITIES )
    public String getManageExcludedIdentities( HttpServletRequest request )
    {
        final List<ExcludedIdentities> excludedIdentitiesList = SuspiciousIdentityHome.getExcludedIdentitiesList( );
        if ( CollectionUtils.isEmpty( excludedIdentitiesList ) )
        {
            addInfo( "No excluded identities found" );
        }

        final HashMap<Object, Object> parameters = new HashMap<>( );
        parameters.put( "view_" + VIEW_MANAGE_EXCLUDED_IDENTITIES, "" );
        final Map<String, Object> model = getPaginatedListModel( request, MARK_EXCLUDED_IDENTITIES_LIST, excludedIdentitiesList, JSP_MANAGE_SUSPICIOUSIDENTITYS,
                parameters );

        return getPage( PROPERTY_PAGE_TITLE_MANAGE_EXCLUDED_IDENTITIES, TEMPLATE_MANAGE_EXCLUDED_IDENTITIES, model );
    }

    /**
     * Returns the form to select which identities to process
     *
     * @param request
     * @return
     */
    @View( value = VIEW_DISPLAY_EXCLUDED_IDENTITIES )
    public String getDisplayExcludedIdentities( final HttpServletRequest request )
    {
        final IdentityDto firstIdentity;
        final IdentityDto secondIdentity;
        final List<IdentityDto> identities = new ArrayList<>( );
        final List<AttributeKey> readableAttributes = new ArrayList<>( );
        String firstCustomerId;
        String secondCustomerId;
        try
        {
            firstCustomerId = request.getParameter( "first_customer_id" );
            secondCustomerId = request.getParameter( "second_customer_id" );
            if ( StringUtils.isEmpty( firstCustomerId ) )
            {
                addError( "First Customer ID must be specified in request " );
                return getDuplicateTypes( request );
            }

            if ( StringUtils.isEmpty( secondCustomerId ) )
            {
                addError( "Second Customer ID must be specified in request " + firstCustomerId );
                return getDuplicateTypes( request );
            }

            firstIdentity = IdentityService.instance( ).getQualifiedIdentity( firstCustomerId );
            if ( firstIdentity == null )
            {
                addError( "Could not find first identity with customer ID " + firstCustomerId );
                return getDuplicateTypes( request );
            }

            secondIdentity = IdentityService.instance( ).getQualifiedIdentity( secondCustomerId );
            if ( secondIdentity == null )
            {
                addError( "Could not find second identity with customer ID " + secondIdentity );
                return getDuplicateTypes( request );
            }
            identities.add( firstIdentity );
            identities.add( secondIdentity );
            readableAttributes.addAll( AttributeKeyHome.getAttributeKeysList( ) );
            final Comparator<AttributeKey> sortById = Comparator.comparing( AttributeKey::getId );
            final Comparator<AttributeKey> sortByPivot = ( o1, o2 ) -> Boolean.compare( o2.getPivot( ), o1.getPivot( ) );
            readableAttributes.sort( sortByPivot.thenComparing( sortById ) );
        }
        catch( final IdentityStoreException e )
        {
            addError( "An error occurred when fetching firstIdentity : " + e.getMessage( ) );
            return getDuplicateTypes( request );
        }

        final Map<String, Object> model = getModel( );
        model.put( MARK_IDENTITY_LIST, identities );
        model.put( MARK_FIRST_CUSTOMER_ID, firstCustomerId );
        model.put( MARK_SECOND_CUSTOMER_ID, secondCustomerId );
        model.put( MARK_READABLE_ATTRIBUTES, readableAttributes );

        return getPage( PROPERTY_PAGE_TITLE_DISPLAY_IDENTITIES, TEMPLATE_DISPLAY_IDENTITIES, model );
    }

    /**
     * Manages the removal form of a excluded identities whose identifier is in the http request
     *
     * @param request
     *            The Http request
     * @return the html code to confirm
     */
    @Action( ACTION_CONFIRM_REMOVE_EXCLUDED_IDENTITIES )
    public String getConfirmRemoveSExcludedIdentities( HttpServletRequest request )
    {
        final String firstCustomerId = request.getParameter( PARAMETER_FIRST_CUSTOMER_ID );
        final String secondCustomerId = request.getParameter( PARAMETER_SECOND_CUSTOMER_ID );
        final UrlItem url = new UrlItem( getActionUrl( ACTION_REMOVE_EXCLUDED_IDENTITIES ) );
        url.addParameter( PARAMETER_FIRST_CUSTOMER_ID, firstCustomerId );
        url.addParameter( PARAMETER_SECOND_CUSTOMER_ID, secondCustomerId );

        final String strMessageUrl = AdminMessageService.getMessageUrl( request, MESSAGE_CONFIRM_REMOVE_EXCLUDED_IDENTITIES, url.getUrl( ),
                AdminMessage.TYPE_CONFIRMATION );

        return redirect( request, strMessageUrl );
    }

    /**
     * Handles the removal form of a suspiciousidentity
     *
     * @param request
     *            The Http request
     * @return the jsp URL to display the form to manage suspiciousidentitys
     */
    @Action( ACTION_REMOVE_EXCLUDED_IDENTITIES )
    public String doRemoveExcludedIdentities( HttpServletRequest request )
    {
        final String firstCustomerId = request.getParameter( PARAMETER_FIRST_CUSTOMER_ID );
        final String secondCustomerId = request.getParameter( PARAMETER_SECOND_CUSTOMER_ID );

        if ( StringUtils.isEmpty( firstCustomerId ) )
        {
            addError( "First Customer ID must be specified in request " );
            return getManageExcludedIdentities( request );
        }

        if ( StringUtils.isEmpty( secondCustomerId ) )
        {
            addError( "Second Customer ID must be specified in request " );
            return getManageExcludedIdentities( request );
        }

        SuspiciousIdentityHome.removeExcludedIdentities( firstCustomerId, secondCustomerId );
        addInfo( "Identities exclusion successfully removed", getLocale( ) );

        return redirectView( request, VIEW_MANAGE_EXCLUDED_IDENTITIES );
    }

    /**
     * Build the Manage View
     * 
     * @param request
     *            The HTTP request
     * @return The page
     */
    @View( value = VIEW_MANAGE_SUSPICIOUSIDENTITYS )
    public String getManageSuspiciousIdentitys( HttpServletRequest request )
    {
        _suspiciousidentity = null;

        if ( request.getParameter( AbstractPaginator.PARAMETER_PAGE_INDEX ) == null || _listIdSuspiciousIdentitys.isEmpty( ) )
        {
            _listIdSuspiciousIdentitys = SuspiciousIdentityHome.getIdSuspiciousIdentitysList( );
        }

        final HashMap<Object, Object> parameters = new HashMap<>( );
        parameters.put( "view_" + VIEW_MANAGE_SUSPICIOUSIDENTITYS, "" );
        Map<String, Object> model = getPaginatedListModel( request, MARK_SUSPICIOUSIDENTITY_LIST, _listIdSuspiciousIdentitys, JSP_MANAGE_SUSPICIOUSIDENTITYS,
                parameters );

        return getPage( PROPERTY_PAGE_TITLE_MANAGE_SUSPICIOUSIDENTITYS, TEMPLATE_MANAGE_SUSPICIOUSIDENTITYS, model );
    }

    /**
     * reset the _listIdSuspiciousIdentitys list
     */
    public void resetListId( )
    {
        _listIdSuspiciousIdentitys = new ArrayList<>( );
    }

    /**
     * Returns the form to create a suspiciousidentity
     *
     * @param request
     *            The Http request
     * @return the html code of the suspiciousidentity form
     */
    @View( VIEW_CREATE_SUSPICIOUSIDENTITY )
    public String getCreateSuspiciousIdentity( HttpServletRequest request )
    {
        _suspiciousidentity = ( _suspiciousidentity != null ) ? _suspiciousidentity : new SuspiciousIdentity( );
        List<DuplicateRule> duplicateRules = new ArrayList<>( );
        try
        {
            duplicateRules.addAll( DuplicateRuleService.instance( ).findAll( ) );
            duplicateRules.sort( Comparator.comparing( DuplicateRule::getPriority ).thenComparing( DuplicateRule::getName ) );
        }
        catch( DuplicateRuleNotFoundException e )
        {
            AppLogService.error( "Error while fetching duplicate rules.", e );
            addError( e.getMessage( ) );
        }

        final Map<String, Object> model = getModel( );
        model.put( MARK_SUSPICIOUSIDENTITY, _suspiciousidentity );
        model.put( MARK_DUPLICATE_RULE_LIST, duplicateRules );
        model.put( SecurityTokenService.MARK_TOKEN, SecurityTokenService.getInstance( ).getToken( request, ACTION_CREATE_SUSPICIOUSIDENTITY ) );

        return getPage( PROPERTY_PAGE_TITLE_CREATE_SUSPICIOUSIDENTITY, TEMPLATE_CREATE_SUSPICIOUSIDENTITY, model );
    }

    /**
     * Process the data capture form of a new suspiciousidentity
     *
     * @param request
     *            The Http Request
     * @return The Jsp URL of the process result
     * @throws AccessDeniedException
     */
    @Action( ACTION_CREATE_SUSPICIOUSIDENTITY )
    public String doCreateSuspiciousIdentity( HttpServletRequest request ) throws AccessDeniedException
    {
        populate( _suspiciousidentity, request, getLocale( ) );

        if ( !SecurityTokenService.getInstance( ).validate( request, ACTION_CREATE_SUSPICIOUSIDENTITY ) )
        {
            throw new AccessDeniedException( "Invalid security token" );
        }

        // Check constraints
        if ( !validateBean( _suspiciousidentity, VALIDATION_ATTRIBUTES_PREFIX ) )
        {
            return redirectView( request, VIEW_CREATE_SUSPICIOUSIDENTITY );
        }

        SuspiciousIdentityHome.create( _suspiciousidentity );
        addInfo( INFO_SUSPICIOUSIDENTITY_CREATED, getLocale( ) );
        resetListId( );

        return redirectView( request, VIEW_MANAGE_SUSPICIOUSIDENTITYS );
    }

    /**
     * Manages the removal form of a suspiciousidentity whose identifier is in the http request
     *
     * @param request
     *            The Http request
     * @return the html code to confirm
     */
    @Action( ACTION_CONFIRM_REMOVE_SUSPICIOUSIDENTITY )
    public String getConfirmRemoveSuspiciousIdentity( HttpServletRequest request )
    {
        int nId = Integer.parseInt( request.getParameter( PARAMETER_ID_SUSPICIOUSIDENTITY ) );
        UrlItem url = new UrlItem( getActionUrl( ACTION_REMOVE_SUSPICIOUSIDENTITY ) );
        url.addParameter( PARAMETER_ID_SUSPICIOUSIDENTITY, nId );

        String strMessageUrl = AdminMessageService.getMessageUrl( request, MESSAGE_CONFIRM_REMOVE_SUSPICIOUSIDENTITY, url.getUrl( ),
                AdminMessage.TYPE_CONFIRMATION );

        return redirect( request, strMessageUrl );
    }

    /**
     * Handles the removal form of a suspiciousidentity
     *
     * @param request
     *            The Http request
     * @return the jsp URL to display the form to manage suspiciousidentitys
     */
    @Action( ACTION_REMOVE_SUSPICIOUSIDENTITY )
    public String doRemoveSuspiciousIdentity( HttpServletRequest request )
    {
        int nId = Integer.parseInt( request.getParameter( PARAMETER_ID_SUSPICIOUSIDENTITY ) );

        SuspiciousIdentityHome.remove( nId );
        addInfo( INFO_SUSPICIOUSIDENTITY_REMOVED, getLocale( ) );
        resetListId( );

        return redirectView( request, VIEW_MANAGE_SUSPICIOUSIDENTITYS );
    }

    /**
     * Returns the form to update info about a suspiciousidentity
     *
     * @param request
     *            The Http request
     * @return The HTML form to update info
     */
    @View( VIEW_MODIFY_SUSPICIOUSIDENTITY )
    public String getModifySuspiciousIdentity( HttpServletRequest request )
    {
        int nId = Integer.parseInt( request.getParameter( PARAMETER_ID_SUSPICIOUSIDENTITY ) );

        if ( _suspiciousidentity == null || ( _suspiciousidentity.getId( ) != nId ) )
        {
            Optional<SuspiciousIdentity> optSuspiciousIdentity = SuspiciousIdentityHome.findByPrimaryKey( nId );
            _suspiciousidentity = optSuspiciousIdentity.orElseThrow( ( ) -> new AppException( ERROR_RESOURCE_NOT_FOUND ) );
        }

        Map<String, Object> model = getModel( );
        model.put( MARK_SUSPICIOUSIDENTITY, _suspiciousidentity );
        model.put( SecurityTokenService.MARK_TOKEN, SecurityTokenService.getInstance( ).getToken( request, ACTION_MODIFY_SUSPICIOUSIDENTITY ) );

        return getPage( PROPERTY_PAGE_TITLE_MODIFY_SUSPICIOUSIDENTITY, TEMPLATE_MODIFY_SUSPICIOUSIDENTITY, model );
    }

    /**
     * Process the change form of a suspiciousidentity
     *
     * @param request
     *            The Http request
     * @return The Jsp URL of the process result
     * @throws AccessDeniedException
     */
    @Action( ACTION_MODIFY_SUSPICIOUSIDENTITY )
    public String doModifySuspiciousIdentity( HttpServletRequest request ) throws AccessDeniedException
    {
        populate( _suspiciousidentity, request, getLocale( ) );

        if ( !SecurityTokenService.getInstance( ).validate( request, ACTION_MODIFY_SUSPICIOUSIDENTITY ) )
        {
            throw new AccessDeniedException( "Invalid security token" );
        }

        // Check constraints
        if ( !validateBean( _suspiciousidentity, VALIDATION_ATTRIBUTES_PREFIX ) )
        {
            return redirect( request, VIEW_MODIFY_SUSPICIOUSIDENTITY, PARAMETER_ID_SUSPICIOUSIDENTITY, _suspiciousidentity.getId( ) );
        }

        SuspiciousIdentityHome.update( _suspiciousidentity );
        addInfo( INFO_SUSPICIOUSIDENTITY_UPDATED, getLocale( ) );
        resetListId( );

        return redirectView( request, VIEW_MANAGE_SUSPICIOUSIDENTITYS );
    }
}
