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

import com.liferay.portal.kernel.lar.StagedModelDataHandler;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import jorgediazest.util.model.Model;
import jorgediazest.util.model.ModelFactory;
import jorgediazest.util.service.Service;

/**
 * @author Jorge Díaz
 */
public class StagingCheckerModelFactory extends ModelFactory {

	@Override
	protected Model getModelObject(Service service) {
		Model model = super.getModelObject(service);

		if (_log.isDebugEnabled()) {
			_log.debug(
				model + " - isStagedModel: " + model.isStagedModel() +
					" - isGroupedModel: " + model.isGroupedModel() +
						" - portlets: " + model.getPortlets());
		}

		if (!model.isStagedModel() || !model.isGroupedModel() ||
			(model.getPortlet() == null)) {

			return model;
		}

		if (model.hasAttribute("classNameId")) {
			model.addFilter(model.generateCriterionFilter("classNameId=0"));
		}

		StagedModelDataHandler<?> stagedModelDataHandler =
			model.getStagedModelDataHandler();

		if ((stagedModelDataHandler != null) && model.isWorkflowEnabled()) {
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

		return model;
	}

	private static Log _log = LogFactoryUtil.getLog(
		StagingCheckerModelFactory.class);

}