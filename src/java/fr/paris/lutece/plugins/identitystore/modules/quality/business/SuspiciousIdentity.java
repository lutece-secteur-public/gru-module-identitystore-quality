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

import javax.validation.constraints.Size;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * This is the business class for the object SuspiciousIdentity
 */
public class SuspiciousIdentity implements Serializable
{
    private static final long serialVersionUID = 1L;

    // Variables declarations
    private int _nId;

    @NotEmpty( message = "#i18n{module.identitystore.quality.validation.suspiciousidentity.CustomerId.notEmpty}" )
    @Size( max = 50, message = "#i18n{module.identitystore.quality.validation.suspiciousidentity.CustomerId.size}" )
    private String _strCustomerId;

    private Integer _nIdDuplicateRule;
    private boolean _bIsDeleted;
    private Timestamp _dateCreationDate;

    private Timestamp _dateLastUpdateDate;

    /**
     * Returns the Id
     * 
     * @return The Id
     */
    public int getId( )
    {
        return _nId;
    }

    /**
     * Sets the Id
     * 
     * @param nId
     *            The Id
     */
    public void setId( int nId )
    {
        _nId = nId;
    }

    /**
     * Returns the CustomerId
     * 
     * @return The CustomerId
     */
    public String getCustomerId( )
    {
        return _strCustomerId;
    }

    /**
     * Sets the CustomerId
     * 
     * @param strCustomerId
     *            The CustomerId
     */
    public void setCustomerId( String strCustomerId )
    {
        _strCustomerId = strCustomerId;
    }

    /**
     * Gets the duplicate rule ID used to detect this suspicious identity
     * 
     * @return the duplicate rule ID
     */
    public Integer getIdDuplicateRule( )
    {
        return _nIdDuplicateRule;
    }

    /**
     * Sets the duplicate rule ID used to detect this suspicious identity
     * 
     * @param _nIdDuplicateRule
     *            the duplicate rule ID
     */
    public void setIdDuplicateRule( int _nIdDuplicateRule )
    {
        this._nIdDuplicateRule = _nIdDuplicateRule;
    }

    public Timestamp getCreationDate( )
    {
        return _dateCreationDate;
    }

    public void setCreationDate( Timestamp creationDate )
    {
        this._dateCreationDate = creationDate;
    }

    public Timestamp getLastUpdateDate( )
    {
        return _dateLastUpdateDate;
    }

    public void setLastUpdateDate( Timestamp lastUpdateDate )
    {
        this._dateLastUpdateDate = lastUpdateDate;
    }

    public boolean isDeleted( )
    {
        return _bIsDeleted;
    }

    /**
     * set true if all the attibutes of identity are deleted
     *
     * @param bIsDeleted
     *            if identity removed (softDelete)
     */
    public void setDeleted( boolean bIsDeleted )
    {
        _bIsDeleted = bIsDeleted;
    }
}
