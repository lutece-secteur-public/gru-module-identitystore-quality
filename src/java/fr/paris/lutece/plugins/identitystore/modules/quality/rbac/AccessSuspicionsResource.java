package fr.paris.lutece.plugins.identitystore.modules.quality.rbac;

import fr.paris.lutece.portal.service.rbac.RBACResource;

public class AccessSuspicionsResource implements RBACResource
{

    // RBAC management
    public static final String RESOURCE_TYPE = "ACCESS_SUSPICIONS";

    // Perimissions
    public static final String PERMISSION_PURGE = "PURGE";

    @Override
    public String getResourceTypeCode( )
    {
        return RESOURCE_TYPE;
    }

    @Override
    public String getResourceId( )
    {
        return null;
    }
}
