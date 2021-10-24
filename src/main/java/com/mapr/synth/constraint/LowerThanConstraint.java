package com.mapr.synth.constraint;

import java.text.ParseException;

import com.mapr.synth.FancyTimeFormatter;
import com.mapr.synth.samplers.ComparableField;
import com.mapr.synth.samplers.DateSampler;
import com.mapr.synth.samplers.FieldSampler;
import com.mapr.synth.samplers.IntegerSampler;

public class LowerThanConstraint<T extends FieldSampler & ComparableField> extends Constraint<T> {
	
	private String oldMax;
	
	@Override
    public void applyConstraint() {
		
		//get max and save it 
		//get last sampled as string
		//compare it oppure 
		//set max
		
		
		oldMax = ((ComparableField) referenceField).getMaxAsString();
		if(inRelationshipWith.compareTo(oldMax) < 0) {
			referenceField.setMaxAsString(inRelationshipWith.getLastSampledAsString(), false);
		}

    }
	
	public void resetConstraint() {
		
		referenceField.setMaxAsString(oldMax, false);
	}

}
