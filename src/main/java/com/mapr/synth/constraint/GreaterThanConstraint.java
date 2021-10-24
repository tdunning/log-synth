package com.mapr.synth.constraint;

import java.text.ParseException;
import java.util.Date;

import com.mapr.synth.FancyTimeFormatter;
import com.mapr.synth.samplers.ComparableField;
import com.mapr.synth.samplers.DateSampler;
import com.mapr.synth.samplers.IntegerSampler;

public class GreaterThanConstraint extends Constraint {
	
	private String oldMin;
	
	@Override
    public void applyConstraint() {
		

		if(referenceField instanceof ComparableField && inRelationshipWith instanceof ComparableField) {
			oldMin = ((ComparableField) referenceField).getMinAsString();
			if(((ComparableField) inRelationshipWith).compareTo(oldMin) > 0) {
				((ComparableField) referenceField).setMinAsString(((ComparableField) inRelationshipWith).getLastSampledAsString(), true);
			}
		}
		
		/*
    	if(referenceField instanceof IntegerSampler) {
    		oldMin = ((IntegerSampler) referenceField).getMin();
    		int lastSampledInt = inRelationshipWith.getLastSampled().intValue();
    		if(lastSampledInt > (int) oldMin) {
    			((IntegerSampler) referenceField).setMinAsInt(lastSampledInt + 1);
    		}
    	}
    	
    	else if (referenceField instanceof DateSampler) {
    		
    		oldMin = ((DateSampler) referenceField).getStart();
    		FancyTimeFormatter df = ((DateSampler) referenceField).getFormat(); 
    		String lastSampledDate =  inRelationshipWith.getLastSampled().asText();
    		try {
				if(df.parse(lastSampledDate).getTime() > (long) oldMin) {
					((DateSampler) referenceField).setStart(lastSampledDate);
				}
			} catch (ParseException e) {
 				e.printStackTrace();
			}
    		
    	}*/
    }
	
	public void resetConstraint() {
		
		if(referenceField instanceof ComparableField) {
			((ComparableField) referenceField).setMinAsString(oldMin, false);
		}
		/*
    	if(referenceField instanceof IntegerSampler) {
    		((IntegerSampler) referenceField).setMinAsInt((int)oldMin);
    	}
    	if(referenceField instanceof DateSampler) {
			((DateSampler) referenceField).setStartL((long) oldMin);
    	}*/
		
	}

}
