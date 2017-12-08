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
import com.liferay.portal.kernel.dao.orm.Property;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.lar.StagedModelDataHandler;
import com.liferay.portal.kernel.lar.StagedModelDataHandlerRegistryUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Portlet;
import com.liferay.portal.service.PortletLocalServiceUtil;

import java.lang.reflect.Proxy;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jorgediazest.stagingchecker.util.ConfigurationUtil;

import jorgediazest.util.model.Model;
import jorgediazest.util.model.ModelFactory;
import jorgediazest.util.model.ModelUtil;
import jorgediazest.util.model.ModelWrapper;

/**
 * @author Jorge Díaz
 */
public class StagingCheckerModelFactory extends ModelFactory {

	public StagingCheckerModelFactory() {
		fillHandlerPortletIdMap();

		this.companyId = 0L;
	}

	public StagingCheckerModelFactory(
		long companyId, Date startModifiedDate, Date endModifiedDate) {

		fillHandlerPortletIdMap();

		this.companyId = companyId;
		this.startModifiedDate = startModifiedDate;
		this.endModifiedDate = endModifiedDate;
	}

	@Override
	public Model getModelObject(String className) {
		Model model = super.getModelObject(className);

		if (model == null) {
			return null;
		}

		if (model instanceof ModelWrapper) {
			return model;
		}

		Set<Portlet> portlets = getPortlets(model.getClassName());

		if (_log.isDebugEnabled()) {
			_log.debug(
				model + " - isStagedModel: " + model.isStagedModel() +
					" - isGroupedModel: " + model.isGroupedModel() +
						" - portlets: " + portlets);
		}

		Criterion stagedModelCriterion = null;

		if (model.isStagedModel() && model.isGroupedModel() &&
			model.isWorkflowEnabled() && !portlets.isEmpty()) {

			StagedModelDataHandler<?> stagedModelDataHandler =
				StagedModelDataHandlerRegistryUtil.getStagedModelDataHandler(
					className);

			stagedModelCriterion = model.getProperty("status").in(
				stagedModelDataHandler.getExportableStatuses());
		}

		List<String> keyAttributes = ConfigurationUtil.getKeyAttributes(model);

		String sqlFilter = ConfigurationUtil.getStringFilter(model);

		Criterion sqlCriterion = ModelUtil.generateSQLCriterion(sqlFilter);

		Criterion companyCriterion = null;

		if (companyId > 0) {
			companyCriterion = model.getAttributeCriterion(
				"companyId", companyId);
		}

		Criterion criterion = ModelUtil.generateConjunctionCriterion(
			companyCriterion, stagedModelCriterion, sqlCriterion);

		if (startModifiedDate != null) {
			Criterion startDateCriterion = getAttributeRangeCriterion(
				model, "modifiedDate", startModifiedDate, true);

			criterion = ModelUtil.generateConjunctionCriterion(
				startDateCriterion, criterion);
		}

		if (endModifiedDate != null) {
			Criterion endDateCriterion = getAttributeRangeCriterion(
					model, "modifiedDate", endModifiedDate, false);

			criterion = ModelUtil.generateConjunctionCriterion(
				endDateCriterion, criterion);
		}

		if ((criterion == null) && ((keyAttributes == null) ||
			 keyAttributes.isEmpty())) {

			return model;
		}

		if ((criterion != null) && (model.count(criterion) == -1)) {
			cacheNullModelObject.add(className);

			return null;
		}

		ModelWrapper modelWrapper = new ModelWrapper(model);

		if (criterion != null) {
			modelWrapper.setCriterion(criterion);
		}

		if ((keyAttributes != null) && !keyAttributes.isEmpty()) {
			modelWrapper.setKeyAttributes(keyAttributes);
		}

		cacheModelObject.put(className, modelWrapper);

		return modelWrapper;
	}

	public Portlet getPortlet(String className) {
		Set<Portlet> portlets = getPortlets(className);

		if ((portlets == null) || portlets.isEmpty()) {
			return null;
		}

		return portlets.toArray(new Portlet[portlets.size()])[0];
	}

	public Set<Portlet> getPortlets(String className) {
		Object stagingDataHandler =
			StagedModelDataHandlerRegistryUtil.getStagedModelDataHandler(
				className);

		if (stagingDataHandler == null) {
			return Collections.emptySet();
		}

		if (stagingDataHandler instanceof Proxy) {
			try {
				ClassLoaderBeanHandler classLoaderBeanHandler =
					(ClassLoaderBeanHandler)
						Proxy.getInvocationHandler(stagingDataHandler);
				stagingDataHandler = classLoaderBeanHandler.getBean();

				if (stagingDataHandler == null) {
					return Collections.emptySet();
				}
			}
			catch (Exception e) {
				if (_log.isDebugEnabled()) {
					_log.debug(e, e);
				}
			}
		}

		String key = stagingDataHandler.getClass().getName();

		if (!handlerPortletMap.containsKey(key)) {
			return Collections.emptySet();
		}

		return handlerPortletMap.get(key);
	}

	protected void fillHandlerPortletIdMap() {
		for (Portlet portlet : PortletLocalServiceUtil.getPortlets()) {
			for (String handler : portlet.getStagedModelDataHandlerClasses()) {
				if (!handlerPortletMap.containsKey(handler)) {
					handlerPortletMap.put(handler, new HashSet<Portlet>());
				}

				Set<Portlet> portletSet = handlerPortletMap.get(handler);

				if (!portletSet.contains(portlet)) {
					portletSet.add(portlet);

					if (_log.isDebugEnabled()) {
						_log.debug(
							"Adding: " + handler + " portlet " + portlet);
					}
				}
			}
		}
	}

	protected Criterion getAttributeRangeCriterion(
		Model model, String attribute, Object value, boolean isStartValue) {

		if (!model.hasAttribute(attribute)) {
			return RestrictionsFactoryUtil.disjunction();
		}

		Property property = model.getProperty(attribute);

		if (isStartValue) {
			return property.ge(value);
		}

		return property.lt(value);
	}

	protected Map<String, Set<Portlet>> handlerPortletMap =
		new HashMap<String, Set<Portlet>>();

	private static Log _log = LogFactoryUtil.getLog(
		StagingCheckerModelFactory.class);

	private long companyId;
	private Date endModifiedDate;
	private Date startModifiedDate;

}