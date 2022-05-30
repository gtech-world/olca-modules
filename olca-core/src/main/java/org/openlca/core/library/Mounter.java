package org.openlca.core.library;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.openlca.core.database.IDatabase;
import org.openlca.core.database.NativeSql;
import org.openlca.core.model.Category;
import org.openlca.core.model.ModelType;
import org.openlca.jsonld.ZipStore;
import org.openlca.jsonld.input.JsonImport;
import org.openlca.jsonld.input.UpdateMode;
import org.openlca.util.Strings;

import gnu.trove.set.hash.TLongHashSet;
import jakarta.persistence.Table;

/**
 * Mounts a library and its dependencies on a database.
 */
record Mounter(IDatabase db, Library library) implements Runnable {

	@Override
	public void run() {
		try {
			for (var lib : Libraries.dependencyOrderOf(library)) {
				var libId = lib.id();
				var meta = new File(lib.folder(), "meta.zip");
				try (var zip = ZipStore.open(meta)) {
					new JsonImport(zip, db)
						.setUpdateMode(UpdateMode.ALWAYS)
						.run();
					for (var type : ModelType.values()) {
						if (!type.isRoot())
							continue;
						var refIds = zip.getRefIds(type);
						if (refIds.isEmpty())
							continue;
						tag(libId, type, new HashSet<>(refIds));
					}
				}
				db.addLibrary(libId);
				new CategoryTagger(db, libId).run();
			}
		} catch (Exception e) {
			throw new RuntimeException("failed to import library", e);
		}
	}

	private void tag(String libId, ModelType type, Set<String> refIds) {
		var clazz = type.getModelClass();
		if (clazz == null)
			return;
		var table = clazz.getAnnotation(Table.class);
		var sql = "select ref_id, library from " + table.name();
		NativeSql.on(db).updateRows(sql, r -> {
			var id = r.getString(1);
			if (refIds.contains(id)) {
				r.updateString(2, libId);
				r.updateRow();
			}
			return true;
		});
	}

	/**
	 * Tags categories which are only used in a specific library with the ID of
	 * that library.
	 */
	private record CategoryTagger(IDatabase db, String libId)
		implements Runnable {

		@Override
		public void run() {

			// collect the IDs of categories that are only used by the library and
			// the IDs of categories that are also used by non-library data sets
			var libCategories = new TLongHashSet();
			var nonLibCategories = new TLongHashSet();
			for (var type : ModelType.values()) {
				if (!type.isRoot())
					continue;
				var table = type.getModelClass().getAnnotation(Table.class);
				if (table == null)
					continue;
				var query = "select distinct f_category, library from " + table.name();
				NativeSql.on(db).query(query, r -> {
					var id = r.getLong(1);
					if (id == 0)
						return true;
					var qLib = r.getString(2);

					if (Strings.nullOrEqual(qLib, libId)) {
						if (!nonLibCategories.contains(id)) {
							libCategories.add(id);
						}
					} else {
						if (libCategories.contains(id)) {
							libCategories.remove(id);
							nonLibCategories.add(id);
						}
					}
					return true;
				});
			}

			// tag the categories and their parents
			for (var it = libCategories.iterator(); it.hasNext(); ) {
				var libCat = db.get(Category.class, it.next());
				while (libCat != null && !nonLibCategories.contains(libCat.id)) {
					libCat.library = libId;
					libCat = db.update(libCat).category;
				}
			}
		}
	}
}
