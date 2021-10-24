package com.mapr.synth.constraint;

import java.text.ParseException;

import com.mapr.synth.FancyTimeFormatter;
import com.mapr.synth.samplers.ComparableField;
import com.mapr.synth.samplers.DateSampler;
import com.mapr.synth.samplers.IntegerSampler;

public class LowerThanConstraint extends Constraint {
	
	private String oldMax;
	
	@Override
    public void applyConstraint() {
		
		//get max and save it 
		//get last sampled as string
		//compare it oppure 
		//set max
		
		if(referenceField instanceof ComparableField && inRelationshipWith instanceof ComparableField) {
			oldMax = ((ComparableField) referenceField).getMaxAsString();
			if(((ComparableField) inRelationshipWith).compareTo(oldMax) < 0) {
				((ComparableField) referenceField).setMaxAsString(((ComparableField) inRelationshipWith).getLastSampledAsString(), false);
			}
		}
		
		/*
    	if(referenceField instanceof IntegerSampler) {
    		oldMax = ((IntegerSampler) referenceField).getMax();
    		int lastSampledInt = inRelationshipWith.getLastSampled().intValue();
    		
    		if(lastSampledInt <= (int) oldMax) {
    			((IntegerSampler) referenceField).setMaxAsInt(lastSampledInt);
    		}
    	}
    	
    	else if (referenceField instanceof DateSampler) {
    		
    		oldMax = ((DateSampler) referenceField).getEnd();
    		FancyTimeFormatter df = ((DateSampler) referenceField).getFormat(); 
    		String lastSampledDate =  inRelationshipWith.getLastSampled().asText();
    		try {
				if(df.parse(lastSampledDate).getTime() <= (long) oldMax) {
					((DateSampler) referenceField).setEnd(lastSampledDate);
				}
			} catch (ParseException e) {
 				e.printStackTrace();}
    		
    	}
    	*/

    }
	
	public void resetConstraint() {
		
		if(referenceField instanceof ComparableField) {
			((ComparableField) referenceField).setMaxAsString(oldMax, false);
		}
		/*
    	if(referenceField instanceof IntegerSampler) {
    		((IntegerSampler) referenceField).setMaxAsInt((int)oldMax);
    	}
    	if(referenceField instanceof DateSampler) {
    		((DateSampler) referenceField).setEndL((long)oldMax);
    	}*/
	}

}
