package com.privat.pitz.financehelper.core;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * UI callback used when a transaction has to be relayed to another financial entity: the core
 * asks the UI to collect the user's choice. Declared here so core does not depend on the UI layer.
 */
public interface RedirectionPrompt {
    void getTransactionRedirectionInput(String targetFileName, JSONObject fileContent,
                                        String desc, float amount, JSONArray allAccounts);
}
