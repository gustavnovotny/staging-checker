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
import com.liferay.portal.kernel.lar.StagedModelDataHandler;
import com.liferay.portal.kernel.lar.StagedModelDataHandlerRegistryUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Portlet;
import com.liferay.portal.service.PortletLocalServiceUtil;

import java.lang.reflect.Proxy;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jorgediazest.util.model.Model;
import jorgediazest.util.model.ModelFactory;
import jorgediazest.util.model.ModelWrapper;
import jorgediazest.util.service.Service;

/**
 * @author Jorge Díaz
 */
public class StagingCheckerModelFactory extends ModelFactory {

	public StagingCheckerModelFactory() {
		fillHandlerPortletIdMap();
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

	@Override
	protected Model getModelObject(Service service) {
		Model model = super.getModelObject(service);

		if (model == null) {
			return null;
		}

		Set<Portlet> portlets = getPortlets(model.getClassName());

		if (_log.isDebugEnabled()) {
			_log.debug(
				model + " - isStagedModel: " + model.isStagedModel() +
					" - isGroupedModel: " + model.isGroupedModel() +
						" - portlets: " + portlets);
		}

		if (!model.isStagedModel() || !model.isGroupedModel() ||
			portlets.isEmpty()) {

			return model;
		}

		ModelWrapper modelWrapper = new ModelWrapper(model);

		if (model.hasAttribute("classNameId")) {
			modelWrapper.addFilter(
				model.generateCriterionFilter("classNameId=0"));
		}

		String className = model.getClassName();

		StagedModelDataHandler<?> stagedModelDataHandler =
			StagedModelDataHandlerRegistryUtil.getStagedModelDataHandler(
				className);

		if ((stagedModelDataHandler != null) && model.isWorkflowEnabled()) {
			modelWrapper.addFilter(
				model.getProperty("status").in(
					stagedModelDataHandler.getExportableStatuses()));
		}

		if (model.getClassName().startsWith(
				"com.liferay.portlet.documentlibrary.model.") &&
			model.hasAttribute("repositoryId")) {

			modelWrapper.addFilter(
				model.generateSingleCriterion("groupId=repositoryId"));
		}
		else if (model.getClassName().startsWith(
					"com.liferay.portlet.dynamicdatamapping.model.")) {

			modelWrapper.setFilter(null);
		}

		return modelWrapper;
	}

	protected Map<String, Set<Portlet>> handlerPortletMap =
		new HashMap<String, Set<Portlet>>();

	private static Log _log = LogFactoryUtil.getLog(
		StagingCheckerModelFactory.class);

}