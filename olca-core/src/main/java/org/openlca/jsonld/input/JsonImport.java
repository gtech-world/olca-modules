package org.openlca.jsonld.input;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import org.openlca.core.database.IDatabase;
import org.openlca.core.io.CategorySync;
import org.openlca.core.io.EntityResolver;
import org.openlca.core.io.ExchangeProviderQueue;
import org.openlca.core.model.Category;
import org.openlca.core.model.Exchange;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.RefEntity;
import org.openlca.core.model.RootEntity;
import org.openlca.jsonld.JsonStoreReader;
import org.openlca.jsonld.upgrades.Upgrades;

public class JsonImport implements Runnable, EntityResolver {

	private final IDatabase db;
	final JsonStoreReader reader;
	UpdateMode updateMode = UpdateMode.NEVER;
	private Consumer<RefEntity> callback;
	final CategorySync categories;

	private final ExchangeProviderQueue providers;
	private final Map<Class<?>, ModelType> types = new HashMap<>();
	private final Map<ModelType, Set<String>> visited = new HashMap<>();

	public JsonImport(JsonStoreReader reader, IDatabase db) {
		this.db = db;
		this.reader = Upgrades.chain(reader);
		this.providers = ExchangeProviderQueue.create(db);
		this.categories = CategorySync.of(db);
		for (var type : ModelType.values()) {
			if (type.isRoot()) {
				types.put(type.getModelClass(), type);
			}
		}
	}

	public JsonImport setUpdateMode(UpdateMode updateMode) {
		this.updateMode = updateMode;
		return this;
	}

	public JsonImport setCallback(Consumer<RefEntity> callback) {
		this.callback = callback;
		return this;
	}

	void visited(ModelType type, String refId) {
		var set = visited.computeIfAbsent(type, k -> new HashSet<>());
		set.add(refId);
	}

	public ExchangeProviderQueue providers() {
		return providers;
	}

	void imported(RefEntity entity) {
		if (callback == null)
			return;
		callback.accept(entity);
	}

	boolean hasVisited(ModelType type, String refId) {
		Set<String> set = visited.get(type);
		return set != null && set.contains(refId);
	}

	public void run(ModelType type, String id) {
		if (type == null || id == null)
			return;
		switch (type) {
			case ACTOR -> ActorImport.run(id, this);
			case CATEGORY -> CategoryImport.run(id, this);
			case CURRENCY -> CurrencyImport.run(id, this);
			case DQ_SYSTEM -> DQSystemImport.run(id, this);
			case EPD -> EpdImport.run(id, this);
			case FLOW -> FlowImport.run(id, this);
			case FLOW_PROPERTY -> FlowPropertyImport.run(id, this);
			case IMPACT_CATEGORY -> ImpactCategoryImport.run(id, this);
			case IMPACT_METHOD -> ImpactMethodImport.run(id, this);
			case LOCATION -> LocationImport.run(id, this);
			case PARAMETER -> ParameterImport.run(id, this);
			case PROCESS -> ProcessImport.run(id, this);
			case PRODUCT_SYSTEM -> ProductSystemImport.run(id, this);
			case PROJECT -> ProjectImport.run(id, this);
			case RESULT -> ResultImport.run(id, this);
			case SOCIAL_INDICATOR -> SocialIndicatorImport.run(id, this);
			case SOURCE -> SourceImport.run(id, this);
			case UNIT_GROUP -> UnitGroupImport.run(id, this);
			default -> {
			}
		}
	}

	@Override
	public void run() {
		var typeOrder = new ModelType[]{
			ModelType.ACTOR,
			ModelType.CATEGORY,
			ModelType.CURRENCY,
			ModelType.DQ_SYSTEM,
			ModelType.EPD,
			ModelType.FLOW,
			ModelType.FLOW_PROPERTY,
			ModelType.IMPACT_CATEGORY,
			ModelType.IMPACT_METHOD,
			ModelType.LOCATION,
			ModelType.PARAMETER,
			ModelType.PROCESS,
			ModelType.PRODUCT_SYSTEM,
			ModelType.PROJECT,
			ModelType.RESULT,
			ModelType.SOCIAL_INDICATOR,
			ModelType.SOURCE,
			ModelType.UNIT_GROUP,
		};
		for (var type : typeOrder) {
			for (var id : reader.getRefIds(type)) {
				run(type, id);
			}
		}
	}


	@Override
	public <T extends RootEntity> T get(Class<T> type, String refId) {
		// TODO: for small objects that are often used, we could
		// maintain a cache here
		var modelType = types.get(type);
		if (modelType == null)
			return null;
		T model = db.get(type, refId);
		if (model != null) {
			if (updateMode == UpdateMode.NEVER || hasVisited(modelType, refId))
				return model;
		}
		var json = reader.get(modelType, refId);
		if (json == null)
			return model;
		if (skipImport(model, json)) {
			visited(modelType, refId);
			return model;
		}

		var reader = (EntityReader<T>) readerFor(modelType);



		return null;
	}

	private <T extends RefEntity> boolean skipImport(T model, JsonObject json) {
		if (model == null || updateMode == UpdateMode.ALWAYS)
			return false;
		if (!(model instanceof RootEntity root))
			return false;
		long jsonVersion = In.getVersion(json);
		if (jsonVersion != root.version)
			return jsonVersion < root.version;
		long jsonDate = In.getLastChange(json);
		return jsonDate <= root.lastChange;
	}

	@Override
	public Category getCategory(ModelType type, String path) {
		return categories.get(type, path);
	}

	@Override
	public void resolveProvider(String providerId, Exchange exchange) {
		providers.add(providerId, exchange);
	}

	private EntityReader<?> readerFor(ModelType type) {
		// TODO EPD, Result, ImpactMethod ...
		return switch (type) {
			case ACTOR -> new ActorReader(this);
			case CURRENCY -> new CurrencyReader(this);
			case DQ_SYSTEM -> new DQSystemReader(this);
			case FLOW -> new FlowReader(this);
			case FLOW_PROPERTY -> new FlowPropertyReader(this);
			case IMPACT_CATEGORY -> new ImpactCategoryReader(this);
			default -> null;
		};
	}
}
