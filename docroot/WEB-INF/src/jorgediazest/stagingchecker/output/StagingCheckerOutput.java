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

package jorgediazest.stagingchecker.output;

import com.liferay.portal.kernel.dao.search.ResultRow;
import com.liferay.portal.kernel.dao.search.SearchContainer;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.Group;
import com.liferay.portal.service.GroupLocalServiceUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.portlet.PortletConfig;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;

import jorgediazest.stagingchecker.ExecutionMode;
import jorgediazest.stagingchecker.data.Results;
import jorgediazest.stagingchecker.model.StagingCheckerModel;

import jorgediazest.util.data.Data;
import jorgediazest.util.data.DataUtil;
import jorgediazest.util.output.OutputUtils;

/**
 * @author Jorge Díaz
 */
public class StagingCheckerOutput {

	public static List<String> generateCSVOutput(
		PortletConfig portletConfig, String title, Locale locale,
		EnumSet<ExecutionMode> executionMode,
		Map<Company, Long> companyProcessTime,
		Map<Company, Map<Long, List<Results>>> companyResultDataMap,
		Map<Company, String> companyError) {

		List<String> out = new ArrayList<String>();

		if (companyResultDataMap != null) {
			String[] headerKeys = new String[] {
				"output.company", "output.groupid", "output.groupname",
				"output.entityclass", "output.entityname", "output.errortype",
				"output.count", "output.primarykeys"};

			List<String> headers = OutputUtils.getHeaders(
				portletConfig, locale, headerKeys);

			out.add(OutputUtils.getCSVRow(headers));
		}

		for (
			Map.Entry<Company, Long> companyEntry :
				companyProcessTime.entrySet()) {

			Long processTime = companyEntry.getValue();

			String companyOutput =
				companyEntry.getKey().getCompanyId() + " - " +
				companyEntry.getKey().getWebId();

			if (companyResultDataMap != null) {
				Map<Long, List<Results>> resultDataMap =
					companyResultDataMap.get(companyEntry.getKey());

				int numberOfRows = 0;

				for (
					Map.Entry<Long, List<Results>> entry :
						resultDataMap.entrySet()) {

					String groupIdOutput = null;
					String groupNameOutput = null;

					try {
						Group group = GroupLocalServiceUtil.fetchGroup(
							entry.getKey());

						if (group == null) {
							groupIdOutput = LanguageUtil.get(
								portletConfig, locale,
								"output.not-applicable-groupid");
							groupNameOutput = LanguageUtil.get(
								portletConfig, locale,
								"output.not-applicable-groupname");
						}
						else {
							groupIdOutput = "" + group.getGroupId();
							groupNameOutput = group.getName();
						}
					}
					catch (Exception e) {
						groupIdOutput = "" + entry.getKey();
					}

					for (Results result : entry.getValue()) {
						String lineError = generateCSVRow(
							portletConfig, result, companyOutput, groupIdOutput,
							groupNameOutput, "error", locale, result.getError(),
							"");

						if (lineError != null) {
							numberOfRows++;
							out.add(lineError);
						}

						for (String type : outputTypes) {
							String line = generateCSVRow(
									portletConfig, result, companyOutput,
									groupIdOutput, groupNameOutput, type,
									locale);

							if (line != null) {
								numberOfRows++;
								out.add(line);
							}
						}
					}
				}

				if (numberOfRows == 0) {
					out.add(StringPool.BLANK);
					out.add(
						"No results found: your system is ok or perhaps " +
						"you have to change some filters");
				}
			}

			String errorMessage = companyError.get(companyEntry.getKey());

			if (Validator.isNotNull(errorMessage)) {
				out.add(
					"Company: " + companyEntry.getKey().getCompanyId() +
					" - " + companyEntry.getKey().getWebId());
				out.add(errorMessage);
			}

			out.add(StringPool.BLANK);
			out.add(
				"Executed " + title + " for company " +
				companyEntry.getKey().getCompanyId() + " in " + processTime +
				" ms");
		}

		return out;
	}

	public static SearchContainer<Results> generateSearchContainer(
		PortletConfig portletConfig, RenderRequest renderRequest,
		EnumSet<ExecutionMode> executionMode,
		Map<Long, List<Results>> resultDataMap,
		PortletURL serverURL) throws SystemException {

		Locale locale = renderRequest.getLocale();

		String[] headerKeys = new String[] {
			"output.groupid", "output.groupname", "output.entityclass",
			"output.entityname", "output.errortype", "output.count",
			"output.primarykeys"};

		List<String> headerNames = OutputUtils.getHeaders(
			portletConfig, locale, headerKeys);

		SearchContainer<Results> searchContainer =
			new SearchContainer<Results>(
				renderRequest, null, null, SearchContainer.DEFAULT_CUR_PARAM,
				SearchContainer.MAX_DELTA, serverURL, headerNames, null);

		int numberOfRows = 0;

		for (
			Entry<Long, List<Results>> entry :
				resultDataMap.entrySet()) {

			String groupIdOutput = null;
			String groupNameOutput = null;

			Group group = GroupLocalServiceUtil.fetchGroup(entry.getKey());

			if (group == null) {
				groupIdOutput = LanguageUtil.get(
					portletConfig, locale, "output.not-applicable-groupid");
				groupNameOutput = LanguageUtil.get(
					portletConfig, locale, "output.not-applicable-groupname");
			}
			else {
				groupIdOutput = "" + group.getGroupId();
				groupNameOutput = group.getName();
			}

			List<Results> results = searchContainer.getResults();

			if ((results == null) || (results.size() == 0)) {
				results = new ArrayList<Results>();
			}

			results.addAll(entry.getValue());

			results = ListUtil.subList(
				results, searchContainer.getStart(), searchContainer.getEnd());

			searchContainer.setResults(results);

			List<ResultRow> resultRows = searchContainer.getResultRows();

			for (Results result : entry.getValue()) {
				ResultRow rowError = generateSearchContainerRow(
					portletConfig, result, groupIdOutput, groupNameOutput,
					"error", locale, numberOfRows, result.getError(), "");

				if (rowError != null) {
					numberOfRows++;
					resultRows.add(rowError);
				}

				for (String type : outputTypes) {
					ResultRow row = generateSearchContainerRow(
						portletConfig, result, groupIdOutput, groupNameOutput,
						type, locale, numberOfRows);

					if (row != null) {
						numberOfRows++;
						resultRows.add(row);
					}
				}
			}
		}

		searchContainer.setTotal(numberOfRows);

		return searchContainer;
	}

	static Log _log = LogFactoryUtil.getLog(StagingCheckerOutput.class);

	protected static String generateCSVRow(
		PortletConfig portletConfig, Results result, String companyOutput,
		String groupIdOutput, String groupNameOutput, String type,
		Locale locale) {

		Set<Data> data = result.getData(type);

		if ((data == null) || data.isEmpty()) {
			return null;
		}

		String output = DataUtil.getValuesPKText(type, data);
		String outputSize = "" + data.size();

		return generateCSVRow(
			portletConfig, result, companyOutput, groupIdOutput,
			groupNameOutput, type, locale, output, outputSize);
	}

	protected static String generateCSVRow(
		PortletConfig portletConfig, Results result, String companyOutput,
		String groupIdOutput, String groupNameOutput, String type,
		Locale locale, String output, String outputSize) {

		if (Validator.isNull(output)) {
			return null;
		}

		StagingCheckerModel model = result.getModel();

		String modelOutput = model.getName();
		String modelDisplayNameOutput = model.getDisplayName(locale);

		List<String> line = new ArrayList<String>();
		line.add(companyOutput);

		if (groupIdOutput != null) {
			line.add(groupIdOutput);
			line.add(groupNameOutput);
		}

		line.add(modelOutput);
		line.add(modelDisplayNameOutput);
		line.add(LanguageUtil.get(portletConfig, locale, "output." + type));
		line.add(outputSize);
		line.add(output);
		return OutputUtils.getCSVRow(line);
	}

	protected static ResultRow generateSearchContainerRow(
		PortletConfig portletConfig, Results result, String groupIdOutput,
		String groupNameOutput, String type, Locale locale, int numberOfRows) {

		Set<Data> data = result.getData(type);

		if ((data == null) || data.isEmpty()) {
			return null;
		}

		String output = DataUtil.getValuesPKText(type, data);
		String outputSize = ""+data.size();

		return generateSearchContainerRow(
			portletConfig, result, groupIdOutput, groupNameOutput, type, locale,
			numberOfRows, output, outputSize);
	}

	protected static ResultRow generateSearchContainerRow(
		PortletConfig portletConfig, Results result, String groupIdOutput,
		String groupNameOutput, String type, Locale locale, int numberOfRows,
		String output, String outputSize) {

		if (Validator.isNull(output)) {
			return null;
		}

		ResultRow row = new ResultRow(result, type, numberOfRows);
		StagingCheckerModel model = result.getModel();

		String modelOutput = model.getName();
		String modelDisplayNameOutput = model.getDisplayName(locale);

		if ((groupIdOutput != null) && (groupNameOutput!= null)) {
			row.addText(groupIdOutput);
			row.addText(groupNameOutput);
		}

		row.addText(HtmlUtil.escape(modelOutput));
		row.addText(HtmlUtil.escape(modelDisplayNameOutput));
		row.addText(
			HtmlUtil.escape(
				LanguageUtil.get(
					portletConfig, locale, "output." + type)).replace(
						" ", "&nbsp;"));
		row.addText(HtmlUtil.escape(outputSize));
		row.addText(HtmlUtil.escape(output));
		return row;
	}

	private static String[] outputTypes = {
		"both-exact", "both-notexact", "only-staging", "only-live"};

}