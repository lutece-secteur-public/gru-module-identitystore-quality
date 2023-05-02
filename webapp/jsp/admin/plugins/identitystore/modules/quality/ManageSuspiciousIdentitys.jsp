<jsp:useBean id="managequalitySuspiciousIdentity" scope="session" class="fr.paris.lutece.plugins.identitystore.modules.quality.web.ManageSuspiciousIdentitys" />
<% String strContent = managequalitySuspiciousIdentity.processController ( request , response ); %>

<%@ page errorPage="../../../../ErrorPage.jsp" %>
<jsp:include page="../../../../AdminHeader.jsp" />

<%= strContent %>

<%@ include file="../../../../AdminFooter.jsp" %>
