package com.mapr.synth.constraint;

import com.mapr.synth.samplers.ComparableField;
import com.mapr.synth.samplers.FieldSampler;

public class GreaterThanConstraint<T extends FieldSampler & ComparableField> extends Constraint<T> {
	
	private String oldMin;
	
	@Override
    public void applyConstraint() {
		
		oldMin = referenceField.getMinAsString();
		if(inRelationshipWith.compareTo(oldMin) > 0) {
			referenceField.setMinAsString(inRelationshipWith.getLastSampledAsString(), true);
		}
	}	
	
	public void resetConstraint() {
		
		referenceField.setMinAsString(oldMin, false);		
	}

}
