package org.openlca.io.olca;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openlca.core.database.CategoryDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.ProcessDao;
import org.openlca.core.database.SocialIndicatorDao;
import org.openlca.core.database.Derby;
import org.openlca.core.model.Location;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.Process;
import org.openlca.core.model.SocialAspect;
import org.openlca.core.model.SocialIndicator;

/**
 * We ignore these tests by default because they take quite some time.
 */
@Ignore
public class TestDatabaseImport {

	private IDatabase source;
	private IDatabase target;

	@Before
	public void setup() {
		source = Derby.createInMemory();
		target = Derby.createInMemory();
	}

	@After
	public void cleanup() throws Exception {
		source.close();
		target.close();
	}

	@Test
	public void testCopySocialAspects() {
		var indicator = new SocialIndicator();
		indicator.refId = "si";
		source.insert(indicator);

		var process = new Process();
		process.refId = "pr";
		var aspect = SocialAspect.of(process, indicator);
		source.insert(process);

		new DatabaseImport(source, target).run();
		indicator = target.get(SocialIndicator.class, "si");
		assertNotNull(indicator);
		process = target.get(Process.class, "pr");
		assertEquals(1, process.socialAspects.size());
		aspect = process.socialAspects.get(0);
		assertNotNull(aspect.indicator);
		assertEquals("si", aspect.indicator.refId);
	}

	@Test
	public void testCopyLocations() {
		var location = Location.of("US");
		location.category = CategoryDao.sync(
				source, ModelType.LOCATION, "some", "countries");
		source.insert(location);
		new DatabaseImport(source, target).run();
		location = target.get(Location.class, location.refId);
		assertEquals("some/countries", location.category.toPath());
	}

}