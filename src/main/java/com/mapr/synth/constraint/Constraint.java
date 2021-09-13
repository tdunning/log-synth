package com.mapr.synth.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.mapr.synth.samplers.FieldSampler;

public class Constraint extends FieldSampler{
	
	private String att1 = "";
	private String att2 = "";
	private String relationship = "";

	@Override
	public JsonNode sample() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getAtt1() {
		return att1;
	}

	public void setAtt1(String att1) {
		this.att1 = att1;
	}

	public String getAtt2() {
		return att2;
	}

	public void setAtt2(String att2) {
		this.att2 = att2;
	}

	public String getRelationship() {
		return relationship;
	}

	public void setRelationship(String relationship) {
		this.relationship = relationship;
	}

}
