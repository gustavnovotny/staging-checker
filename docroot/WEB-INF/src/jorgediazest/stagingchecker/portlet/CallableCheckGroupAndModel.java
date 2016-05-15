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

import com.liferay.portal.kernel.dao.orm.Criterion;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.service.GroupLocalServiceUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import jorgediazest.stagingchecker.ExecutionMode;

import jorgediazest.util.data.Comparison;
import jorgediazest.util.data.ComparisonUtil;
import jorgediazest.util.data.Data;
import jorgediazest.util.model.Model;

/**
 * @author Jorge Díaz
 */
public class CallableCheckGroupAndModel implements Callable<Comparison> {

	public static Set<String> calculateAttributesToCheck(Model model) {
		Set<String> attributesToCheck = new LinkedHashSet<String>();

		attributesToCheck.add(model.getPrimaryKeyAttribute());
		attributesToCheck.add("companyId");
		attributesToCheck.add("uuid");

		if (model.isResourcedModel()) {
			attributesToCheck.add("resourcePrimKey");
		}

		attributesToCheck.addAll(
			Arrays.asList(model.getDataComparator().getExactAttributes()));

		return attributesToCheck;
	}

	public CallableCheckGroupAndModel(
		long companyId, long groupId, Model model,
		Set<ExecutionMode> executionMode) {

		this.companyId = companyId;
		this.groupId = groupId;
		this.model = model;
		this.executionMode = executionMode;
	}

	@Override
	public Comparison call() throws Exception {

		try {
			if (_log.isInfoEnabled()) {
				_log.info(
					"Model: " + model.getName() + " - CompanyId: " +
					companyId + " - GroupId: " + groupId);
			}

			if (!model.hasAttribute("groupId")) {
				return null;
			}

			Group group = GroupLocalServiceUtil.fetchGroup(groupId);

			if (!group.isStagedPortlet(model.getPortletId())) {
				if (_log.isDebugEnabled()) {
					_log.debug(
						model.getName() + " is not staged for group " +
							groupId);
				}

				return null;
			}

			long stagingGroupId = group.getStagingGroup().getGroupId();

			Criterion stagingFilter = model.getCompanyGroupFilter(
				companyId, stagingGroupId);

			String[] attributesToCheck = calculateAttributesToCheck(
				model).toArray(new String[0]);

			Set<Data> stagingData = new HashSet<Data>(
				model.getData(attributesToCheck, stagingFilter).values());

			Criterion liveFilter = model.getCompanyGroupFilter(
				companyId, groupId);

			Set<Data> liveData = new HashSet<Data>(
				model.getData(attributesToCheck, liveFilter).values());

			boolean showBothExact = executionMode.contains(
				ExecutionMode.SHOW_BOTH_EXACT);
			boolean showBothNotExact = executionMode.contains(
				ExecutionMode.SHOW_BOTH_NOTEXACT);
			boolean showOnlyStaging = executionMode.contains(
				ExecutionMode.SHOW_STAGING);
			boolean showOnlyLive = executionMode.contains(
				ExecutionMode.SHOW_LIVE);

			return ComparisonUtil.getComparison(
				model, stagingData, liveData, showBothExact, showBothNotExact,
				showOnlyStaging, showOnlyLive);
		}
		catch (Throwable t) {
			return ComparisonUtil.getError(model, t);
		}
	}

	private static Log _log = LogFactoryUtil.getLog(
		CallableCheckGroupAndModel.class);

	private long companyId = -1;
	private Set<ExecutionMode> executionMode = null;
	private long groupId = -1;
	private Model model = null;

}