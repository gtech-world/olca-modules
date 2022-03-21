package org.openlca.jsonld.input;

import org.openlca.core.model.ModelType;
import org.openlca.core.model.ParameterRedefSet;
import org.openlca.core.model.ProductSystem;
import org.openlca.core.model.RefEntity;
import org.openlca.jsonld.Json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ProductSystemImport extends BaseImport<ProductSystem> {

	private ProductSystemImport(String refId, JsonImport conf) {
		super(ModelType.PRODUCT_SYSTEM, refId, conf);
	}

	static ProductSystem run(String refId, JsonImport conf) {
		return new ProductSystemImport(refId, conf).run();
	}

	@Override
	ProductSystem map(JsonObject json, long id) {
		if (json == null)
			return null;
		var s = new ProductSystem();
		In.mapAtts(json, s, id, conf);
		String processRefId = Json.getRefId(json, "refProcess");
		if (processRefId != null) {
			s.referenceProcess = ProcessImport.run(processRefId, conf);
		}

		s.targetAmount = Json.getDouble(json, "targetAmount", 1d);
		addProcesses(json, s);
		addParameterSets(json, s);
		importLinkRefs(json, s);
		ProductSystemLinks.map(json, conf, s);
		return conf.db.put(s);
	}

	private void addProcesses(JsonObject json, ProductSystem s) {
		JsonArray array = Json.getArray(json, "processes");
		if (array == null || array.size() == 0)
			return;
		for (JsonElement e : array) {
			if (e.isJsonObject()) {
				addProcess(s, e.getAsJsonObject());
			}
		}
	}

	private void addProcess(ProductSystem s, JsonObject ref) {
		if (ref == null)
			return;
		String refId = Json.getString(ref, "@id");
		String type = Json.getString(ref, "@type");
		RefEntity p;
		if ("ProductSystem".equals(type)) {
			p = ProductSystemImport.run(refId, conf);
		}else if ("Result".equals(type)) {
			p = ResultImport.run(refId, conf);
		} else {
			p = ProcessImport.run(refId, conf);
		}
		if (p != null) {
			s.processes.add(p.id);
		}
	}

	private void importLinkRefs(JsonObject json, ProductSystem s) {
		JsonArray array = Json.getArray(json, "processLinks");
		if (array == null || array.size() == 0)
			return;
		for (JsonElement element : array) {
			JsonObject obj = element.getAsJsonObject();
			String flowRefId = Json.getRefId(obj, "flow");
			FlowImport.run(flowRefId, conf);
			addProcess(s, Json.getObject(obj, "provider"));
			addProcess(s, Json.getObject(obj, "process"));
		}
	}

	private void addParameterSets(JsonObject json, ProductSystem sys) {
		var array = Json.getArray(json, "parameterSets");
		if (array == null || array.size() == 0)
			return;
		for (JsonElement elem : array) {
			if (!elem.isJsonObject())
				continue;
			var obj = elem.getAsJsonObject();
			var set = new ParameterRedefSet();
			sys.parameterSets.add(set);
			set.name = Json.getString(obj, "name");
			set.description = Json.getString(obj, "description");
			set.isBaseline = Json.getBool(obj, "isBaseline", false);
			JsonArray redefs = Json.getArray(obj, "parameters");
			if (redefs != null && redefs.size() > 0) {
				set.parameters.addAll(
						ParameterRedefs.read(redefs, conf));
			}
		}
	}
}
