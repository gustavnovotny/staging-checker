<%--
/**
 * Copyright (c) 2015-present Jorge Díaz All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
--%>

<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/sql" prefix="sql" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/xml" prefix="x" %>

<%@ taglib uri="http://liferay.com/tld/aui" prefix="aui" %>
<%@ taglib uri="http://liferay.com/tld/portlet" prefix="liferay-portlet" %>
<%@ taglib uri="http://liferay.com/tld/security" prefix="liferay-security" %>
<%@ taglib uri="http://liferay.com/tld/theme" prefix="liferay-theme" %>
<%@ taglib uri="http://liferay.com/tld/ui" prefix="liferay-ui" %>
<%@ taglib uri="http://liferay.com/tld/util" prefix="liferay-util" %>

<%@ page contentType="text/html; charset=UTF-8" %>

<%@ page import="com.liferay.portal.kernel.dao.search.SearchContainer" %>
<%@ page import="com.liferay.portal.kernel.log.Log" %>
<%@ page import="com.liferay.portal.kernel.util.Validator" %>
<%@ page import="com.liferay.portal.model.Company" %>

<%@ page import="java.util.EnumSet" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Map.Entry" %>
<%@ page import="java.util.Set" %>

<%@ page import="javax.portlet.PortletURL" %>

<%@ page import="jorgediazest.stagingchecker.ExecutionMode" %>
<%@ page import="jorgediazest.stagingchecker.output.StagingCheckerOutput" %>
<%@ page import="jorgediazest.stagingchecker.portlet.StagingCheckerPortlet" %>

<%@ page import="jorgediazest.util.data.Comparison" %>
<%@ page import="jorgediazest.util.model.Model" %>
<%@ page import="jorgediazest.util.output.OutputUtils" %>

<portlet:defineObjects />

<portlet:renderURL var="viewURL" />

<portlet:actionURL name="executeCheck" var="executeCheckURL" windowState="normal" />

<liferay-ui:header
	backURL="<%= viewURL %>"
	title="staging-checker"
/>

<%
	Log _log = StagingCheckerPortlet.getLogger();
	EnumSet<ExecutionMode> executionMode = (EnumSet<ExecutionMode>) request.getAttribute("executionMode");
	Map<Company, Long> companyProcessTime = (Map<Company, Long>) request.getAttribute("companyProcessTime");
	Map<Company, Map<Long, List<Comparison>>> companyResultDataMap = (Map<Company, Map<Long, List<Comparison>>>) request.getAttribute("companyResultDataMap");
	Map<Company, String> companyError = (Map<Company, String>) request.getAttribute("companyError");
	List<Model> modelList = (List<Model>) request.getAttribute("modelList");
	Set<String> filterClassNameSelected = (Set<String>) request.getAttribute("filterClassNameSelected");
	if (filterClassNameSelected == null) {
		filterClassNameSelected = new HashSet<String>();
	}
	List<Long> groupIdList = (List<Long>) request.getAttribute("groupIdList");
	List<String> groupDescriptionList = (List<String>) request.getAttribute("groupDescriptionList");
	Set<String> filterGroupIdSelected = (Set<String>) request.getAttribute("filterGroupIdSelected");
	if (filterGroupIdSelected == null) {
		filterGroupIdSelected = new HashSet<String>();
	}
	Locale locale = renderRequest.getLocale();
%>

<aui:form action="<%= executeCheckURL %>" method="POST" name="fm">
	<aui:fieldset>
		<aui:column>
			<aui:select name="outputFormat">
				<aui:option selected="true" value="Table"><liferay-ui:message key="output-format-table" /></aui:option>
				<aui:option value="CSV"><liferay-ui:message key="output-format-csv" /></aui:option>
			</aui:select>
			<aui:input helpMessage="output-both-exact-help" name="outputBothExact" type="checkbox" value="false" />
			<aui:input helpMessage="output-both-not-exact-help" name="outputBothNotExact" type="checkbox" value="true" />
			<aui:input helpMessage="output-staging-help" name="outputStaging" type="checkbox" value="true" />
			<aui:input helpMessage="output-live-help" name="outputLive" type="checkbox" value="true" />
		</aui:column>
		<aui:column>
			<aui:select helpMessage="filter-class-name-help" multiple="true" name="filterClassName" onChange='<%= renderResponse.getNamespace() + "disableReindexAndRemoveOrphansButtons(this);" %>' onClick='<%= renderResponse.getNamespace() + "disableReindexAndRemoveOrphansButtons(this);" %>' style="height: 240px; width: 250px;">
				<aui:option selected="<%= filterClassNameSelected.isEmpty() %>" value=""><liferay-ui:message key="all" /></aui:option>
				<aui:option disabled="true" value="-">--------</aui:option>

<%
				for (Model model : modelList) {
					String className = model.getClassName();
					String displayName = model.getDisplayName(locale);
					if (Validator.isNull(displayName)) {
						displayName = className;
					}
%>

					<aui:option selected="<%= filterClassNameSelected.contains(className) %>" value="<%= className %>"><%= displayName %></aui:option>

<%
				}
%>

			</aui:select>
		</aui:column>
		<aui:column>
			<aui:select helpMessage="filter-group-id-help" multiple="true" name="filterGroupId" onChange='<%= renderResponse.getNamespace() + "disableReindexAndRemoveOrphansButtons(this);" %>' onClick='<%= renderResponse.getNamespace() + "disableReindexAndRemoveOrphansButtons(this);" %>' style="height: 240px; width: 250px;">
				<aui:option selected='<%= filterGroupIdSelected.isEmpty() || filterGroupIdSelected.contains("-1000") %>' value="-1000"><liferay-ui:message key="filter-group-id-no-filter" /></aui:option>
				<aui:option disabled="true" value="-">--------</aui:option>

<%
				for (int i=0;i<groupIdList.size();i++) {
					String groupIdStr = "" + groupIdList.get(i);
%>

					<aui:option selected="<%= filterGroupIdSelected.contains(groupIdStr) %>" value="<%= groupIdStr %>"><%= groupDescriptionList.get(i) %></aui:option>

<%
				}
%>

			</aui:select>
		</aui:column>
		<aui:column>
			<aui:input helpMessage="number-of-threads-help" name="numberOfThreads" type="text" value='<%= request.getAttribute("numberOfThreads") %>' />
			<aui:input name="dumpAllObjectsToLog" type="checkbox" value="false" />
		</aui:column>
	</aui:fieldset>

	<aui:button-row>
		<aui:button type="submit" value="check-staging" />

		<aui:button onClick="<%= viewURL %>" type="cancel" value="clean" />
	</aui:button-row>
</aui:form>

<%
	if ((companyProcessTime != null) && (companyError != null)) {

		String outputFormat = request.getParameter("outputFormat");

		if (Validator.isNotNull(outputFormat)) {
			if (outputFormat.equals("CSV")) {
%>

	<%@ include file="/html/stagingchecker/output/result_csv.jspf" %>

<%
			}
			else if (outputFormat.equals("Table")) {
%>

	<%@ include file="/html/stagingchecker/output/result_table.jspf" %>

<%
			}
			else {
%>

	<%@ include file="/html/stagingchecker/output/result_error.jspf" %>

<%
			}
		}
	}
%>