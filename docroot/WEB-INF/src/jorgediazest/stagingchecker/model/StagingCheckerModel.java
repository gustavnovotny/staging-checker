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

import com.liferay.portal.kernel.dao.orm.Conjunction;
import com.liferay.portal.kernel.dao.orm.Criterion;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.ProjectionList;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jorgediazest.stagingchecker.data.Data;
import jorgediazest.stagingchecker.data.DataUtil;

import jorgediazest.util.model.Model;
import jorgediazest.util.model.ModelImpl;
import jorgediazest.util.service.Service;

/**
 * @author Jorge Díaz
 */
public class StagingCheckerModel extends ModelImpl {

	public static String[] auditedModelAttributes =
		new String[] { "companyId", "createDate", "modifiedDate"};
	public static String[] groupedModelAttributes = new String[] { "groupId" };
	public static String[] resourcedModelAttributes =
		new String[] { "resourcePrimKey" };
	public static String[] stagedModelAttributes =
		new String[] { "uuid", "companyId", "createDate", "modifiedDate" };
	public static String[] workflowModelAttributes = new String[] { "status" };

	@Override
	public Model clone() {
		StagingCheckerModel model;
		try {
			model = (StagingCheckerModel)super.clone();
		}
		catch (Exception e) {
			_log.error("Error executing clone");
			throw new RuntimeException(e);
		}

		return model;
	}

	public int compareTo(Data data1, Data data2) {
		if ((data1.getPrimaryKey() != -1) && (data2.getPrimaryKey() != -1) &&
			!this.isResourcedModel()) {

			return DataUtil.compareLongs(
				data1.getPrimaryKey(), data2.getPrimaryKey());
		}
		else if ((data1.getResourcePrimKey() != -1) &&
				 (data2.getResourcePrimKey() != -1)) {

			return DataUtil.compareLongs(
				data1.getResourcePrimKey(), data2.getResourcePrimKey());
		}
		else {
			return 0;
		}
	}

	public Data createDataObject(Object[] result) {
		Data data = new Data(this);
		data.setPrimaryKey((Long)result[0]);

		int i = 0;

		for (String attrib : this.getLiferayIndexedAttributes()) {
			data.setProperty(attrib, result[i++]);
		}

		return data;
	}

	public boolean equals(Data data1, Data data2) {
		return Validator.equals(data1.getUuid(), data2.getUuid());
	}

	public boolean exact(Data data1, Data data2) {
		if (!data1.equals(data2)) {
			return false;
		}

		if (!Validator.equals(data1.getCompanyId(), data2.getCompanyId())) {
			return false;
		}

		if (this.hasAttribute("groupId") &&
			!Validator.equals(data1.getGroupId(), data2.getGroupId())) {

			return false;
		}

		if (!Validator.equals(data1.getCreateDate(), data2.getCreateDate())) {
			return false;
		}

		if (!Validator.equals(
				data1.getModifiedDate(), data2.getModifiedDate())) {

			return false;
		}

		if (!Validator.equals(data1.getStatus(), data2.getStatus())) {
			return false;
		}

		if (this.hasAttribute("version") &&
			Validator.isNotNull(data1.getVersion()) &&
			Validator.isNotNull(data2.getVersion())) {

			return data1.getVersion().equals(data2.getVersion());
		}

		return true;
	}

	public Criterion generateQueryFilter() {
		if (!this.isWorkflowEnabled()) {
			return null;
		}

		return this.generateCriterionFilter(
			"status=" + WorkflowConstants.STATUS_APPROVED);
	}

	public Criterion getCompanyGroupFilter(long companyId) {
		return getCompanyGroupFilter(companyId, 0);
	}

	public Criterion getCompanyGroupFilter(long companyId, long groupId) {
		Conjunction conjunction = RestrictionsFactoryUtil.conjunction();

		if (this.hasAttribute("companyId")) {
			conjunction.add(getProperty("companyId").eq(companyId));
		}

		if (this.hasAttribute("groupId") && (groupId != 0)) {
			conjunction.add(getProperty("groupId").eq(groupId));
		}

		return conjunction;
	}

	public Map<Long, Data> getLiferayData(Criterion filter) throws Exception {

		Map<Long, Data> dataMap = new HashMap<Long, Data>();

		DynamicQuery query = service.newDynamicQuery();

		ProjectionList projectionList =
			this.getPropertyProjection(
				liferayIndexedAttributes.toArray(new String[0]));

		query.setProjection(ProjectionFactoryUtil.distinct(projectionList));

		query.add(filter);

		@SuppressWarnings("unchecked")
		List<Object[]> results = (List<Object[]>)service.executeDynamicQuery(
			query);

		for (Object[] result : results) {
			Data data = createDataObject(result);
			dataMap.put(data.getPrimaryKey(), data);
		}

		return dataMap;
	}

	public List<String> getLiferayIndexedAttributes() {
		return liferayIndexedAttributes;
	}

	public Integer hashCode(Data data) {
		if (Validator.isNotNull(data.getUuid())) {
			return data.getUuid().hashCode();
		}

		return null;
	}

	@Override
	public void init(
			String classPackageName, String classSimpleName, Service service)
		throws Exception {

		super.init(classPackageName, classSimpleName, service);

		this.liferayIndexedAttributes = new ArrayList<String>();

		String primaryKey = this.getPrimaryKeyAttribute();

		this.setIndexPrimaryKey(primaryKey);

		if (Validator.isNull(primaryKey)) {
			throw new RuntimeException("Missing primary key!!");
		}

		if (this.hasAttribute("companyId")) {
			this.addIndexedAttribute("companyId");
		}

		if (this.isAuditedModel()) {
			addIndexedAttributes(auditedModelAttributes);
		}

		if (this.isGroupedModel()) {
			addIndexedAttributes(groupedModelAttributes);
		}

		if (this.isResourcedModel()) {
			addIndexedAttributes(resourcedModelAttributes);
		}

		if (this.isStagedModel()) {
			addIndexedAttributes(stagedModelAttributes);
		}

		if (this.isWorkflowEnabled()) {
			addIndexedAttributes(workflowModelAttributes);
		}

		if (this.hasAttribute("version")) {
			this.addIndexedAttribute("version");
		}

		this.setFilter(this.generateQueryFilter());
	}

	protected static Criterion generateConjunctionQueryFilter(
		Criterion criterion1, Criterion criterion2) {

		if (criterion1 == null) {
			return criterion2;
		}

		Conjunction conjuntion = RestrictionsFactoryUtil.conjunction();
		conjuntion.add(criterion1);
		conjuntion.add(criterion2);
		return conjuntion;
	}

	protected void addIndexedAttribute(String col) {
		if (!liferayIndexedAttributes.contains(col)) {
			liferayIndexedAttributes.add(col);
		}
	}

	protected void addIndexedAttributes(String[] modelAttributes) {

		for (int i = 0; i<modelAttributes.length; i++)
		{
			this.addIndexedAttribute((modelAttributes[i]));
		}
	}

	protected void removeIndexedAttribute(String col) {
		while (liferayIndexedAttributes.contains(col)) {
			liferayIndexedAttributes.remove(col);
		}
	}

	protected void setIndexPrimaryKey(String col) {
		if (liferayIndexedAttributes.contains(col)) {
			liferayIndexedAttributes.remove(col);
		}

		liferayIndexedAttributes.add(0, col);
	}

	private static Log _log = LogFactoryUtil.getLog(StagingCheckerModel.class);

	private List<String> liferayIndexedAttributes = null;

}