package org.openlca.io.ecospold2;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.openlca.core.database.BaseDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.model.Category;
import org.openlca.core.model.Flow;
import org.openlca.core.model.FlowProperty;
import org.openlca.core.model.FlowPropertyFactor;
import org.openlca.core.model.FlowType;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.Unit;
import org.openlca.ecospold2.ElementaryExchange;
import org.openlca.ecospold2.Exchange;
import org.openlca.ecospold2.IntermediateExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FlowHandler {

	private Logger log = LoggerFactory.getLogger(getClass());
	private IDatabase database;
	private Map<String, Flow> cachedFlows = new HashMap<>();
	private Map<String, Unit> unitMap = new HashMap<>();
	private Map<String, FlowProperty> propertyMap = new HashMap<>();
	private Map<String, Category> flowCategories = new HashMap<>();

	public FlowHandler(IDatabase database) {
		this.database = database;
		try {
			loadMaps();
		} catch (Exception e) {
			log.error("Failed to load unit - maps", e);
		}
	}

	private void loadMaps() throws Exception {
		InputStream is = getClass().getResourceAsStream("ei3_unit_map.csv");
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line = null;
		while ((line = reader.readLine()) != null) {
			String[] args = line.split(",");
			String eiUnit = args[0];
			Unit unit = database.createDao(Unit.class).getForId(args[1]);
			FlowProperty prop = database.createDao(FlowProperty.class)
					.getForId(args[2]);
			if (unit != null && prop != null) {
				unitMap.put(eiUnit, unit);
				propertyMap.put(eiUnit, prop);
			}
		}
	}

	public Unit getUnit(String id) {
		return unitMap.get(id);
	}

	public Flow getFlow(IntermediateExchange exchange) {
		Flow dbFlow = getDbFlow(exchange.getIntermediateExchangeId());
		if (dbFlow != null)
			return dbFlow;
		return createProduct(exchange);
	}

	public Flow getFlow(ElementaryExchange exchange) {
		Flow dbFlow = getDbFlow(exchange.getElementaryExchangeId());
		if (dbFlow != null)
			return dbFlow;
		return createElemFlow(exchange);
	}

	private Flow getDbFlow(String flowId) {
		if (flowId == null)
			return null;
		Flow flow = cachedFlows.get(flowId);
		if (flow != null)
			return flow;
		return getFromDb(flowId);
	}

	private Flow getFromDb(String flowId) {
		try {
			Flow flow = database.createDao(Flow.class).getForId(flowId);
			cachedFlows.put(flowId, flow);
			return flow;
		} catch (Exception e) {
			log.error("Failed to load flow from DB, id=" + flowId, e);
			return null;
		}
	}

	private Flow createProduct(IntermediateExchange exchange) {
		Flow flow = new Flow();
		flow.setId(exchange.getIntermediateExchangeId());
		flow.setFlowType(FlowType.PRODUCT_FLOW);
		fillCacheFlow(exchange, flow);
		return flow;
	}

	private Flow createElemFlow(ElementaryExchange exchange) {
		Flow flow = new Flow();
		flow.setId(exchange.getElementaryExchangeId());
		flow.setFlowType(FlowType.ELEMENTARY_FLOW);
		fillCacheFlow(exchange, flow);
		return flow;
	}

	private void fillCacheFlow(Exchange exchange, Flow flow) {
		flow.setName(exchange.getName());
		FlowProperty prop = propertyMap.get(exchange.getUnitId());
		if (prop == null) {
			log.warn("unknown unit {}", exchange.getUnitId());
			return;
		}
		FlowPropertyFactor fac = new FlowPropertyFactor();
		fac.setId(UUID.randomUUID().toString());
		fac.setFlowProperty(prop);
		fac.setConversionFactor(1.0);
		flow.getFlowPropertyFactors().add(fac);
		flow.setReferenceFlowProperty(prop);
		try {
			setCategory(flow);
			database.createDao(Flow.class).insert(flow);
			cachedFlows.put(flow.getId(), flow);
		} catch (Exception e) {
			log.error("Failed to store flow", e);
		}
	}

	private void setCategory(Flow flow) throws Exception {
		String pref = flow.getName().substring(0, 1).toLowerCase();
		Category cat = flowCategories.get(pref);
		if (cat == null) {
			cat = new Category(UUID.randomUUID().toString(), pref,
					ModelType.FLOW);
			BaseDao<Category> dao = database.createDao(Category.class);
			Category parent = dao.getForId(Flow.class.getCanonicalName());
			parent.add(cat);
			cat.setParentCategory(parent);
			dao.update(parent);
			flowCategories.put(pref, cat);
		}
		flow.setCategory(cat);
	}

}