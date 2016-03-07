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

import com.liferay.portal.kernel.dao.orm.Criterion;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.ProjectionList;
import com.liferay.portlet.journal.model.JournalArticleResource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jorgediazest.util.data.Data;
import jorgediazest.util.model.Model;
import jorgediazest.util.service.Service;

/**
 * @author Jorge Díaz
 */
public class JournalArticle extends IgnoreCreateDateModel {

	public Map<Long, Data> getData(String[] attributes, Criterion filter)
		throws Exception {

		Map<Long, Data> dataMap = super.getData(attributes, filter);

		for (Data data : dataMap.values()) {
			String resourceUuid = mapResourcePrimKeyUuid.get(
				data.getResourcePrimKey());

			Object version = data.get("version");

			if ((resourceUuid != null) && (version != null)) {
				data.setUuid(resourceUuid + "_" + version.toString());
			}
		}

		return dataMap;
	}

	@Override
	public void init(
			String classPackageName, String classSimpleName, Service service)
		throws Exception {

		super.init(classPackageName, classSimpleName, service);

		mapResourcePrimKeyUuid = initMapResourcePrimKeyUuid();
	}

	public Map<Long, String> initMapResourcePrimKeyUuid() throws Exception {
		Model modelResource = modelFactory.getModelObject(
			null, JournalArticleResource.class.getName());

		DynamicQuery queryModelResource =
			modelResource.getService().newDynamicQuery();

		ProjectionList projectionList = ProjectionFactoryUtil.projectionList();
		projectionList.add(
			modelResource.getPropertyProjection("resourcePrimKey"));
		projectionList.add(modelResource.getPropertyProjection("uuid"));

		queryModelResource.setProjection(projectionList);

		@SuppressWarnings("unchecked")
		List<Object[]> results =
			(List<Object[]>)modelResource.getService().executeDynamicQuery(
				queryModelResource);

		Map<Long, String> mapResourcePrimKeyUuidAux =
			new ConcurrentHashMap<Long, String>();

		for (Object[] result : results) {
			long resourcePrimKey = (Long)result[0];
			String uuid = (String)result[1];

			mapResourcePrimKeyUuidAux.put(resourcePrimKey, uuid);
		}

		return mapResourcePrimKeyUuidAux;
	}

	protected Map<Long, String> mapResourcePrimKeyUuid = null;

}