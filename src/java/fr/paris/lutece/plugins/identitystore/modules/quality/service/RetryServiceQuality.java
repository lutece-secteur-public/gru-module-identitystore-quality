package fr.paris.lutece.plugins.identitystore.modules.quality.service;

import fr.paris.lutece.plugins.identitystore.service.indexer.elastic.index.service.RetryService;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.IdentityDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.search.DuplicateSearchResponse;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryServiceQuality extends RetryService
{

    public DuplicateSearchResponse callSearchDuplicateWithRetry ( IdentityDto identity, List<String> ruleCodes, List<String> attributesFilter) throws IdentityStoreException
    {
        final AtomicInteger failedCalls = new AtomicInteger( 0 );
        DuplicateSearchResponse response = SearchDuplicatesService.instance( ).findDuplicates( identity, ruleCodes , attributesFilter );
        while ( response == null )
        {
            final int nbRetry = failedCalls.getAndIncrement( );
            AppLogService.error( "Retry nb " + nbRetry );
            if ( nbRetry > MAX_RETRY )
            {
                AppLogService.error( "The number of retries exceeds the configured value of " + MAX_RETRY + ", interrupting.." );
                return null;
            }
            try
            {
                Thread.sleep( TEMPO_RETRY );
            }
            catch( InterruptedException e )
            {
                AppLogService.error( "Could thread sleep.. + " + e.getMessage( ) );
            }
            response = SearchDuplicatesService.instance( ).findDuplicates( identity, ruleCodes , attributesFilter );
        }
        return response;
    }

}
