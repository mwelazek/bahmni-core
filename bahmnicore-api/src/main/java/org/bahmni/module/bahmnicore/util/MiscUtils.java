package org.bahmni.module.bahmnicore.util;

import org.apache.commons.collections.CollectionUtils;
import org.openmrs.Concept;
import org.openmrs.api.ConceptService;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class MiscUtils {	
    public static List<Concept> getConceptsForNames(List<String> conceptNames, ConceptService conceptService) {
        //Returning null for the sake of UTs
        if (CollectionUtils.isNotEmpty(conceptNames)) {
            List<Concept> rootConcepts = new ArrayList<>();
            for (String rootConceptName : conceptNames) {
                Concept concept = conceptService.getConceptByName(rootConceptName);
                if (concept != null) {
                    rootConcepts.add(concept);
                }
            }
            return rootConcepts;
        }
        return new ArrayList<>();
    }

    public static void setUuidsForObservations(Collection<BahmniObservation> bahmniObservations) {
        for (BahmniObservation bahmniObservation : bahmniObservations) {
            if (org.apache.commons.lang3.StringUtils.isBlank(bahmniObservation.getUuid())) {
                bahmniObservation.setUuid(UUID.randomUUID().toString());
            }
        }
    }
	
    public static BahmniObservation getFollowUpDateObservation(Collection<BahmniObservation> bahmniObservations) {
		BahmniObservation observation = null;
        for (BahmniObservation bahmniObservation : bahmniObservations) {
			for(BahmniObservation groupMember : bahmniObservation.getGroupMembers()) {
				for(BahmniObservation groupMember2 : groupMember.getGroupMembers()) {
					if(groupMember2.getConceptUuid().equals("88489023-783b-4021-b7a9-05ca9877bf67")) {
						if(groupMember2.getValue() != null){
							observation = groupMember2;
						}
					}				
				}
			}
        }
		return observation;
    }
}
