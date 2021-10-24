package com.mapr.synth.constraint;

import java.util.Map;

import com.mapr.synth.samplers.FieldSampler;

public class DependencyConstraint extends Constraint {
	
	private Map<String, FieldSampler> dependency;
	private FieldSampler dependentField;
	
	public void setDependency(Map<String, FieldSampler> dependency) {
		this.dependency = dependency;
	}
	
	@Override
	public void applyConstraint() {
		String lastSampledForAtt1 = this.inRelationshipWith.getLastSampled().asText();
		dependentField = dependency.get(lastSampledForAtt1);
	}
	
	public FieldSampler getDependentField() {
		return dependentField;
	}

}
