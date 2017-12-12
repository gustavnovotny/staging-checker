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
<%@ page import="com.liferay.portal.kernel.model.Company" %>
<%@ page import="com.liferay.portal.kernel.theme.PortletDisplay" %>
<%@ page import="com.liferay.portal.kernel.theme.ThemeDisplay" %>
<%@ page import="com.liferay.portal.kernel.util.Validator" %>
<%@ page import="com.liferay.portal.kernel.util.WebKeys" %>

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

<portlet:defineObjects />

<portlet:renderURL var="viewURL" />

<%
	ThemeDisplay themeDisplay = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);

	PortletDisplay portletDisplay = themeDisplay.getPortletDisplay();

	String configurationURL = portletDisplay.getURLConfigurationJS();
%>

<portlet:actionURL name="executeCheck" var="executeCheckURL" windowState="normal" />

<script type="text/javascript">
	function showHide(shID) {
		if (document.getElementById(shID)) {
			if (document.getElementById(shID+'-show').style.display != 'none') {
				document.getElementById(shID+'-show').style.display = 'none';
				document.getElementById(shID).style.display = 'block';
			}
			else {
				document.getElementById(shID+'-show').style.display = 'inline';
				document.getElementById(shID).style.display = 'none';
			}
		}
	}
</script>

<div class="container-fluid-1280"><div class="card-horizontal main-content-card"><div class="panel-body">

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
	String updateMessage = (String) request.getAttribute("updateMessage");
%>

<%
	if (Validator.isNotNull(updateMessage)) {
%>

<div class="alert alert-warning"><%= updateMessage %></div>

<%
	}
%>

<aui:form action="<%= executeCheckURL %>" method="POST" name="fm">
	<aui:row>
		<aui:col width="33">
			<aui:input helpMessage="output-both-exact-help" name="outputBothExact" type="checkbox" value="false" />
			<aui:input helpMessage="output-both-not-exact-help" name="outputBothNotExact" type="checkbox" value="true" />
			<aui:input helpMessage="output-staging-help" name="outputStaging" type="checkbox" value="true" />
			<aui:input helpMessage="output-live-help" name="outputLive" type="checkbox" value="true" />
			<aui:fieldset>
				<aui:select helpMessage="filter-modified-date-help"  inlineLabel="left" name="filterModifiedDate" onClick='<%= renderResponse.getNamespace() + "disableReindexAndRemoveOrphansButtons(this);" + renderResponse.getNamespace() + "disableOutputOnlyInIndex(this);" %>' >
					<aui:option selected="true" value="0"><liferay-ui:message key="filter-group-id-no-filter" /></aui:option>
					<aui:option value="1"><liferay-ui:message key="1-hour" /></aui:option>
					<aui:option value="3"><liferay-ui:message key="3-hours" /></aui:option>
					<aui:option value="6"><liferay-ui:message key="6-hours" /></aui:option>
					<aui:option value="12"><liferay-ui:message key="12-hours" /></aui:option>
					<aui:option value="24"><liferay-ui:message key="1-day" /></aui:option>
					<aui:option value="72"><liferay-ui:message key="3-days" /></aui:option>
					<aui:option value="168"><liferay-ui:message key="1-week" /></aui:option>
					<aui:option value="336"><liferay-ui:message key="2-weeks" /></aui:option>
					<aui:option value="729"><liferay-ui:message key="1-month" /></aui:option>
					<aui:option value="2190"><liferay-ui:message key="3-months" /></aui:option>
					<aui:option value="4380"><liferay-ui:message key="6-months" /></aui:option>
					<aui:option value="8760"><liferay-ui:message key="1-year" /></aui:option>
					<aui:option value="26280"><liferay-ui:message key="3-years" /></aui:option>
					<aui:option value="43800"><liferay-ui:message key="5-years" /></aui:option>
				</aui:select>
			</aui:fieldset>
		</aui:col>
		<aui:col width="33">
			<aui:select helpMessage="filter-class-name-help" multiple="true" name="filterClassName" onChange='<%= renderResponse.getNamespace() + "disableReindexAndRemoveOrphansButtons(this);" %>' onClick='<%= renderResponse.getNamespace() + "disableReindexAndRemoveOrphansButtons(this);" %>' style="height: 240px; min-width: 120px; max-width: 340px;">

<%
				String selectedAllModels = "";
				if (filterClassNameSelected.isEmpty()) {
					selectedAllModels = "selected";
				}
%>

				<option <%= selectedAllModels %> value=""><liferay-ui:message key="all" /></option>
				<option disabled="true" value="-">--------</option>

<%
				for (Model model : modelList) {
					String className = model.getClassName();
					String displayName = model.getDisplayName(locale);
					if (Validator.isNull(displayName)) {
						displayName = className;
					}

					String selectedModel = "";
					if (filterClassNameSelected.contains(className)) {
						selectedModel = "selected";
					}
%>

					<option <%= selectedModel %> value="<%= className %>"><%= displayName %></option>

<%
				}
%>

			</aui:select>
		</aui:col>
		<aui:col width="33">
			<aui:select helpMessage="filter-group-id-help" multiple="true" name="filterGroupId" onChange='<%= renderResponse.getNamespace() + "disableReindexAndRemoveOrphansButtons(this);" %>' onClick='<%= renderResponse.getNamespace() + "disableReindexAndRemoveOrphansButtons(this);" %>' style="height: 240px; min-width: 120px; max-width: 340px;">

<%
String selectedNoFilter = "";
String selectedWithoutGroupId = "";
String selectedAllSites = "";
String selectedUserSites = "";
if (filterGroupIdSelected.isEmpty() || filterGroupIdSelected.contains("-1000")) {
	selectedNoFilter = "selected";
}
%>

				<option <%= selectedNoFilter %> value="-1000"><liferay-ui:message key="filter-group-id-no-filter" /></option>
				<option disabled="true" value="-">--------</option>

<%
				for (int i=0;i<groupIdList.size();i++) {
					String groupIdStr = "" + groupIdList.get(i);

					String selectedGroup = "";
					if (filterGroupIdSelected.contains(groupIdStr)) {
						selectedGroup = "selected";
					}
%>

					<option <%= selectedGroup %> value="<%= groupIdStr %>"><%= groupDescriptionList.get(i) %></option>

<%
				}
%>

			</aui:select>
		</aui:col>
	</aui:row>

	<aui:button-row style="margin-top: 0px;">
		<aui:button type="submit" value="check-staging" />

<%
	String exportCsvResourceURL = (String)request.getAttribute("exportCsvResourceURL");
	if (exportCsvResourceURL != null) {
		exportCsvResourceURL = "window.open('" + exportCsvResourceURL + "');";
%>

		<aui:button onClick="<%= exportCsvResourceURL %>" value="export-to-csv" />

<%
	}
%>

		<aui:button onClick="<%= viewURL %>" value="clean" />
		<aui:button onClick="<%= configurationURL %>" value="configuration" />

	</aui:button-row>
</aui:form>

<%
	if ((companyProcessTime != null) && (companyError != null)) {
%>

<h2><b><%= request.getAttribute("title") %></b></h2>

<%
		for (Entry<Company, Long> companyEntry : companyProcessTime.entrySet()) {
			Long processTime = companyEntry.getValue();
			%>

			<h3>Company: <%= companyEntry.getKey().getCompanyId() %> - <%= companyEntry.getKey().getWebId() %></h3>

			<%
			if (companyResultDataMap != null) {
				Map<Long, List<Comparison>> resultDataMap =
					companyResultDataMap.get(companyEntry.getKey());

				PortletURL serverURL = renderResponse.createRenderURL();

				SearchContainer searchContainer = StagingCheckerOutput.generateSearchContainer(portletConfig, renderRequest, true, resultDataMap, serverURL);

				if (searchContainer.getTotal() > 0) {
				%>

				<liferay-ui:search-iterator paginate="false" searchContainer="<%= searchContainer %>" />

				<%
				}
				else {
				%>

				<b>No results found:</b> your system is ok or perhaps you have to change some filters<br /><br />

				<%
				}
			}
			String errorMessage = companyError.get(companyEntry.getKey());
%>

<c:if test="<%= Validator.isNotNull(errorMessage) %>">
	<aui:input cssClass="lfr-textarea-container" name="output" resizable="<%= true %>" type="textarea" value="<%= errorMessage %>" />
</c:if>

<i>Executed <b><%= request.getAttribute("title") %></b> for company <%= companyEntry.getKey().getCompanyId() %> in <%=processTime %> ms</i><br />

<%
		}
%>

<%
	}
%>

</div></div></div>

<aui:script>
	Liferay.provide(window,'closePopupWindow', function(dialogId) {
			var dialog = Liferay.Util.Window.getById(dialogId);
			dialog.destroy();
		},
		['liferay-util-window']
	);
</aui:script>