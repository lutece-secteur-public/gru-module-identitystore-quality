package fr.paris.lutece.plugins.identitystore.modules.quality.web.request;

import fr.paris.lutece.plugins.identitystore.v3.web.rs.AbstractIdentityStoreRequest;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.ResponseDto;
import fr.paris.lutece.plugins.identitystore.v3.web.rs.dto.common.ResponseStatusType;
import fr.paris.lutece.plugins.identitystore.web.exception.IdentityStoreException;
import fr.paris.lutece.test.LuteceTestCase;

import static org.junit.Assert.assertThrows;

public abstract class AbstractIdentityStoreRequestTest extends LuteceTestCase {

    protected static final String H_CLIENT_CODE = "TEST";
    protected static final String H_APP_CODE = "TEST";
    protected static final String H_AUTHOR_NAME = "test";
    protected static final String H_AUTHOR_TYPE = "admin";

    public abstract void test_1_RequestOK() throws Exception;

    public abstract void test_2_RequestKO() throws Exception;

    protected void executeRequestKO(final AbstractIdentityStoreRequest request, final String strTestCase,
                                    final Class<? extends IdentityStoreException> expectedException, final String expectedExceptionMessageKey) {
        final Exception e = assertThrows(strTestCase + " : request was expected to fail, but it was successfull.", Exception.class, request::doRequest);
        assertEquals(strTestCase + " : the exception that occured is not of the expected type. Exception message : " + e.getMessage(), expectedException,
                     e.getClass());
        if (expectedExceptionMessageKey != null) {
            final String exceptionMsgKey = ((IdentityStoreException) e).getLocaleMessageKey();
            assertEquals(strTestCase + " : the exception message key does not match the expected key. Exception message key : " + exceptionMsgKey,
                         expectedExceptionMessageKey, exceptionMsgKey);
        }
    }

    protected ResponseDto executeRequestOK(final AbstractIdentityStoreRequest request, final String strTestCase,
                                           final ResponseStatusType expectedResponseStatusType) {
        try {
            final ResponseDto response = request.doRequest();
            assertNotNull(strTestCase + " : response is null", response);

            final ResponseStatusType responseStatusType = response.getStatus().getType();
            assertEquals(strTestCase + " : response doesn't have the expected status. Response status : " + responseStatusType, expectedResponseStatusType,
                         responseStatusType);
            return response;
        } catch (final IdentityStoreException e) {
            fail(strTestCase + " : request was expected to succeed, but it failed. Exception message : " + e.getMessage());
        }
        return null;
    }
}