package com.mapr.synth.constraint;

import java.text.ParseException;

import com.mapr.synth.FancyTimeFormatter;
import com.mapr.synth.samplers.ComparableField;
import com.mapr.synth.samplers.DateSampler;
import com.mapr.synth.samplers.IntegerSampler;

public class EqualsToConstraint extends Constraint {
	
	private String oldMin;
	private String oldMax;

	@Override
    public void applyConstraint() {
		
		

		if(referenceField instanceof ComparableField && inRelationshipWith instanceof ComparableField) {
			oldMax = ((ComparableField) referenceField).getMaxAsString();
			oldMin = ((ComparableField) referenceField).getMinAsString();

			((ComparableField) referenceField).setMinAsString(((ComparableField) inRelationshipWith).getLastSampledAsString(), false);
			((ComparableField) referenceField).setMaxAsString(((ComparableField) inRelationshipWith).getLastSampledAsString(), true);
		}
		
		/*
		// IntegerSampler
    	if(referenceField instanceof IntegerSampler) {
    		oldMin = ((IntegerSampler) referenceField).getMin();
    		oldMax = ((IntegerSampler) referenceField).getMax();
    		
    		int lastSampledInt = inRelationshipWith.getLastSampled().intValue();
    		
    		((IntegerSampler) referenceField).setMinAsInt(lastSampledInt);
    		((IntegerSampler) referenceField).setMaxAsInt(lastSampledInt + 1);
    	}
    	
		// DateSampler
    	else if (referenceField instanceof DateSampler) {
    		
    		oldMin = ((DateSampler) referenceField).getStart();
    		oldMax = ((DateSampler) referenceField).getEnd();
    		
    		String lastSampledDate = inRelationshipWith.getLastSampled().asText();
    		
    		try {
				((DateSampler) referenceField).setStart(lastSampledDate);
				((DateSampler) referenceField).setEnd(lastSampledDate);
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
    	}*/
    }
	
	public void resetConstraint() {
		
		if(referenceField instanceof ComparableField) {
			((ComparableField) referenceField).setMinAsString(oldMin, false);
			((ComparableField) referenceField).setMaxAsString(oldMax, false);
		}
		/*
		// IntegerSampler
    	if(referenceField instanceof IntegerSampler) {
    		((IntegerSampler) referenceField).setMinAsInt((int)oldMin);
    		((IntegerSampler) referenceField).setMaxAsInt((int)oldMax);
    	}
    	
		// DateSampler
    	else if (referenceField instanceof DateSampler) {
    		((DateSampler) referenceField).setStartL((long) oldMin);
    		((DateSampler) referenceField).setEndL((long) oldMax);
    	}*/
	}

}
