package org.openlca.core.database.usage;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openlca.core.Tests;
import org.openlca.core.database.CurrencyDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.database.ProcessDao;
import org.openlca.core.model.Currency;
import org.openlca.core.model.Exchange;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.Process;
import org.openlca.core.model.descriptors.RootDescriptor;
import org.openlca.core.model.descriptors.CurrencyDescriptor;
import org.openlca.core.model.descriptors.Descriptor;

public class CurrencyUseSearchTest {

	private IDatabase database = Tests.getDb();
	private IUseSearch<CurrencyDescriptor> search;

	@Before
	public void setup() {
		search = IUseSearch.FACTORY.createFor(ModelType.CURRENCY, database);
	}

	@Test
	public void testFindNoUsage() {
		Currency currency = createCurrency();
		List<RootDescriptor> models = search.findUses(Descriptor
				.of(currency));
		Assert.assertNotNull(models);
		Assert.assertTrue(models.isEmpty());
		new CurrencyDao(database).delete(currency);
	}

	@Test
	public void testFindInCurrency() {
		Currency currency = createCurrency();
		Currency other = createCurrency();
		other.referenceCurrency = currency;
		new CurrencyDao(database).update(other);
		List<RootDescriptor> results = search.findUses(Descriptor
				.of(currency));
		new CurrencyDao(database).delete(currency);
		new CurrencyDao(database).delete(other);
		Descriptor expected = Descriptor.of(other);
		Assert.assertEquals(1, results.size());
		Assert.assertEquals(expected, results.get(0));
	}

	@Test
	public void testFindInExchanges() {
		Currency currency = createCurrency();
		Process process = createProcess(currency);
		List<RootDescriptor> results = search.findUses(Descriptor
				.of(currency));
		new ProcessDao(database).delete(process);
		new CurrencyDao(database).delete(currency);
		Descriptor expected = Descriptor.of(process);
		Assert.assertEquals(1, results.size());
		Assert.assertEquals(expected, results.get(0));
	}

	private Currency createCurrency() {
		Currency currency = new Currency();
		currency.name = "currency";
		new CurrencyDao(database).insert(currency);
		return currency;
	}

	private Process createProcess(Currency currency) {
		Process process = new Process();
		process.name = "process";
		Exchange exchange = new Exchange();
		exchange.currency = currency;
		process.exchanges.add(exchange);
		new ProcessDao(database).insert(process);
		return process;
	}
}
