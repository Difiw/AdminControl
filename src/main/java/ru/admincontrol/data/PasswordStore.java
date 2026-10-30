package ru.admincontrol.data;

import java.util.LinkedHashMap;
import java.util.Map;

public class PasswordStore {
	public Map<String, PasswordEntry> byNick = new LinkedHashMap<>();
	public Map<String, PasswordEntry> byUuid = new LinkedHashMap<>();
}
