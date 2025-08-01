package fr.paris.lutece.plugins.identitystore.modules.quality.service;

import fr.paris.lutece.plugins.identitystore.business.duplicates.suspicions.SuspicionActionHome;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.util.Constants;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.util.sql.TransactionManager;

import java.util.List;

public class SuspicionActionService {

    private static SuspicionActionService _instance;

    public static SuspicionActionService instance() {
        if (_instance == null) {
            _instance = new SuspicionActionService();
        }
        return _instance;
    }

    private SuspicionActionService() {

    }

    public void delete(final List<Integer> suspicionActionIds) throws IdentityStoreException {
        TransactionManager.beginTransaction(null);
        try {
            SuspicionActionHome.delete(suspicionActionIds);
            TransactionManager.commitTransaction(null);
        } catch (final Exception e) {
            TransactionManager.rollBack(null);
            throw new IdentityStoreException(e.getMessage(), Constants.PROPERTY_REST_ERROR_DURING_TREATMENT);
        }
    }
}
