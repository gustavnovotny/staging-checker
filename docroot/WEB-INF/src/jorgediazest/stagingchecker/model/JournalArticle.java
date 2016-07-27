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
import com.liferay.portlet.journal.model.JournalArticleResource;

import java.util.Map;

import jorgediazest.util.data.Data;
import jorgediazest.util.modelquery.ModelQueryImpl;

/**
 * @author Jorge Díaz
 */
public class JournalArticle extends ModelQueryImpl {

	public Map<Long, Data> getData(
			String[] attributes, String mapKeyAttribute, Criterion filter)
		throws Exception {

		Map<Long, Data> dataMap = super.getData(
			attributes, mapKeyAttribute, filter);

		addRelatedModelData(
			dataMap, JournalArticleResource.class.getName(),
			" =resourcePrimKey,resourceUuid=uuid".split(","),
			"resourcePrimKey".split(","), false, false);

		for (Data data : dataMap.values()) {
			String resourceUuid = (String) data.get("resourceUuid");

			Object version = data.get("version");

			if ((resourceUuid != null) && (version != null)) {
				data.set("uuid", resourceUuid + "_" + version.toString());
			}
		}

		return dataMap;
	}

}