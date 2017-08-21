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

package jorgediazest.stagingchecker.util;

import com.liferay.asset.kernel.AssetRendererFactoryRegistryUtil;
import com.liferay.asset.kernel.model.AssetRendererFactory;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.PrefsPropsUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jorgediazest.stagingchecker.model.StagingCheckerQueryHelper;

import jorgediazest.util.model.Model;

import org.yaml.snakeyaml.Yaml;
/**
 * @author Jorge Díaz
 */
public class ConfigurationUtil {

	public static Object getConfigurationEntry(String configurationEntry) {
		Map<String, Object> configuration = getConfiguration();

		if (configuration == null) {
			return null;
		}

		return configuration.get(configurationEntry);
	}

	public static int getDefaultNumberThreads() {
		return PortletPropsValues.NUMBER_THREADS;
	}

	@SuppressWarnings("unchecked")
	public static Collection<String> getExactAttributesToCheck(Model model) {
		return (Collection<String>)getModelInfo(
			model, "exactAttributesToCheck");
	}

	@SuppressWarnings("unchecked")
	public static List<String> getKeyAttributes(Model model) {
		return (List<String>)getModelInfo(model, "keyAttributes");
	}

	@SuppressWarnings("unchecked")
	public static Collection<String> getModelAttributesToQuery(Model model) {
		Collection<String> attributesToCheck =
			(Collection<String>) getModelInfo(model, "attributesToQuery");

		if (attributesToCheck == null) {
			return Collections.emptySet();
		}

		return attributesToCheck;
	}

	public static Map<String, Map<String, Object>> getModelInfo() {
		if (modelInfo != null) {
			return modelInfo;
		}

		synchronized(ConfigurationUtil.class) {
			if (modelInfo == null) {
				Map<String, Map<String, Object>> modelInfoAux =
					new HashMap<String, Map<String, Object>>();

				@SuppressWarnings("unchecked")
				Collection<Map<String, Object>> modelInfoList =
					(Collection<Map<String, Object>>)getConfigurationEntry(
						"modelInfo");

				for (Map<String, Object> modelMap : modelInfoList) {
					String model = (String) modelMap.get("model");
					modelInfoAux.put(model, modelMap);
				}

				modelInfo = modelInfoAux;
			}

			return modelInfo;
		}
	}

	public static Object getModelInfo(Model model, String entry) {
		Object value = null;

		Map<String, Object> modelMap = getModelInfo().get(model.getClassName());

		if (modelMap != null) {
			value = modelMap.get(entry);
		}

		if (value != null) {
			return value;
		}

		if (model.isWorkflowEnabled()) {
			value = getModelInfo().get("workflowedModel").get(entry);

			if (value != null) {
				return value;
			}
		}

		if (model.isResourcedModel()) {
			value = getModelInfo().get("resourcedModel").get(entry);

			if (value != null) {
				return value;
			}
		}

		return getModelInfo().get("default").get(entry);
	}

	public static StagingCheckerQueryHelper getQueryHelper(Model model) {

		return (StagingCheckerQueryHelper)getModelInfo(
			model, "queryHelperClass");
	}

	public static List<Map<String, Object>> getRelatedDataToQuery(Model model) {
		boolean checkAssetEntryRelations =
			ConfigurationUtil.checkAssetEntryRelations(model);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> relatedDataToQueryList =
			(List<Map<String,Object>>)getModelInfo(model, "relatedDataToQuery");

		if (relatedDataToQueryList == null) {
			relatedDataToQueryList = Collections.emptyList();
		}

		List<Map<String, Object>> relatedDataToQueryListFiltered =
			new ArrayList<Map<String, Object>>();

		for (Map<String, Object> relatedDataToQuery : relatedDataToQueryList) {
			String relatedModel = (String)relatedDataToQuery.get("model");

			if (Validator.isNull(relatedModel)) {
				continue;
			}

			if ("com.liferay.portlet.asset.model.Asset".equals(relatedModel) &&
				checkAssetEntryRelations) {

				continue;
			}

			relatedDataToQueryListFiltered.add(relatedDataToQuery);
		}

		return relatedDataToQueryList;
	}

	public static String getStringFilter(Model model) {

		return (String) getModelInfo(model, "filter");
	}

	public static boolean ignoreClassName(String className) {
		if (Validator.isNull(className)) {
			return true;
		}

		return configurationListEntryContainsValue(
			"ignoreClassNames", className);
	}

	public static boolean modelNotIndexed(String className) {
		return configurationListEntryContainsValue(
			"modelNotIndexed", className);
	}

	protected static boolean checkAssetEntryRelations(Model model) {
		boolean assetEntryRelations = true;

		AssetRendererFactory assetRendererFactory =
			AssetRendererFactoryRegistryUtil.getAssetRendererFactoryByClassName(
				model.getClassName());

		if ((assetRendererFactory == null) ||
			!assetRendererFactory.isSelectable()) {

			assetEntryRelations = false;
		}

		return assetEntryRelations;
	}

	protected static boolean configurationListEntryContainsValue(
		String configurationEntry, String value) {

		@SuppressWarnings("unchecked")
		Collection<String> list = (Collection<String>)getConfigurationEntry(
			configurationEntry);

		return (list.contains(value));
	}

	protected static Map<String, Object> getConfiguration() {
		if (configuration != null) {
			return configuration;
		}

		synchronized(ConfigurationUtil.class) {
			try {
				if (configuration == null) {
					configuration = readConfiguration(CONFIGURATION_FILE);
				}
			}
			catch (IOException ioe) {
				_log.error(ioe);

				throw new RuntimeException(ioe);
			}
			catch (SystemException se) {
				_log.error(se);

				throw new RuntimeException(se);
			}

			return configuration;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> readConfiguration(
			String configurationFile)
		throws IOException, SystemException {

		ClassLoader classLoader = ConfigurationUtil.class.getClassLoader();

		InputStream inputStream = classLoader.getResourceAsStream(
			configurationFile);

		String configuration = StringUtil.read(inputStream);

		String journalArticleIndexPrimaryKeyAttribute;

		if (PrefsPropsUtil.getBoolean("journal.articles.index.all.versions")) {
			journalArticleIndexPrimaryKeyAttribute = "pk";
		}
		else {
			journalArticleIndexPrimaryKeyAttribute = "resourcePrimKey";
		}

		configuration = configuration.replace(
			"$$JOURNAL_ARTICLE_INDEX_PRIMARY_KEY_ATTRIBUTE$$",
			journalArticleIndexPrimaryKeyAttribute);

		Yaml yaml = new Yaml();

		return (Map<String, Object>)yaml.load(configuration);
	}

	private static final String CONFIGURATION_FILE = "configuration.yml";

	private static Log _log = LogFactoryUtil.getLog(ConfigurationUtil.class);

	private static Map<String, Object> configuration = null;
	private static Map<String, Map<String, Object>> modelInfo = null;

}