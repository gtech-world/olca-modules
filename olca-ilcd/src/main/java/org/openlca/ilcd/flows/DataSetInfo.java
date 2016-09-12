
package org.openlca.ilcd.flows;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import org.openlca.ilcd.commons.FlowCategoryInformation;
import org.openlca.ilcd.commons.FreeText;
import org.openlca.ilcd.commons.Other;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DataSetInformationType", propOrder = {
		"uuid",
		"name",
		"synonyms",
		"classificationInformation",
		"casNumber",
		"sumFormula",
		"generalComment",
		"other"
})
public class DataSetInfo implements Serializable {

	private final static long serialVersionUID = 1L;

	@XmlElement(name = "UUID", namespace = "http://lca.jrc.it/ILCD/Common", required = true)
	public String uuid;

	public FlowName name;

	@XmlElement(namespace = "http://lca.jrc.it/ILCD/Common")
	public final List<FreeText> synonyms = new ArrayList<>();

	public FlowCategoryInformation classificationInformation;

	@XmlElement(name = "CASNumber")
	public String casNumber;

	public String sumFormula;

	@XmlElement(namespace = "http://lca.jrc.it/ILCD/Common")
	public final List<FreeText> generalComment = new ArrayList<>();

	@XmlElement(namespace = "http://lca.jrc.it/ILCD/Common")
	public Other other;

	@XmlAnyAttribute
	public final Map<QName, String> otherAttributes = new HashMap<>();

}
