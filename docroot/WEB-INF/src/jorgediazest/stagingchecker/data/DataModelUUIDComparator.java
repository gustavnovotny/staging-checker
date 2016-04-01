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

import com.liferay.portal.kernel.util.LocalizationUtil;
import com.liferay.portal.kernel.util.Validator;

import jorgediazest.util.data.Data;
import jorgediazest.util.data.DataModelComparator;

/**
 * @author Jorge Díaz
 */
public class DataModelUUIDComparator extends DataModelComparator {

	public DataModelUUIDComparator(String[] exactAttributes) {
		super(exactAttributes);
	}

	@Override
	public int compare(Data data1, Data data2) {

		if (Validator.isNotNull(data1.getUuid())) {
			return data1.getUuid().compareTo(data2.getUuid());
		}
		else if (Validator.isNotNull(data2.getUuid())) {
			return -1 * compare(data2, data1);
		}
		else {
			return 0;
		}
	}

	@Override
	public boolean equals(Data data1, Data data2) {
		return Validator.equals(data1.getUuid(), data2.getUuid());
	}

	@Override
	public boolean exact(Data data1, Data data2) {
		if (!data1.equals(data2)) {
			return false;
		}

		if (!Validator.equals(data1.getCompanyId(), data2.getCompanyId())) {
			return false;
		}

		if (data1.getModel().hasAttribute("groupId") &&
			!Validator.equals(data1.getGroupId(), data2.getGroupId())) {

			return false;
		}

		for (String attr : exactAttributes) {
			Object value1 = data1.get(attr);
			Object value2 = data2.get(attr);

			if (Validator.isNotNull(value1) &&
				Validator.isNotNull(value2) &&
				!Validator.equals(value1, value2)) {

			if (value1 instanceof String && value2 instanceof String &&
				("name".equals(attr) || "title".equals(attr) ||
				 "description".equals(attr))) {

					value1 = LocalizationUtil.getLocalizationMap(
						(String)value1);
					value2 = LocalizationUtil.getLocalizationMap(
						(String)value2);

					return Validator.equals(value1, value2);
				}
				else {
					return false;
				}
			}
		}

		return true;
	}

	@Override
	public Integer hashCode(Data data) {
		if (Validator.isNull(data.getUuid())) {
			return null;
		}

		return data.getUuid().hashCode();
	}

}