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

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentity;
import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspiciousIdentityHome;
import fr.paris.lutece.portal.service.admin.AccessDeniedException;
import fr.paris.lutece.portal.service.message.AdminMessage;
import fr.paris.lutece.portal.service.message.AdminMessageService;
import fr.paris.lutece.portal.service.security.SecurityTokenService;
import fr.paris.lutece.portal.service.util.AppException;
import fr.paris.lutece.portal.util.mvc.admin.annotations.Controller;
import fr.paris.lutece.portal.util.mvc.commons.annotations.Action;
import fr.paris.lutece.portal.util.mvc.commons.annotations.View;
import fr.paris.lutece.util.html.AbstractPaginator;
import fr.paris.lutece.util.url.UrlItem;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides the user interface to manage SuspiciousIdentity features ( manage, create, modify, remove )
 */
@Controller( controllerJsp = "ManageSuspiciousIdentitys.jsp", controllerPath = "jsp/admin/plugins/identitystore/modules/quality/", right = "IDENTITYSTORE_QUALITY_MANAGEMENT" )
public class ManageSuspiciousIdentitys extends AbstractManageQualityJspBean<Integer, SuspiciousIdentity>
{
    // Templates
    private static final String TEMPLATE_MANAGE_SUSPICIOUSIDENTITYS = "/admin/plugins/identitystore/modules/quality/manage_suspiciousidentitys.html";
    private static final String TEMPLATE_CREATE_SUSPICIOUSIDENTITY = "/admin/plugins/identitystore/modules/quality/create_suspiciousidentity.html";
    private static final String TEMPLATE_MODIFY_SUSPICIOUSIDENTITY = "/admin/plugins/identitystore/modules/quality/modify_suspiciousidentity.html";

    // Parameters
    private static final String PARAMETER_ID_SUSPICIOUSIDENTITY = "id";

    // Properties for page titles
    private static final String PROPERTY_PAGE_TITLE_MANAGE_SUSPICIOUSIDENTITYS = "module.identitystore.quality.manage_suspiciousidentitys.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_MODIFY_SUSPICIOUSIDENTITY = "module.identitystore.quality.modify_suspiciousidentity.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_CREATE_SUSPICIOUSIDENTITY = "module.identitystore.quality.create_suspiciousidentity.pageTitle";

    // Markers
    private static final String MARK_SUSPICIOUSIDENTITY_LIST = "suspiciousidentity_list";
    private static final String MARK_SUSPICIOUSIDENTITY = "suspiciousidentity";

    private static final String JSP_MANAGE_SUSPICIOUSIDENTITYS = "jsp/admin/plugins/identitystore/modules/quality/ManageSuspiciousIdentitys.jsp";

    // Properties
    private static final String MESSAGE_CONFIRM_REMOVE_SUSPICIOUSIDENTITY = "module.identitystore.quality.message.confirmRemoveSuspiciousIdentity";

    // Validations
    private static final String VALIDATION_ATTRIBUTES_PREFIX = "module.identitystore.quality.model.entity.suspiciousidentity.attribute.";

    // Views
    private static final String VIEW_MANAGE_SUSPICIOUSIDENTITYS = "manageSuspiciousIdentitys";
    private static final String VIEW_CREATE_SUSPICIOUSIDENTITY = "createSuspiciousIdentity";
    private static final String VIEW_MODIFY_SUSPICIOUSIDENTITY = "modifySuspiciousIdentity";

    // Actions
    private static final String ACTION_CREATE_SUSPICIOUSIDENTITY = "createSuspiciousIdentity";
    private static final String ACTION_MODIFY_SUSPICIOUSIDENTITY = "modifySuspiciousIdentity";
    private static final String ACTION_REMOVE_SUSPICIOUSIDENTITY = "removeSuspiciousIdentity";
    private static final String ACTION_CONFIRM_REMOVE_SUSPICIOUSIDENTITY = "confirmRemoveSuspiciousIdentity";

    // Infos
    private static final String INFO_SUSPICIOUSIDENTITY_CREATED = "module.identitystore.quality.info.suspiciousidentity.created";
    private static final String INFO_SUSPICIOUSIDENTITY_UPDATED = "module.identitystore.quality.info.suspiciousidentity.updated";
    private static final String INFO_SUSPICIOUSIDENTITY_REMOVED = "module.identitystore.quality.info.suspiciousidentity.removed";

    // Errors
    private static final String ERROR_RESOURCE_NOT_FOUND = "Resource not found";

    // Session variable to store working values
    private SuspiciousIdentity _suspiciousidentity;
    private List<Integer> _listIdSuspiciousIdentitys;

    /**
     * Build the Manage View
     * 
     * @param request
     *            The HTTP request
     * @return The page
     */
    @View( value = VIEW_MANAGE_SUSPICIOUSIDENTITYS, defaultView = true )
    public String getManageSuspiciousIdentitys( HttpServletRequest request )
    {
        _suspiciousidentity = null;

        if ( request.getParameter( AbstractPaginator.PARAMETER_PAGE_INDEX ) == null || _listIdSuspiciousIdentitys.isEmpty( ) )
        {
            _listIdSuspiciousIdentitys = SuspiciousIdentityHome.getIdSuspiciousIdentitysList( );
        }

        Map<String, Object> model = getPaginatedListModel( request, MARK_SUSPICIOUSIDENTITY_LIST, _listIdSuspiciousIdentitys, JSP_MANAGE_SUSPICIOUSIDENTITYS );

        return getPage( PROPERTY_PAGE_TITLE_MANAGE_SUSPICIOUSIDENTITYS, TEMPLATE_MANAGE_SUSPICIOUSIDENTITYS, model );
    }

    /**
     * Get Items from Ids list
     * 
     * @param listIds
     * @return the populated list of items corresponding to the id List
     */
    @Override
    List<SuspiciousIdentity> getItemsFromIds( List<Integer> listIds )
    {
        List<SuspiciousIdentity> listSuspiciousIdentity = SuspiciousIdentityHome.getSuspiciousIdentitysListByIds( listIds );

        // keep original order
        return listSuspiciousIdentity.stream( ).sorted( Comparator.comparingInt( notif -> listIds.indexOf( notif.getId( ) ) ) ).collect( Collectors.toList( ) );
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

        Map<String, Object> model = getModel( );
        model.put( MARK_SUSPICIOUSIDENTITY, _suspiciousidentity );
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
