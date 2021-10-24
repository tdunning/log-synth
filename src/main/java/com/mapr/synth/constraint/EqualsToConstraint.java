package com.mapr.synth.constraint;

import java.text.ParseException;

import com.mapr.synth.FancyTimeFormatter;
import com.mapr.synth.samplers.ComparableField;
import com.mapr.synth.samplers.DateSampler;
import com.mapr.synth.samplers.FieldSampler;
import com.mapr.synth.samplers.IntegerSampler;

public class EqualsToConstraint<T extends FieldSampler & ComparableField> extends Constraint<T> {
	
	private String oldMin;
	private String oldMax;

	@Override
    public void applyConstraint() {
		
		oldMax = referenceField.getMaxAsString();
		oldMin = referenceField.getMinAsString();
	
		referenceField.setMinAsString(inRelationshipWith.getLastSampledAsString(), false);
		referenceField.setMaxAsString(inRelationshipWith.getLastSampledAsString(), true);
    }
	
	public void resetConstraint() {
		
		referenceField.setMinAsString(oldMin, false);
		referenceField.setMaxAsString(oldMax, false);
	}

}
