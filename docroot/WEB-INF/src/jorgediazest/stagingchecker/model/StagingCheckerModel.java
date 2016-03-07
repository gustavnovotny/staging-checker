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

package jorgediazest.stagingchecker.model;

import com.liferay.portal.kernel.bean.ClassLoaderBeanHandler;
import com.liferay.portal.kernel.dao.orm.Criterion;
import com.liferay.portal.kernel.lar.StagedModelDataHandler;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Portlet;
import com.liferay.portal.service.PortletLocalServiceUtil;

import java.lang.reflect.Proxy;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jorgediazest.util.data.Data;
import jorgediazest.util.model.Model;
import jorgediazest.util.model.ModelImpl;
import jorgediazest.util.model.ModelUtil;
import jorgediazest.util.service.Service;

/**
 * @author Jorge Díaz
 */
public class StagingCheckerModel extends ModelImpl {

	public static String[] auditedModelAttributes =
		new String[] { "companyId", "createDate"};
	public static String[] groupedModelAttributes = new String[] { };
	public static String[] resourcedModelAttributes =
		new String[] { "resourcePrimKey" };
	public static String[] stagedModelAttributes =
		new String[] { "uuid", "companyId", "createDate"};
	public static String[] workflowModelAttributes = new String[] { "status" };

	public Set<String> calculateAttributesToCheck() {
		Set<String> attributesToCheckAux = new LinkedHashSet<String>();

		attributesToCheckAux.add(this.getPrimaryKeyAttribute());
		attributesToCheckAux.add("companyId");

		if (this.isAuditedModel()) {
			attributesToCheckAux.addAll(Arrays.asList(auditedModelAttributes));
		}

		if (this.isGroupedModel()) {
			attributesToCheckAux.addAll(Arrays.asList(groupedModelAttributes));
		}

		if (this.isResourcedModel()) {
			attributesToCheckAux.addAll(
				Arrays.asList(resourcedModelAttributes));
		}

		if (this.isStagedModel()) {
			attributesToCheckAux.addAll(Arrays.asList(stagedModelAttributes));
		}

		if (this.isWorkflowEnabled()) {
			attributesToCheckAux.addAll(Arrays.asList(workflowModelAttributes));
		}

		attributesToCheckAux.addAll(Arrays.asList(this.getExactAttributes()));

		Set<String> attributesToCheck = new LinkedHashSet<String>();

		for (String attr : attributesToCheckAux) {
			if (this.hasAttribute(attr)) {
				attributesToCheck.add(attr);
			}
		}

		return attributesToCheck;
	}

	@Override
	public Model clone() {
		StagingCheckerModel model;
		try {
			model = (StagingCheckerModel)super.clone();
			model.attributesToCheck = new LinkedHashSet<String>(
				this.attributesToCheck);
		}
		catch (Exception e) {
			_log.error("Error executing clone");
			throw new RuntimeException(e);
		}

		return model;
	}

	public int compareTo(Data data1, Data data2) {

		if (Validator.isNotNull(data1.getUuid())) {
			return data1.getUuid().compareTo(data2.getUuid());
		}
		else if (Validator.isNotNull(data2.getUuid())) {
			return -1 * compareTo(data2, data1);
		}
		else {
			return 0;
		}
	}

	public boolean equals(Data data1, Data data2) {
		return Validator.equals(data1.getUuid(), data2.getUuid());
	}

	public Criterion generateQueryFilter() {
		Criterion classNameIdFilter = null;

		if (this.hasAttribute("classNameId")) {
			classNameIdFilter = this.generateCriterionFilter("classNameId=0");
		}

		Criterion workflowFilter = null;

		if (this.isWorkflowEnabled() && (getStagedModelDataHandler() != null)) {
			workflowFilter = this.getProperty("status").in(
				this.getStagedModelDataHandler().getExportableStatuses());
		}

		return ModelUtil.generateConjunctionQueryFilter(
			classNameIdFilter, workflowFilter);
	}

	public final Set<String> getAttributesToCheck() {
		if (attributesToCheck == null) {
			attributesToCheck = calculateAttributesToCheck();
		}

		return attributesToCheck;
	}

	public String getPortletId() {

		StagedModelDataHandler<?> stagedModelDataHandler =
			this.getStagedModelDataHandler();

		if (stagedModelDataHandler instanceof Proxy) {
			try {
				ClassLoaderBeanHandler classLoaderBeanHandler =
					(ClassLoaderBeanHandler)
						Proxy.getInvocationHandler(stagedModelDataHandler);
				stagedModelDataHandler =
					(StagedModelDataHandler<?>)classLoaderBeanHandler.getBean();
			}
			catch (Exception e) {
				if (_log.isDebugEnabled()) {
					_log.debug(e, e);
				}
			}
		}

		if (stagedModelDataHandler == null) {
			return null;
		}

		if (!stagedHandlerPortletIdMap.containsKey(
				stagedModelDataHandler.getClass().getName())) {

			fillStagingHandlerPortletIdMap();
		}

		return stagedHandlerPortletIdMap.get(
			stagedModelDataHandler.getClass().getName());
	}

	public Integer hashCode(Data data) {
		if (Validator.isNotNull(data.getUuid())) {
			return data.getUuid().hashCode();
		}

		return null;
	}

	@Override
	public void init(
			String classPackageName, String classSimpleName, Service service)
		throws Exception {

		super.init(classPackageName, classSimpleName, service);

		this.setFilter(this.generateQueryFilter());
	}

	protected static void fillStagingHandlerPortletIdMap() {
		for (Portlet portlet : PortletLocalServiceUtil.getPortlets()) {
			List<String> stagingHandlerList =
				portlet.getStagedModelDataHandlerClasses();

			for (String stagingHandler : stagingHandlerList) {
				if (!stagedHandlerPortletIdMap.containsKey(stagingHandler)) {
					stagedHandlerPortletIdMap.put(
						stagingHandler, portlet.getPortletId());

					if (_log.isDebugEnabled()) {
						_log.debug(
							"Adding: " + stagingHandler + " portlet " +
							portlet.getPortletId());
					}
				}
			}
		}
	}

	protected Set<String> attributesToCheck = null;

	private static Log _log = LogFactoryUtil.getLog(StagingCheckerModel.class);

	private static
		ConcurrentHashMap<String, String> stagedHandlerPortletIdMap =
			new ConcurrentHashMap<String, String>();

}