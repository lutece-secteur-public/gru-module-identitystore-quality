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

package fr.paris.lutece.plugins.identitystore.modules.quality.business;

import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.crud.SuspiciousIdentityChangeRequest;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.util.ReferenceList;

import java.util.List;
import java.util.Optional;

/**
 * This class provides instances management methods (create, find, ...) for SuspiciousIdentity objects
 */
public final class SuspiciousIdentityHome
{
    // Static variable pointed at the DAO instance
    private static ISuspiciousIdentityDAO _dao = SpringContextService.getBean( "identitystore-quality.suspiciousIdentityDAO" );
    private static Plugin _plugin = PluginService.getPlugin( "identitystore-quality" );

    /**
     * Private constructor - this class need not be instantiated
     */
    private SuspiciousIdentityHome( )
    {
    }

    /**
     * Create an instance of the suspiciousIdentity class
     * 
     * @param suspiciousIdentity
     *            The instance of the SuspiciousIdentity which contains the informations to store
     * @return The instance of suspiciousIdentity which has been created with its primary key.
     */
    public static SuspiciousIdentity create( SuspiciousIdentity suspiciousIdentity )
    {
        _dao.insert( suspiciousIdentity, _plugin );

        return suspiciousIdentity;
    }

    /**
     * Update of the suspiciousIdentity which is specified in parameter
     * 
     * @param suspiciousIdentity
     *            The instance of the SuspiciousIdentity which contains the data to store
     * @return The instance of the suspiciousIdentity which has been updated
     */
    public static SuspiciousIdentity update( SuspiciousIdentity suspiciousIdentity )
    {
        _dao.store( suspiciousIdentity, _plugin );

        return suspiciousIdentity;
    }

    /**
     * Update of the suspiciousIdentity which is specified in parameter
     *
     * @param suspiciousIdentity
     *            The instance of the SuspiciousIdentity which contains the data to store
     * @return The instance of the suspiciousIdentity which has been updated
     */
    public static void exclude( SuspiciousIdentity suspiciousIdentityMaster, SuspiciousIdentity suspiciousIdentityChild, int idRule )
    {
        _dao.insertExcluded( suspiciousIdentityMaster, suspiciousIdentityChild, idRule, _plugin );

    }

    /**
     * Remove the suspiciousIdentity whose identifier is specified in parameter
     * 
     * @param nKey
     *            The suspiciousIdentity Id
     */
    public static void remove( int nKey )
    {
        _dao.delete( nKey, _plugin );
    }

    /**
     * Returns an instance of a suspiciousIdentity whose identifier is specified in parameter
     * 
     * @param nKey
     *            The suspiciousIdentity primary key
     * @return an instance of SuspiciousIdentity
     */
    public static Optional<SuspiciousIdentity> findByPrimaryKey( int nKey )
    {
        return _dao.load( nKey, _plugin );
    }

    /**
     * Load the data of all the suspiciousIdentity objects and returns them as a list
     * 
     * @param max
     *            max number of suspicious identities to return
     * @return the list which contains the data of all the suspiciousIdentity objects
     */
    public static List<SuspiciousIdentity> getSuspiciousIdentitysList( final Integer rule, final int max )
    {
        return _dao.selectSuspiciousIdentitysList( rule, max, _plugin );
    }

    /**
     * Load the id of all the suspiciousIdentity objects and returns them as a list
     * 
     * @return the list which contains the id of all the suspiciousIdentity objects
     */
    public static List<Integer> getIdSuspiciousIdentitysList( )
    {
        return _dao.selectIdSuspiciousIdentitysList( _plugin );
    }

    /**
     * Load the data of all the suspiciousIdentity objects and returns them as a referenceList
     * 
     * @return the referenceList which contains the data of all the suspiciousIdentity objects
     */
    public static ReferenceList getSuspiciousIdentitysReferenceList( )
    {
        return _dao.selectSuspiciousIdentitysReferenceList( _plugin );
    }

    /**
     * Load the data of all the avant objects and returns them as a list
     * 
     * @param listIds
     *            liste of ids
     * @return the list which contains the data of all the avant objects
     */
    public static List<SuspiciousIdentity> getSuspiciousIdentitysListByIds( List<Integer> listIds )
    {
        return _dao.selectSuspiciousIdentitysListByIds( _plugin, listIds );
    }

    public static SuspiciousIdentity selectByCustomerID( String customerId )
    {
        return _dao.selectByCustomerID( customerId, _plugin );
    }

    public static void purge( )
    {
        _dao.purge( _plugin );
    }

    public static int countSuspiciousIdentity( final int ruleId )
    {
        return _dao.countSuspiciousIdentities( ruleId, _plugin );
    }
}
