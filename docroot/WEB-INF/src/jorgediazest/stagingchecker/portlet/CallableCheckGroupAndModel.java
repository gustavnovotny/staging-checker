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

import com.liferay.portal.kernel.dao.shard.ShardUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.security.auth.CompanyThreadLocal;
import com.liferay.portal.service.GroupLocalServiceUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import jorgediazest.stagingchecker.ExecutionMode;
import jorgediazest.stagingchecker.model.StagingCheckerQueryHelper;
import jorgediazest.stagingchecker.util.ConfigurationUtil;

import jorgediazest.util.comparator.DataComparator;
import jorgediazest.util.comparator.DataModelComparator;
import jorgediazest.util.data.Comparison;
import jorgediazest.util.data.ComparisonUtil;
import jorgediazest.util.data.Data;
import jorgediazest.util.model.Model;

/**
 * @author Jorge Díaz
 */
public class CallableCheckGroupAndModel implements Callable<Comparison> {

	public CallableCheckGroupAndModel(
		Map<String, Map<Long, List<Data>>> queryCache, long companyId,
		long groupId, Model model, Set<ExecutionMode> executionMode) {

		this.companyId = companyId;
		this.groupId = groupId;
		this.queryCache = queryCache;
		this.model = model;
		this.executionMode = executionMode;
	}

	@Override
	public Comparison call() throws Exception {

		boolean showBothExact = executionMode.contains(
			ExecutionMode.SHOW_BOTH_EXACT);
		boolean showBothNotExact = executionMode.contains(
			ExecutionMode.SHOW_BOTH_NOTEXACT);
		boolean showOnlyStaging = executionMode.contains(
			ExecutionMode.SHOW_STAGING);
		boolean showOnlyLive = executionMode.contains(ExecutionMode.SHOW_LIVE);

		try {
			CompanyThreadLocal.setCompanyId(companyId);

			ShardUtil.pushCompanyService(companyId);

			if (_log.isInfoEnabled()) {
				_log.info(
					"Model: " + model.getName() + " - CompanyId: " +
						companyId + " - GroupId: " + groupId);
			}

			Group group = GroupLocalServiceUtil.fetchGroup(groupId);

			long stagingGroupId = group.getStagingGroup().getGroupId();

			StagingCheckerQueryHelper queryHelper =
				ConfigurationUtil.getQueryHelper(model);

			Map<Long, Data> stagingDataMap = queryHelper.getLiferayData(
				model, stagingGroupId);

			queryHelper.addRelatedModelData(
				queryCache, stagingDataMap, model, stagingGroupId);

			Set<Data> stagingData = new HashSet<Data>(stagingDataMap.values());

			Map<Long, Data> liveDataMap = queryHelper.getLiferayData(
				model, groupId);

			queryHelper.addRelatedModelData(
				queryCache, liveDataMap, model, groupId);

			Set<Data> liveData = new HashSet<Data>(liveDataMap.values());

			Collection<String> exactAttributes =
				ConfigurationUtil.getExactAttributesToCheck(model);

			List<String> exactAttributesList = new ArrayList<String>(
				model.getKeyAttributes());

			exactAttributesList.addAll(exactAttributes);

			DataComparator exactDataComparator = new DataModelComparator(
				exactAttributesList);

			return ComparisonUtil.getComparison(
				model, exactDataComparator, stagingData, liveData,
				showBothExact, showBothNotExact, showOnlyStaging, showOnlyLive);
		}
		catch (Throwable t) {
			return ComparisonUtil.getError(model, t);
		}
		finally {
			ShardUtil.popCompanyService();
		}
	}

	private static Log _log = LogFactoryUtil.getLog(
		CallableCheckGroupAndModel.class);

	private long companyId = -1;
	private Set<ExecutionMode> executionMode = null;
	private long groupId = -1;
	private Model model = null;
	private Map<String, Map<Long, List<Data>>> queryCache = null;

}