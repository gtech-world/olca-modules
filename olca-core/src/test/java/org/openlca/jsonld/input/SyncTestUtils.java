package org.openlca.jsonld.input;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import org.openlca.core.database.IDatabase;
import org.openlca.jsonld.ZipStore;
import org.openlca.validation.Validation;

import static org.junit.Assert.assertTrue;

class SyncTestUtils {

	static File copyToTemp(String filename) {
		try {
			var tmp = Files.createTempFile("olca-sync-test", ".zip");
			var resource = Objects.requireNonNull(
				SyncTestUtils.class.getResourceAsStream(filename));
			Files.copy(resource, tmp, StandardCopyOption.REPLACE_EXISTING);
			return tmp.toFile();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static void doImport(File file, IDatabase db) {
		try {
			ZipStore store = ZipStore.open(file);
			JsonImport json = new JsonImport(store, db);
			json.setUpdateMode(UpdateMode.ALWAYS);
			json.run();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static void validate(IDatabase db) {
		var validation = Validation.on(db)
			.skipInfos(true)
			.skipWarnings(true);
		validation.run();
		assertTrue(validation.items().isEmpty());
	}

	static void delete(File file) {
		if (file == null || !file.exists())
			return;
		try {
			Files.delete(file.toPath());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
