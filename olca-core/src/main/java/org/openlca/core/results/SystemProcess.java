package org.openlca.core.results;

import java.util.Date;
import java.util.UUID;

import org.openlca.core.database.FlowDao;
import org.openlca.core.database.IDatabase;
import org.openlca.core.math.CalculationSetup;
import org.openlca.core.model.Exchange;
import org.openlca.core.model.Flow;
import org.openlca.core.model.FlowProperty;
import org.openlca.core.model.Process;
import org.openlca.core.model.ProcessType;
import org.openlca.core.model.SocialAspect;
import org.openlca.core.model.descriptors.FlowDescriptor;

public class SystemProcess {

	public static Process create(IDatabase database, CalculationSetup setup, SimpleResultProvider<?> result,
			String name) {
		return new SystemProcess(database, setup, result, name).create(false);
	}

	public static Process createWithMetaData(IDatabase database, CalculationSetup setup,
			SimpleResultProvider<?> result, String name) {
		return new SystemProcess(database, setup, result, name).create(true);
	}

	private final FlowDao flowDao;
	private final CalculationSetup setup;
	private final SimpleResultProvider<?> result;
	private final String name;

	private SystemProcess(IDatabase database, CalculationSetup setup, SimpleResultProvider<?> result, String name) {
		this.flowDao = new FlowDao(database);
		this.setup = setup;
		this.result = result;
		this.name = name;
	}

	private Process create(boolean withMetaData) {
		Process p = new Process();
		p.setName(name);
		p.setRefId(UUID.randomUUID().toString());
		p.setProcessType(ProcessType.LCI_RESULT);
		addRefFlow(p);
		addElemFlows(p);
		if (withMetaData)
			copyMetaData(p);
		return p;
	}

	private void addRefFlow(Process p) {
		Flow product = getProduct();
		FlowProperty property = setup.getFlowPropertyFactor().getFlowProperty();
		Exchange qRef = p.exchange(product, property, setup.getUnit());
		qRef.amount = setup.getAmount();
		p.setQuantitativeReference(qRef);
	}

	private Flow getProduct() {
		if (setup == null || setup.productSystem == null)
			return null;
		Exchange ref = setup.productSystem.referenceExchange;
		return ref == null ? null : ref.flow;
	}

	private void addElemFlows(Process p) {
		for (FlowDescriptor d : result.getFlowDescriptors()) {
			FlowResult flowResult = result.getTotalFlowResult(d);
			if (flowResult == null || flowResult.value == 0)
				continue;
			Flow flow = flowDao.getForId(d.getId());
			if (flow == null)
				continue;
			Exchange e = p.exchange(flow);
			e.isInput = flowResult.input;
			e.amount = flowResult.value;
		}
	}

	private void copyMetaData(Process p) {
		Process refProc = setup.productSystem.referenceProcess;
		if (refProc == null)
			return;
		for (SocialAspect sa : refProc.socialAspects)
			p.socialAspects.add(sa.clone());
		p.socialDqSystem = refProc.socialDqSystem;
		p.setCategory(refProc.getCategory());
		p.setDefaultAllocationMethod(refProc.getDefaultAllocationMethod());
		p.setDescription(refProc.getDescription());
		if (refProc.getDocumentation() != null)
			p.setDocumentation(refProc.getDocumentation().clone());
		p.setInfrastructureProcess(refProc.isInfrastructureProcess());
		p.setLastChange(new Date().getTime());
		p.setLocation(refProc.getLocation());
	}
}