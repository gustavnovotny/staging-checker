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

package jorgediazest.stagingchecker.data;

import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.service.GroupLocalServiceUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import jorgediazest.stagingchecker.ExecutionMode;
import jorgediazest.stagingchecker.model.StagingCheckerModel;

/**
 * @author Jorge Díaz
 */
public class Results {

	public static void dumpToLog(
			boolean groupBySite,
			Map<Long, List<Results>> resultDataMap)
		throws SystemException {

		if (!_log.isInfoEnabled()) {
			return;
		}

		for (Entry<Long, List<Results>> entry : resultDataMap.entrySet()) {
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

			for (Results result : entry.getValue()) {
				result.dumpToLog();
			}
		}
	}

	public static Results getError(StagingCheckerModel model, Exception e) {
		_log.error(
			"Model: " + model.getName() + " EXCEPTION: " +
				e.getClass() + " - " + e.getMessage(),e);

		return new Results(model, e.getClass() + " - " + e.getMessage());
	}

	public static Results getStagingCheckResult(
		StagingCheckerModel model, Set<Data> stagingData, Set<Data> liveData,
		Set<ExecutionMode> executionMode) {

		Map<String, Set<Data>> data = new HashMap<String, Set<Data>>();

		if (executionMode.contains(ExecutionMode.SHOW_BOTH_EXACT)) {
			data.put("both-exact-staging", new HashSet<Data>());
			data.put("both-exact-live", new HashSet<Data>());
		}

		if (executionMode.contains(ExecutionMode.SHOW_BOTH_NOTEXACT)) {
			data.put("both-notexact-staging", new HashSet<Data>());
			data.put("both-notexact-live", new HashSet<Data>());
		}

		Data[] bothArrSetStaging = Results.getBothDataArray(
			stagingData, liveData);
		Data[] bothArrSetLive = Results.getBothDataArray(liveData, stagingData);

		if (executionMode.contains(ExecutionMode.SHOW_BOTH_EXACT) ||
			executionMode.contains(ExecutionMode.SHOW_BOTH_NOTEXACT)) {

			for (int i = 0; i<bothArrSetLive.length; i++) {
				Data dataLive = bothArrSetLive[i];
				Data dataStaging = bothArrSetStaging[i];

				if (!dataLive.equals(dataStaging)) {
					throw new RuntimeException("Inconsistent data");
				}
				else if (dataLive.exact(dataStaging)) {
					if (executionMode.contains(ExecutionMode.SHOW_BOTH_EXACT)) {
						data.get("both-exact-live").add(dataLive);
						data.get("both-exact-staging").add(dataStaging);
					}
				}
				else if (executionMode.contains(
							ExecutionMode.SHOW_BOTH_NOTEXACT)) {

					data.get("both-notexact-live").add(dataLive);
					data.get("both-notexact-staging").add(dataStaging);
				}
			}
		}

		Set<Data> bothDataSet = new HashSet<Data>(liveData);
		bothDataSet.retainAll(stagingData);

		if (executionMode.contains(ExecutionMode.SHOW_STAGING)) {
			Set<Data> stagingOnlyData = stagingData;
			stagingOnlyData.removeAll(bothDataSet);
			data.put("only-staging", stagingOnlyData);
		}

		if (executionMode.contains(ExecutionMode.SHOW_LIVE)) {
			Set<Data> liveOnlyData = liveData;
			liveOnlyData.removeAll(bothDataSet);
			data.put("only-live", liveOnlyData);
		}

		return new Results(model, data);
	}

	public void dumpToLog() {

		if (!_log.isInfoEnabled()) {
			return;
		}

		_log.info("*** ClassName: "+ model.getName());

		for (Entry<String, Set<Data>> entry : data.entrySet()) {
			if (entry.getValue().size() != 0) {
				_log.info("==" + entry.getKey() + "==");

				for (Data d : entry.getValue()) {
					_log.info(d.getAllData(","));
				}
			}
		}
	}

	public Set<Data> getData(String type) {
		if ("both-exact".equals(type)) {
			type = "both-exact-staging";
		}
		else if ("both-notexact".equals(type)) {
			type = "both-notexact-staging";
		}

		return data.get(type);
	}

	public String getError() {
		return error;
	}

	public StagingCheckerModel getModel() {
		return model;
	}

	protected static Data[] getBothDataArray(Set<Data> set1, Set<Data> set2) {
		Set<Data> both = new TreeSet<Data>(set1);
		both.retainAll(set2);
		return both.toArray(new Data[0]);
	}

	protected Results(StagingCheckerModel model, Map<String, Set<Data>> data) {
		this.data = data;
		this.error = null;
		this.model = model;
	}

	protected Results(StagingCheckerModel model, String error) {
		this.data = new HashMap<String, Set<Data>>();
		this.error = error;
		this.model = model;
	}

	private static Log _log = LogFactoryUtil.getLog(Results.class);

	private Map<String, Set<Data>> data;
	private String error;
	private StagingCheckerModel model;

}