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

import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.dao.shard.ShardUtil;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.lar.StagedModelDataHandler;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.Group;
import com.liferay.portal.security.auth.CompanyThreadLocal;
import com.liferay.portal.service.ClassNameLocalServiceUtil;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;
import com.liferay.util.bridges.mvc.MVCPortlet;
import com.liferay.util.portlet.PortletProps;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import jorgediazest.stagingchecker.ExecutionMode;
import jorgediazest.stagingchecker.data.DataModelUUIDComparator;
import jorgediazest.stagingchecker.model.JournalArticle;

import jorgediazest.util.data.Comparison;
import jorgediazest.util.data.ComparisonUtil;
import jorgediazest.util.data.DataComparator;
import jorgediazest.util.model.DefaultModel;
import jorgediazest.util.model.Model;
import jorgediazest.util.model.ModelFactory;
import jorgediazest.util.model.ModelFactory.DataComparatorFactory;
import jorgediazest.util.model.ModelUtil;

/**
 * Portlet implementation class StagingCheckerPortlet
 *
 * @author Jorge Díaz
 */
public class StagingCheckerPortlet extends MVCPortlet {

	public static Map<Long, List<Comparison>> executeCheck(
		Company company, List<Long> groupIds, List<String> classNames,
		Set<ExecutionMode> executionMode, int threadsExecutor)
	throws ExecutionException, InterruptedException {

		Map<String, Class<? extends Model>> modelClassMap =
			new HashMap<String, Class<? extends Model>>();

		modelClassMap.put(
			"com.liferay.portlet.journal.model.JournalArticle",
			JournalArticle.class);

		ModelFactory modelFactory = new ModelFactory(
			DefaultModel.class, modelClassMap);

		DataComparatorFactory dataComparatorFactory =
			new DataComparatorFactory() {

			protected DataComparator defaultComparator =
				new DataModelUUIDComparator(new String[] {

				"createDate", "status", "version", "name", "title",
				"description", "size", "AssetTag.name", "AssetCategory.uuid",
				"com.liferay.portal.model.ResourcePermission" });

			protected DataComparator noCreateDateComparator =
				new DataModelUUIDComparator(new String[] {

				"status", "version", "name", "title", "description", "size",
				"AssetTag.name", "AssetCategory.uuid",
				"com.liferay.portal.model.ResourcePermission" });

			protected DataComparator noNameComparator =
				new DataModelUUIDComparator(new String[] {

				"createDate", "status", "version", "title", "description",
				"size", "AssetTag.name", "AssetCategory.uuid",
				"com.liferay.portal.model.ResourcePermission" });

			@Override
			public DataComparator getDataComparator(Model model) {
				if ("com.liferay.portlet.asset.model.AssetCategory".equals(
						model.getClassName()) ||
					"com.liferay.portlet.asset.model.AssetVocabulary".equals(
							model.getClassName()) ||
					"com.liferay.portlet.journal.model.JournalArticle".equals(
						model.getClassName())) {

					return noCreateDateComparator;
				}

				final String strDLFileEntry =
					"com.liferay.portlet.documentlibrary.model.DLFileEntry";

				if (strDLFileEntry.equals(model.getClassName())) {
					return noNameComparator;
				}

				return defaultComparator;
			}

		};

		modelFactory.setDataComparatorFactory(dataComparatorFactory);

		Map<String, Model> modelMap = modelFactory.getModelMap(classNames);

		List<Model> modelList = new ArrayList<Model>();

		for (Model model : modelMap.values()) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					model + " - isStagedModel: " + model.isStagedModel() +
						" - isGroupedModel: " + model.isGroupedModel() +
							" - portlets: " + model.getPortlets());
			}

			if (model.isStagedModel() && model.isGroupedModel() &&
				(model.getPortlet() != null)) {

				if (model.hasAttribute("classNameId")) {
					model.addFilter(
						model.generateCriterionFilter("classNameId=0"));
				}

				StagedModelDataHandler<?> stagedModelDataHandler =
					model.getStagedModelDataHandler();

				if ((stagedModelDataHandler != null) &&
					model.isWorkflowEnabled()) {

					model.addFilter(
						model.getProperty("status").in(
							stagedModelDataHandler.getExportableStatuses()));
				}

				if (model.getClassName().startsWith(
						"com.liferay.portlet.documentlibrary.model.") &&
					model.hasAttribute("repositoryId")) {

					model.addFilter(
						model.generateSingleCriterion("groupId=repositoryId"));
				}
				else if (model.getClassName().startsWith(
							"com.liferay.portlet.dynamicdatamapping.model.")) {

					model.setFilter(null);
				}

				modelList.add(model);
			}
		}

		long companyId = company.getCompanyId();

		ExecutorService executor = Executors.newFixedThreadPool(
			threadsExecutor);

		Map<Long, List<Future<Comparison>>> futureResultDataMap =
			new LinkedHashMap<Long, List<Future<Comparison>>>();

		for (long groupId : groupIds) {
			List<Future<Comparison>> futureResultList =
				new ArrayList<Future<Comparison>>();

			for (Model model : modelList) {
				CallableCheckGroupAndModel c =
					new CallableCheckGroupAndModel(
						companyId, groupId, model, executionMode);

				futureResultList.add(executor.submit(c));
			}

			futureResultDataMap.put(groupId, futureResultList);
		}

		Map<Long, List<Comparison>> resultDataMap =
			new LinkedHashMap<Long, List<Comparison>>();

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

		int numberOfThreads = getNumberOfThreads(renderRequest);
		renderRequest.setAttribute("numberOfThreads", numberOfThreads);

		super.doView(renderRequest, renderResponse);
	}

	public void executeCheck(ActionRequest request, ActionResponse response)
		throws Exception {

		PortalUtil.copyRequestParameters(request, response);

		EnumSet<ExecutionMode> executionMode = getExecutionMode(request);

		String[] filterClassNameArr = null;
		String filterClassName = ParamUtil.getString(
			request, "filterClassName");

		if (Validator.isNotNull(filterClassName)) {
			filterClassNameArr = filterClassName.split(",");
		}

		String[] filterGroupIdArr = null;
		String filterGroupId = ParamUtil.getString(request, "filterGroupId");

		if (Validator.isNotNull(filterGroupId)) {
			filterGroupIdArr = filterGroupId.split(",");
		}

		List<Company> companies = CompanyLocalServiceUtil.getCompanies();

		Map<Company, Map<Long, List<Comparison>>> companyResultDataMap =
			new HashMap<Company, Map<Long, List<Comparison>>>();

		Map<Company, Long> companyProcessTime = new HashMap<Company, Long>();

		Map<Company, String> companyError = new HashMap<Company, String>();

		for (Company company : companies) {
			try {
				CompanyThreadLocal.setCompanyId(company.getCompanyId());

				ShardUtil.pushCompanyService(company.getCompanyId());

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

					ComparisonUtil.dumpToLog(true, resultDataMap);
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
			finally {
				ShardUtil.popCompanyService();
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
				if (className.contains(filterClassName)) {
					classNames.add(className);
					break;
				}
			}
		}

		return classNames;
	}

	public List<Long> getGroupIds(Company company, String[] filterGroupIdArr)
		throws SystemException {

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

	private static Log _log = LogFactoryUtil.getLog(
		StagingCheckerPortlet.class);

	private static String[] ignoreClassNames = new String[] {
		"com.liferay.portal.kernel.repository.model.FileEntry",
		"com.liferay.portal.kernel.repository.model.Folder",
		"com.liferay.portal.model.UserPersonalSite"};

}