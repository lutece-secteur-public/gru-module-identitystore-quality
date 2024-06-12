package fr.paris.lutece.plugins.identitystore.modules.quality.business;

import java.util.Arrays;

public enum DuplicatesDaemonLimitationMode {
    /**
     * The limitation of the rule is applied over every execution of the daemon.
     */
    GLOBAL,

    /**
     * The limitation of the rule is applied for a single execution of the daemon.
     */
    INCREMENTAL;

    /**
     * Returns the mode associated to the given key. If there is no value matching the key, the default mode is GLOBAL.
     * @param key the key to be found
     * @return the {@link DuplicatesDaemonLimitationMode} matching the given key
     */
    public static DuplicatesDaemonLimitationMode getMode( final String key )
    {
        return Arrays.stream( values( ) ).filter( mode -> mode.name( ).equalsIgnoreCase( key ) ).findFirst( ).orElse( GLOBAL );
    }
}
