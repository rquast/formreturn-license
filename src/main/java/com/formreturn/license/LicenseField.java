package com.formreturn.license;

public class LicenseField {

	public LicenseField(String fieldname, String description, String[] values, String defaultValue) {
		this.fieldname = fieldname;
		this.description = description;
		if (values != null) {
			this.values = values;
		}
		if (defaultValue != null) {
			this.defaultValue = defaultValue;
		}
	}

	public String fieldname;

	public String description;

	public String[] values;

	public String defaultValue;

}
