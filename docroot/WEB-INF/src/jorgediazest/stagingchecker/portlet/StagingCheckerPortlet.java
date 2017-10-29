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

package jorgediazest.stagingchecker.portlet;

import com.liferay.portal.kernel.dao.orm.Conjunction;
import com.liferay.portal.kernel.dao.orm.Order;
import com.liferay.portal.kernel.dao.orm.OrderFactoryUtil;
import com.liferay.portal.kernel.dao.orm.Projection;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.deploy.DeployManagerUtil;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.Portlet;
import com.liferay.portal.kernel.plugin.PluginPackage;
import com.liferay.portal.kernel.portlet.LiferayPortletContext;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.service.ClassNameLocalServiceUtil;
import com.liferay.portal.kernel.service.GroupLocalServiceUtil;
import com.liferay.portal.kernel.util.JavaConstants;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.Validator;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.ResourceURL;

import jorgediazest.stagingchecker.ExecutionMode;
import jorgediazest.stagingchecker.model.StagingCheckerModelFactory;
import jorgediazest.stagingchecker.output.StagingCheckerOutput;
import jorgediazest.stagingchecker.util.ConfigurationUtil;
import jorgediazest.stagingchecker.util.RemoteConfigurationUtil;

import jorgediazest.util.data.Comparison;
import jorgediazest.util.data.Data;
import jorgediazest.util.model.Model;
import jorgediazest.util.model.ModelFactory;
import jorgediazest.util.model.ModelUtil;
import jorgediazest.util.output.OutputUtils;

/**
 * Portlet implementation class StagingCheckerPortlet
 *
 * @author Jorge Díaz
 */
public class StagingCheckerPortlet extends MVCPortlet {

	public static void dumpToLog(
			boolean groupBySite,
			Map<Long, List<Comparison>> comparisonDataMap)
		throws SystemException {

		if (!_log.isInfoEnabled()) {
			return;
		}

		for (
			Entry<Long, List<Comparison>> entry :
				comparisonDataMap.entrySet()) {

			String groupTitle = null;
			Group group = GroupLocalServiceUtil.fetchGroup(entry.getKey());

			if ((group == null) && groupBySite) {
				groupTitle = "N/A";
			}
			else if (group != null) {
				groupTitle = group.getGroupId() + " - " + group.getName();
			}

			if (groupTitle != null) {
				_log.info("");
				_log.info("---------------");
				_log.info("GROUP: " + groupTitle);
				_log.info("---------------");
			}

			for (Comparison comparison : entry.getValue()) {
				comparison.dumpToLog();
			}
		}
	}

	public static Map<Long, List<Comparison>> executeCheck(
		Company company, List<Long> groupIds, List<String> classNames,
		Set<ExecutionMode> executionMode, int threadsExecutor)
	throws Exception {

		StagingCheckerModelFactory mf = new StagingCheckerModelFactory();

		List<Model> modelList = new ArrayList<Model>();

		for (String className : classNames) {
			Model model = mf.getModelObject(className);

			if (model == null) {
				continue;
			}

			Set<Portlet> portlets = mf.getPortletSet(model.getClassName());

			if (model.isStagedModel() && model.isGroupedModel() &&
				!portlets.isEmpty()) {

				modelList.add(model);
			}
		}

		long companyId = company.getCompanyId();

		Map<String, Map<Long, List<Data>>> queryCache =
			new ConcurrentHashMap<String, Map<Long, List<Data>>>();

		ExecutorService executor = Executors.newFixedThreadPool(
			threadsExecutor);

		Map<Long, List<Future<Comparison>>> futureResultDataMap =
			new TreeMap<Long, List<Future<Comparison>>>();

		for (long groupId : groupIds) {
			List<Future<Comparison>> futureResultList =
				new ArrayList<Future<Comparison>>();

			for (Model model : modelList) {
				if (!isStagingActive(mf, model, groupId)) {
					continue;
				}

				CallableCheckGroupAndModel c =
					new CallableCheckGroupAndModel(
						queryCache, companyId, groupId, model, executionMode);

				futureResultList.add(executor.submit(c));
			}

			futureResultDataMap.put(groupId, futureResultList);
		}

		Map<Long, List<Comparison>> resultDataMap =
			new TreeMap<Long, List<Comparison>>();

		for (
			Entry<Long, List<Future<Comparison>>> entry :
				futureResultDataMap.entrySet()) {

			List<Comparison> resultList = new ArrayList<Comparison>();

			for (Future<Comparison> f : entry.getValue()) {
				Comparison results = f.get();

				if (results != null) {
					resultList.add(results);
				}
			}

			resultDataMap.put(entry.getKey(), resultList);
		}

		executor.shutdownNow();

		return resultDataMap;
	}

	public static EnumSet<ExecutionMode>
		getExecutionMode(ActionRequest request) {

		EnumSet<ExecutionMode> executionMode = EnumSet.noneOf(
			ExecutionMode.class);

		if (ParamUtil.getBoolean(request, "outputBothExact")) {
			executionMode.add(ExecutionMode.SHOW_BOTH_EXACT);
		}

		if (ParamUtil.getBoolean(request, "outputBothNotExact")) {
			executionMode.add(ExecutionMode.SHOW_BOTH_NOTEXACT);
		}

		if (ParamUtil.getBoolean(request, "outputStaging")) {
			executionMode.add(ExecutionMode.SHOW_STAGING);
		}

		if (ParamUtil.getBoolean(request, "outputLive")) {
			executionMode.add(ExecutionMode.SHOW_LIVE);
		}

		if (ParamUtil.getBoolean(request, "dumpAllObjectsToLog")) {
			executionMode.add(ExecutionMode.DUMP_ALL_OBJECTS_TO_LOG);
		}

		return executionMode;
	}

	public static Log getLogger() {
		return _log;
	}

	public static PluginPackage getPluginPackage(PortletConfig portletConfig) {
		PortletContext portletContext = portletConfig.getPortletContext();

		String portletContextName = portletContext.getPortletContextName();

		return DeployManagerUtil.getInstalledPluginPackage(portletContextName);
	}

	public static boolean isStagingActive(
			StagingCheckerModelFactory mf, Model model, long groupId)
		throws SystemException {

		Group group = GroupLocalServiceUtil.fetchGroup(groupId);

		Set<Portlet> portlets = mf.getPortletSet(model.getClassName());

		if (portlets.isEmpty()) {
			return false;
		}

		Portlet portlet = portlets.toArray(new Portlet[1])[0];

		if (!group.isStagedPortlet(portlet.getPortletId())) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					model.getName() + " is not staged for group " +
						groupId);
			}

			return false;
		}

		return true;
	}

	public void doView(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		PortletConfig portletConfig =
			(PortletConfig)renderRequest.getAttribute(
				JavaConstants.JAVAX_PORTLET_CONFIG);

		String updateMessage = getUpdateMessage(portletConfig);

		renderRequest.setAttribute("updateMessage", updateMessage);

		List<String> outputList = StagingCheckerOutput.generateCSVOutput(
			portletConfig, renderRequest);

		String portletId = portletConfig.getPortletName();
		long userId = PortalUtil.getUserId(renderRequest);
		String outputContent = OutputUtils.listStringToString(outputList);

		FileEntry exportCsvFileEntry = OutputUtils.addPortletOutputFileEntry(
			portletId, userId, outputContent);

		if (exportCsvFileEntry != null) {
			ResourceURL exportCsvResourceURL =
				renderResponse.createResourceURL();
			exportCsvResourceURL.setResourceID(exportCsvFileEntry.getTitle());

			renderRequest.setAttribute(
				"exportCsvResourceURL", exportCsvResourceURL.toString());
		}

		try {
			List<Long> siteGroupIds = this.getSiteGroupIds();
			renderRequest.setAttribute("groupIdList", siteGroupIds);

			List<String> groupDescriptionList = getSiteGroupDescriptions(
				siteGroupIds);
			renderRequest.setAttribute(
				"groupDescriptionList", groupDescriptionList);
		}
		catch (SystemException se) {
			throw new PortletException(se);
		}

		try {
			List<Model> modelList = this.getModelList();
			renderRequest.setAttribute("modelList", modelList);
		}
		catch (SystemException se) {
			throw new PortletException(se);
		}

		int numberOfThreads = getNumberOfThreads(renderRequest);
		renderRequest.setAttribute("numberOfThreads", numberOfThreads);

		super.doView(renderRequest, renderResponse);
	}

	public void executeCheck(ActionRequest request, ActionResponse response)
		throws Exception {

		PortalUtil.copyRequestParameters(request, response);

		EnumSet<ExecutionMode> executionMode = getExecutionMode(request);

		String[] filterClassNameArr = ParamUtil.getParameterValues(
			request,"filterClassName");

		response.setRenderParameter("filterClassName", new String[0]);

		request.setAttribute(
			"filterClassNameSelected", SetUtil.fromArray(filterClassNameArr));

		String[] filterGroupIdArr = ParamUtil.getParameterValues(
			request,"filterGroupId");

		response.setRenderParameter("filterGroupId", new String[0]);

		request.setAttribute(
			"filterGroupIdSelected", SetUtil.fromArray(filterGroupIdArr));

		Map<Company, Map<Long, List<Comparison>>> companyResultDataMap =
			new LinkedHashMap<Company, Map<Long, List<Comparison>>>();

		Map<Company, Long> companyProcessTime =
			new LinkedHashMap<Company, Long>();

		Map<Company, String> companyError =
			new LinkedHashMap<Company, String>();

		for (Company company : getCompanyList()) {
			try {
				CompanyThreadLocal.setCompanyId(company.getCompanyId());

				List<String> classNames = getClassNames(filterClassNameArr);

				List<Long> groupIds = getGroupIds(company, filterGroupIdArr);

				long startTime = System.currentTimeMillis();

				Map<Long, List<Comparison>> resultDataMap =
					StagingCheckerPortlet.executeCheck(
						company, groupIds, classNames, executionMode,
						getNumberOfThreads(request));

				long endTime = System.currentTimeMillis();

				if (_log.isInfoEnabled() &&
					executionMode.contains(
							ExecutionMode.DUMP_ALL_OBJECTS_TO_LOG)) {

					_log.info("COMPANY: " + company);

					dumpToLog(true, resultDataMap);
				}

				companyResultDataMap.put(company, resultDataMap);

				companyProcessTime.put(company, (endTime - startTime));
			}
			catch (Throwable t) {
				StringWriter swt = new StringWriter();
				PrintWriter pwt = new PrintWriter(swt);
				pwt.println("Error during execution: " + t.getMessage());
				t.printStackTrace(pwt);
				companyError.put(company, swt.toString());
				_log.error(t, t);
			}
		}

		request.setAttribute("title", "Check Staging");
		request.setAttribute("executionMode", executionMode);
		request.setAttribute("companyProcessTime", companyProcessTime);
		request.setAttribute("companyResultDataMap", companyResultDataMap);
		request.setAttribute("companyError", companyError);
	}

	public List<String> getClassNames() throws SystemException {
		return getClassNames(null);
	}

	public List<String> getClassNames(String[] filterClassNameArr)
		throws SystemException {

		if ((filterClassNameArr == null)||(filterClassNameArr.length == 0)||
			((filterClassNameArr.length == 1) &&
			 Validator.isNull(filterClassNameArr[0]))) {

			filterClassNameArr = null;
		}

		List<String> allClassName =
			ModelUtil.getClassNameValues(
				ClassNameLocalServiceUtil.getClassNames(
					QueryUtil.ALL_POS, QueryUtil.ALL_POS));

		List<String> classNames = new ArrayList<String>();

		for (String className : allClassName) {
			if (ConfigurationUtil.ignoreClassName(className)) {
				continue;
			}

			if (filterClassNameArr == null) {
				classNames.add(className);
				continue;
			}

			for (String filterClassName : filterClassNameArr) {
				if (className.equals(filterClassName)) {
					classNames.add(className);
					break;
				}
			}
		}

		return classNames;
	}

	@SuppressWarnings("unchecked")
	public List<Company> getCompanyList() throws Exception {
		ModelFactory modelFactory = new ModelFactory();

		Model companyModel = modelFactory.getModelObject(Company.class);

		return (List<Company>)
			companyModel.executeDynamicQuery(
				null, OrderFactoryUtil.asc("companyId"));
	}

	public List<Long> getGroupIds(Company company, String[] filterGroupIdArr)
		throws SystemException {

		if ((filterGroupIdArr != null) && (filterGroupIdArr.length == 1) &&
			filterGroupIdArr[0].equals("-1000")) {

			filterGroupIdArr = null;
		}

		List<Group> groups =
			GroupLocalServiceUtil.getCompanyGroups(
				company.getCompanyId(), QueryUtil.ALL_POS, QueryUtil.ALL_POS);

		List<Long> groupIds = new ArrayList<Long>();

		for (Group group : groups) {
			if (!group.hasStagingGroup()) {
				continue;
			}

			if (filterGroupIdArr == null) {
				groupIds.add(group.getGroupId());
				continue;
			}

			String groupIdStr = "" + group.getGroupId();

			for (int i = 0; i < filterGroupIdArr.length; i++) {
				if (groupIdStr.equals(filterGroupIdArr[i])) {
					groupIds.add(group.getGroupId());
					break;
				}
			}
		}

		return groupIds;
	}

	public List<Model> getModelList() throws SystemException {
		return getModelList(null);
	}

	public List<Model> getModelList(String[] filterClassNameArr)
		throws SystemException {

		List<String> classNames = getClassNames(filterClassNameArr);

		ModelFactory modelFactory = new StagingCheckerModelFactory();

		List<Model> modelList = new ArrayList<Model>();

		for (String className : classNames) {
			Model model = modelFactory.getModelObject(className);

			if (model == null) {
				continue;
			}

			if (!model.isStagedModel() || !model.isGroupedModel()) {
				continue;
			}

			StagingCheckerModelFactory mf =
				(StagingCheckerModelFactory)model.getModelFactory();

			Set<Portlet> portlets = mf.getPortletSet(model.getClassName());

			if (!portlets.isEmpty()) {
				modelList.add(model);
			}
		}

		return modelList;
	}

	public int getNumberOfThreads(ActionRequest actionRequest) {
		int def = ConfigurationUtil.getDefaultNumberThreads();

		int num = ParamUtil.getInteger(actionRequest, "numberOfThreads", def);

		return (num == 0) ? def : num;
	}

	public int getNumberOfThreads(RenderRequest renderRequest) {
		int def = ConfigurationUtil.getDefaultNumberThreads();

		int num = ParamUtil.getInteger(renderRequest, "numberOfThreads", def);

		return (num == 0) ? def : num;
	}

	public List<String> getSiteGroupDescriptions(List<Long> siteGroupIds)
		throws SystemException {

		List<String> groupDescriptionList = new ArrayList<String>();

		for (Long siteGroupId : siteGroupIds) {
			Group group = GroupLocalServiceUtil.fetchGroup(siteGroupId);
			String groupDescription = group.getName();
			groupDescription = groupDescription.replace(
				"LFR_ORGANIZATION", "(Org)");

			if (group.isCompany()) {
				if (!group.isStagingGroup()) {
					groupDescription = "Global";
				}

				groupDescription += " - " + group.getCompanyId();
			}

			groupDescriptionList.add(groupDescription);
		}

		return groupDescriptionList;
	}

	@SuppressWarnings("unchecked")
	public List<Long> getSiteGroupIds() {

		ModelFactory modelFactory = new ModelFactory();

		Model model = modelFactory.getModelObject(Group.class);

		Conjunction stagingSites = RestrictionsFactoryUtil.conjunction();
		stagingSites.add(model.getProperty("site").eq(false));
		stagingSites.add(model.getProperty("liveGroupId").ne(0L));

		Projection projection = model.getPropertyProjection("liveGroupId");

		List<Order> orders = Collections.singletonList(
			OrderFactoryUtil.asc("name"));

		try {
			return (List<Long>)model.executeDynamicQuery(
				stagingSites, projection, orders);
		}
		catch (Exception e) {
			if (_log.isWarnEnabled()) {
				_log.warn(e, e);
			}

			return new ArrayList<Long>();
		}
	}

	public String getUpdateMessage(PortletConfig portletConfig) {

		PluginPackage pluginPackage = getPluginPackage(portletConfig);

		if (pluginPackage == null) {
			return getUpdateMessageOffline(portletConfig);
		}

		@SuppressWarnings("unchecked")
		Collection<String> lastAvalibleVersion =
			(Collection<String>)RemoteConfigurationUtil.getConfigurationEntry(
				"lastAvalibleVersion");

		if ((lastAvalibleVersion == null) || lastAvalibleVersion.isEmpty()) {
			return getUpdateMessageOffline(portletConfig);
		}

		String portletVersion = pluginPackage.getVersion();

		if (lastAvalibleVersion.contains(portletVersion)) {
			return null;
		}

		return (String)RemoteConfigurationUtil.getConfigurationEntry(
				"updateMessage");
	}

	public String getUpdateMessageOffline(PortletConfig portletConfig) {
		LiferayPortletContext context =
			(LiferayPortletContext)portletConfig.getPortletContext();

		long installationTimestamp = context.getPortlet().getTimestamp();

		if (installationTimestamp == 0L) {
			return null;
		}

		long offlineUpdateTimeoutMilis =
			(Long)ConfigurationUtil.getConfigurationEntry(
				"offlineUpdateTimeoutMilis");

		long offlineUpdateTimestamp =
			(installationTimestamp + offlineUpdateTimeoutMilis);

		long currentTimeMillis = System.currentTimeMillis();

		if (offlineUpdateTimestamp > currentTimeMillis) {
			return null;
		}

		return (String)ConfigurationUtil.getConfigurationEntry(
				"offlineUpdateMessage");
	}

	public void serveResource(
			ResourceRequest request, ResourceResponse response)
		throws IOException, PortletException {

		PortletConfig portletConfig =
			(PortletConfig)request.getAttribute(
				JavaConstants.JAVAX_PORTLET_CONFIG);

		String resourceId = request.getResourceID();
		String portletId = portletConfig.getPortletName();

		OutputUtils.servePortletFileEntry(portletId, resourceId, response);
	}

	private static Log _log = LogFactoryUtil.getLog(
		StagingCheckerPortlet.class);

}