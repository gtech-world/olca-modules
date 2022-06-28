package org.openlca.core.library;

import org.apache.commons.csv.CSVRecord;
import org.openlca.core.matrix.index.EnviFlow;
import org.openlca.core.model.Flow;

import java.util.List;

/**
 * An item of a library's intervention index.
 */
public record LibEnviItem(
	int index,
	LibFlow flow,
	LibLocation location,
	boolean isInput
) {

	public static LibEnviItem of(int idx, EnviFlow item, DbContext ctx) {
		return new LibEnviItem(
			idx,
			LibFlow.of(item.flow(), ctx),
			LibLocation.of(item.location()),
			item.isInput());
	}

	public static LibEnviItem output(int idx, Flow flow) {
		return new LibEnviItem(idx, LibFlow.of(flow), null, false);
	}

	public static LibEnviItem input(int idx, Flow flow) {
		return new LibEnviItem(idx, LibFlow.of(flow), null, true);
	}

	Proto.ElemFlowEntry toProto() {
		var proto = Proto.ElemFlowEntry.newBuilder()
			.setIndex(index)
			.setIsInput(isInput);
		if (flow != null) {
			proto.setFlow(flow.toProto());
		}
		if (location != null) {
			proto.setLocation(location.toProto());
		}
		return proto.build();
	}

	static LibEnviItem fromProto(Proto.ElemFlowEntry proto) {
		return new LibEnviItem(
			proto.getIndex(),
			proto.hasFlow()
				? LibFlow.fromProto(proto.getFlow())
				: LibFlow.empty(),
			proto.hasLocation()
				? LibLocation.fromProto(proto.getLocation())
				: LibLocation.empty(),
			proto.getIsInput());
	}

	void toCsv(List<String> buffer) {
		buffer.add(Integer.toString(index));
		if (flow == null) {
			LibFlow.empty().toCsv(buffer);
		} else {
			flow.toCsv(buffer);
		}
		if (location == null) {
			LibLocation.empty().toCsv(buffer);
		} else {
			location.toCsv(buffer);
		}
	}

	static LibEnviItem fromCsv(CSVRecord row) {
		return new LibEnviItem(
			Csv.readInt(row, 0),
			LibFlow.fromCsv(row, 1),
			LibLocation.fromCsv(row, 1 + Csv.FLOW_COLS),
			Csv.readBool(row, 1 + Csv.FLOW_COLS + Csv.LOCATION_COLS));
	}

}
