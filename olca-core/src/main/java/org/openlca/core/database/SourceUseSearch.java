package org.openlca.core.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManagerFactory;

import org.openlca.core.model.Source;
import org.openlca.core.model.descriptors.BaseDescriptor;
import org.openlca.core.model.descriptors.ProcessDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Search for entities where a given source is used. */
class SourceUseSearch implements IUseSearch<Source> {

	private Logger log = LoggerFactory.getLogger(getClass());
	private EntityManagerFactory emf;

	public SourceUseSearch(EntityManagerFactory emf) {
		this.emf = emf;
	}

	@Override
	public List<BaseDescriptor> findUses(Source source) {
		String jpql = "select p.id, p.name, p.description, loc.code "
				+ " from Process p left join p.location loc "
				+ " left join p.modelingAndValidation mav "
				+ " left join p.adminInfo info left join mav.sources s"
				+ " where info.publication = :source or s = :source";
		try {
			List<Object[]> results = Query.on(emf).getAll(Object[].class, jpql,
					Collections.singletonMap("source", source));
			List<BaseDescriptor> descriptors = new ArrayList<>();
			for (Object[] result : results) {
				ProcessDescriptor d = new ProcessDescriptor();
				d.setId((Long) result[0]);
				d.setName((String) result[1]);
				d.setDescription((String) result[2]);
				d.setLocationCode((String) result[3]);
				descriptors.add(d);
			}
			return descriptors;
		} catch (Exception e) {
			log.error("Failed to search for source use in processes", e);
			return Collections.emptyList();
		}
	}

}
