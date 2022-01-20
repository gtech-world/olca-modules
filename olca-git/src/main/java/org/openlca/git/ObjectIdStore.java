package org.openlca.git;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.openlca.core.model.CategorizedEntity;
import org.openlca.core.model.Category;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.descriptors.CategorizedDescriptor;
import org.openlca.core.model.descriptors.CategoryDescriptor;
import org.openlca.util.Categories;
import org.openlca.util.Categories.PathBuilder;

public class ObjectIdStore {

	private final File file;
	private final boolean asProto;
	private Map<String, byte[]> store = new HashMap<>();

	private ObjectIdStore(File storeFile, boolean asProto) {
		this.file = storeFile;
		this.asProto = asProto;
	}

	public static ObjectIdStore openProto(File storeFile) throws IOException {
		var store = new ObjectIdStore(storeFile, true);
		store.load();
		return store;
	}

	public static ObjectIdStore openJson(File storeFile) throws IOException {
		var store = new ObjectIdStore(storeFile, false);
		store.load();
		return store;
	}

	@SuppressWarnings("unchecked")
	private void load() throws IOException {
		if (!file.exists())
			return;
		try (var fis = new FileInputStream(file);
				var ois = new ObjectInputStream(fis)) {
			store = (HashMap<String, byte[]>) ois.readObject();
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}
	}

	public void save() throws IOException {
		if (!file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}
		try (var fos = new FileOutputStream(file);
				var oos = new ObjectOutputStream(fos)) {
			oos.writeObject(store);
		}
	}

	public boolean has(ModelType type) {
		var path = getPath(type, null, null);
		return has(path);
	}

	public boolean has(CategorizedEntity e) {
		var path = getPath(e);
		return has(path);
	}

	public boolean has(PathBuilder categoryPath, CategorizedDescriptor d) {
		var path = getPath(categoryPath, d);
		return has(path);
	}

	public boolean has(String path) {
		return store.containsKey(path);
	}

	public byte[] getRawRoot() {
		return getRaw("");
	}

	public byte[] getRaw(ModelType type) {
		var path = getPath(type);
		return getRaw(path);
	}

	public byte[] getRaw(CategorizedEntity e) {
		var path = getPath(e);
		return getRaw(path);
	}

	public byte[] getRaw(PathBuilder categoryPath, CategorizedDescriptor d) {
		var path = getPath(categoryPath, d);
		return getRaw(path);
	}

	public byte[] getRaw(String path) {
		var v = store.get(path);
		if (v == null)
			return getBytes(ObjectId.zeroId());
		return v;
	}

	public ObjectId getRoot() {
		return get("");
	}

	public ObjectId get(ModelType type) {
		var path = getPath(type);
		return get(path);
	}

	public ObjectId get(CategorizedEntity e) {
		var path = getPath(e);
		return get(path);
	}

	public ObjectId get(PathBuilder categoryPath, CategorizedDescriptor d) {
		var path = getPath(categoryPath, d);
		return get(path);
	}

	public ObjectId get(String path) {
		var id = store.get(path);
		if (id == null)
			return ObjectId.zeroId();
		return ObjectId.fromRaw(id);
	}

	public void putRoot(ObjectId id) {
		put("", id);
	}

	public void put(ModelType type, ObjectId id) {
		var path = getPath(type);
		put(path, id);
	}

	public void put(CategorizedEntity e, ObjectId id) {
		var path = getPath(e);
		put(path, id);
	}

	public void put(PathBuilder categoryPath, CategorizedDescriptor d, ObjectId id) {
		var path = getPath(categoryPath, d);
		put(path, id);
	}

	public void put(String path, ObjectId id) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		store.put(path, getBytes(id));
	}
	
	public void invalidateRoot() {
		invalidate("");
	}

	public void invalidate(ModelType type) {
		var path = getPath(type);
		invalidate(path);
	}
	
	public void invalidate(CategorizedEntity e) {
		var path = getPath(e);
		invalidate(path);
	}

	public void invalidate(PathBuilder categoryPath, CategorizedDescriptor d) {
		var path = getPath(categoryPath, d);
		invalidate(path);
	}

	public void invalidate(String path) {
		var split = path.split("/");
		for (var i = 0; i < split.length; i++) {
			var k = "";
			for (var j = 0; j <= i; j++) {
				k += split[j];
				if (j < i) {
					k += "/";
				}
			}
			store.remove(k);
		}
		store.remove("");
	}

	public String getPath(ModelType type) {
		return getPath(type, null, null);
	}

	public String getPath(CategorizedEntity e) {
		var path = Categories.path(e.category).stream().collect(Collectors.joining("/"));
		if (e instanceof Category)
			return getPath(((Category) e).modelType, path, e.name);
		return getPath(ModelType.forModelClass(e.getClass()), path, e.refId + (asProto ? ".proto" : ".json"));
	}

	public String getPath(PathBuilder categoryPath, CategorizedDescriptor d) {
		var path = categoryPath.pathOf(d.category);
		if (d.type == ModelType.CATEGORY)
			return getPath(((CategoryDescriptor) d).categoryType, path, d.name);
		return getPath(d.type, path, d.refId + (asProto ? ".proto" : ".json"));
	}

	private String getPath(ModelType type, String path, String name) {
		var fullPath = type.name();
		if (path != null && !path.isBlank()) {
			if (!path.startsWith("/")) {
				fullPath += "/";
			}
			fullPath += path;
		}
		if (name != null && !name.isBlank()) {
			fullPath += "/" + name;
		}
		return fullPath;
	}

	private byte[] getBytes(ObjectId id) {
		var bytes = new byte[40];
		id.copyRawTo(bytes, 0);
		return bytes;
	}

}