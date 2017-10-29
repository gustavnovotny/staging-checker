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

import com.liferay.exportimport.kernel.lar.PortletDataException;
import com.liferay.exportimport.kernel.lar.PortletDataHandler;
import com.liferay.exportimport.kernel.lar.PortletDataHandlerControl;
import com.liferay.exportimport.kernel.lar.StagedModelDataHandler;
import com.liferay.exportimport.kernel.lar.StagedModelDataHandlerRegistryUtil;
import com.liferay.portal.kernel.bean.ClassLoaderBeanHandler;
import com.liferay.portal.kernel.dao.orm.Criterion;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Portlet;
import com.liferay.portal.kernel.service.PortletLocalServiceUtil;

import java.lang.reflect.Proxy;

import java.util.Collections;
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

	long companyId;

	public StagingCheckerModelFactory() {
		this(0L);
	}

	public StagingCheckerModelFactory(long companyId) {
		fillClassNamePortletMapping();

		this.companyId = companyId;
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

		Set<Portlet> portlets = getPortletSet(model.getClassName());

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

	public Set<Portlet> getPortletSet(String className) {
		if (!classNamePortletMap.containsKey(className)) {
			return new HashSet<>();
		}

		return classNamePortletMap.get(className);
	}

	protected void fillClassNamePortletMapping() {
		for (Portlet portlet : PortletLocalServiceUtil.getPortlets()) {
			PortletDataHandler portletDataHandler =
				portlet.getPortletDataHandlerInstance();

			PortletDataHandlerControl[] pdhControlArr;

			try {
				pdhControlArr = portletDataHandler.getExportControls();
			}
			catch (PortletDataException pde) {
				_log.warn(pde, pde);

				continue;
			}

			for (PortletDataHandlerControl pdhControl : pdhControlArr) {
				addClassNamePortletMapping(pdhControl.getClassName(), portlet);
			}
		}
	}

	private void addClassNamePortletMapping(String className, Portlet portlet) {
		if (!classNamePortletMap.containsKey(className)) {
			classNamePortletMap.put(className, new HashSet<Portlet>());
		}

		Set<Portlet> portletSet = classNamePortletMap.get(className);

		if (!portletSet.contains(portlet)) {
			portletSet.add(portlet);

			if (_log.isDebugEnabled()) {
				_log.debug("Adding: " + className + " portlet " + portlet);
			}
		}
	}

	protected Map<String, Set<Portlet>> classNamePortletMap =
		new HashMap<String, Set<Portlet>>();

	private static Log _log = LogFactoryUtil.getLog(
		StagingCheckerModelFactory.class);

}