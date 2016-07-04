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
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.OrderFactoryUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.exportimport.kernel.lar.StagedModelDataHandler;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.JavaConstants;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.service.ClassNameLocalServiceUtil;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.service.GroupLocalServiceUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.util.portlet.PortletProps;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.ResourceURL;

import jorgediazest.stagingchecker.ExecutionMode;
import jorgediazest.stagingchecker.data.DataModelUUIDComparator;
import jorgediazest.stagingchecker.model.StagingCheckerModelFactory;
import jorgediazest.stagingchecker.model.StagingCheckerModelQueryFactory;
import jorgediazest.stagingchecker.output.StagingCheckerOutput;

import jorgediazest.util.data.Comparison;
import jorgediazest.util.data.DataComparator;
import jorgediazest.util.model.Model;
import jorgediazest.util.model.ModelFactory;
import jorgediazest.util.model.ModelUtil;
import jorgediazest.util.modelquery.ModelQuery;
import jorgediazest.util.modelquery.ModelQueryFactory;
import jorgediazest.util.modelquery.ModelQueryFactory.DataComparatorFactory;
import jorgediazest.util.output.OutputUtils;
import jorgediazest.util.service.Service;

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

		ModelFactory modelFactory = new StagingCheckerModelFactory();

		ModelQueryFactory queryFactory = new StagingCheckerModelQueryFactory(
			modelFactory);

		DataComparatorFactory dataComparatorFactory =
			new DataComparatorFactory() {

			protected DataComparator defaultComparator =
				new DataModelUUIDComparator(new String[] {

				"createDate", "status", "version", "name", "title",
				"description", "size", "AssetTag.uuid", "AssetCategory.uuid",
				"com.liferay.portal.model.ResourcePermission" });

			protected DataComparator noCreateDateComparator =
				new DataModelUUIDComparator(new String[] {

				"status", "version", "name", "title", "description", "size",
				"AssetTag.uuid", "AssetCategory.uuid",
				"com.liferay.portal.model.ResourcePermission" });

			protected DataComparator noNameComparator =
				new DataModelUUIDComparator(new String[] {

				"createDate", "status", "version", "title", "description",
				"size", "AssetTag.uuid", "AssetCategory.uuid",
				"com.liferay.portal.model.ResourcePermission" });

			@Override
			public DataComparator getDataComparator(ModelQuery query) {
				Model model = query.getModel();

				if ("com.liferay.asset.kernel.model.AssetCategory".equals(
						model.getClassName()) ||
					"com.liferay.asset.kernel.model.AssetVocabulary".equals(
						model.getClassName()) ||
					"com.liferay.journal.model.JournalArticle".equals(
						model.getClassName())) {

					return noCreateDateComparator;
				}

				final String strDLFileEntry =
					"com.liferay.document.library.kernel.model.DLFileEntry";

				if (strDLFileEntry.equals(model.getClassName())) {
					return noNameComparator;
				}

				return defaultComparator;
			}

		};

		queryFactory.setDataComparatorFactory(dataComparatorFactory);

		List<ModelQuery> modelQueryList = new ArrayList<ModelQuery>();

		for (String className : classNames) {
			ModelQuery modelQuery = queryFactory.getModelQueryObject(className);

			if (modelQuery == null) {
				continue;
			}

			Model model = modelQuery.getModel();

			if (model.isStagedModel() && model.isGroupedModel() &&
				(model.getPortlet() != null)) {

				modelQueryList.add(modelQuery);
			}
		}

		long companyId = company.getCompanyId();

		ExecutorService executor = Executors.newFixedThreadPool(
			threadsExecutor);

		Map<Long, List<Future<Comparison>>> futureResultDataMap =
			new TreeMap<Long, List<Future<Comparison>>>();

		for (long groupId : groupIds) {
			List<Future<Comparison>> futureResultList =
				new ArrayList<Future<Comparison>>();

			for (ModelQuery modelQuery : modelQueryList) {
				CallableCheckGroupAndModel c =
					new CallableCheckGroupAndModel(
						companyId, groupId, modelQuery, executionMode);

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

	public void doView(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		PortletConfig portletConfig =
			(PortletConfig)renderRequest.getAttribute(
				JavaConstants.JAVAX_PORTLET_CONFIG);

		List<String> outputList = StagingCheckerOutput.generateCSVOutput(
			portletConfig, renderRequest);

		String outputScript = OutputUtils.listStringToString(outputList);

		FileEntry exportCsvFileEntry = null;

		try {
			InputStream inputStream = null;

			if (Validator.isNotNull(outputScript)) {
				inputStream = new ByteArrayInputStream(
					outputScript.getBytes(StringPool.UTF8));
			}

			String portletId = portletConfig.getPortletName();

			Repository repository = OutputUtils.getPortletRepository(portletId);

			OutputUtils.cleanupPortletFileEntries(repository, 8 * 60);

			long userId = PortalUtil.getUserId(renderRequest);

			String fileName =
				"staging-checker_output_" + userId + "_" +
				System.currentTimeMillis() + ".csv";

			exportCsvFileEntry = OutputUtils.addPortletFileEntry(
				repository, inputStream, userId, fileName, "text/plain");

			if (exportCsvFileEntry != null) {
				ResourceURL exportCsvResourceURL =
					renderResponse.createResourceURL();
				exportCsvResourceURL.setResourceID(
					exportCsvFileEntry.getTitle());
				renderRequest.setAttribute(
					"exportCsvResourceURL", exportCsvResourceURL.toString());
			}
		}
		catch (Exception e) {
			_log.error(e, e);
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
			if (ignoreClassName(className)) {
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

		Service companyService = companyModel.getService();

		DynamicQuery companyDynamicQuery = companyService.newDynamicQuery();

		companyDynamicQuery.addOrder(OrderFactoryUtil.asc("companyId"));

		return (List<Company>)
			companyService.executeDynamicQuery(companyDynamicQuery);
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

			if ((model == null) || !model.isStagedModel() ||
				!model.isGroupedModel() || (model.getPortlet() == null)) {

				continue;
			}

			modelList.add(model);
		}

		return modelList;
	}

	public List<ModelQuery> getModelQueryList(
		ModelQueryFactory mqFactory, List<String> classNames) {

		List<ModelQuery> mqList = new ArrayList<ModelQuery>();

		for (String className : classNames) {
			ModelQuery mq = mqFactory.getModelQueryObject(className);

			if (mq != null) {
				mqList.add(mq);
			}
		}

		return mqList;
	}

	public int getNumberOfThreads(ActionRequest actionRequest) {
		int def = GetterUtil.getInteger(PortletProps.get("number.threads"),1);

		int num = ParamUtil.getInteger(actionRequest, "numberOfThreads", def);

		return (num == 0) ? def : num;
	}

	public int getNumberOfThreads(RenderRequest renderRequest) {
		int def = GetterUtil.getInteger(PortletProps.get("number.threads"), 1);

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

		DynamicQuery groupDynamicQuery = model.getService().newDynamicQuery();

		Conjunction stagingSites = RestrictionsFactoryUtil.conjunction();
		stagingSites.add(model.getProperty("site").eq(false));
		stagingSites.add(model.getProperty("liveGroupId").ne(0L));

		groupDynamicQuery.add(stagingSites);
		groupDynamicQuery.setProjection(
			model.getPropertyProjection("liveGroupId"));

		groupDynamicQuery.addOrder(OrderFactoryUtil.asc("name"));

		try {
			return (List<Long>)model.getService().executeDynamicQuery(
				groupDynamicQuery);
		}
		catch (Exception e) {
			if (_log.isWarnEnabled()) {
				_log.warn(e, e);
			}

			return new ArrayList<Long>();
		}
	}

	public boolean ignoreClassName(String className) {
		if (Validator.isNull(className)) {
			return true;
		}

		for (String ignoreClassName : ignoreClassNames) {
			if (ignoreClassName.equals(className)) {
				return true;
			}
		}

		return false;
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

	private static String[] ignoreClassNames = new String[] {
		"com.liferay.portal.repository.liferayrepository.model.LiferayFileEntry",
		"com.liferay.portal.kernel.repository.model.FileEntry",
		"com.liferay.portal.kernel.repository.model.Folder",
		"com.liferay.portal.model.UserPersonalSite"};

}