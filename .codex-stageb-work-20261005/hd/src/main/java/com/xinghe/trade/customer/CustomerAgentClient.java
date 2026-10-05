package com.xinghe.trade.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface CustomerAgentClient {
    JsonNode complete(ObjectNode request);
}
