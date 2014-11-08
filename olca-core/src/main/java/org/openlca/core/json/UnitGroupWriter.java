package org.openlca.core.json;

import java.lang.reflect.Type;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.openlca.core.model.Unit;
import org.openlca.core.model.UnitGroup;

class UnitGroupWriter implements JsonSerializer<UnitGroup> {

	@Override
	public JsonElement serialize(UnitGroup unitGroup, Type type,
			JsonSerializationContext context) {
		JsonObject obj = new JsonObject();
		JsonWriter.addContext(obj);
		map(unitGroup, obj);
		return obj;
	}

	static void map(UnitGroup group, JsonObject obj) {
		if (group == null || obj == null)
			return;
		JsonWriter.addAttributes(group, obj);
		JsonObject propRef = JsonWriter.createReference(group
				.getDefaultFlowProperty());
		obj.add("defaultFlowProperty", propRef);
		JsonObject unitRef = JsonWriter.createReference(group
				.getReferenceUnit());
		obj.add("referenceUnit", unitRef);
		JsonArray units = new JsonArray();
		for (Unit unit : group.getUnits()) {
			JsonObject unitObj = new JsonObject();
			UnitWriter.map(unit, unitObj);
			units.add(unitObj);
		}
		obj.add("units", units);
	}

}
