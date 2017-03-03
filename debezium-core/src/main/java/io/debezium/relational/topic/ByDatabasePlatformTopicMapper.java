/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.relational.topic;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author David Leibovic
 * @see this doc for useful terminology definitions: https://docs.google.com/document/d/12KZDiv5uUvlB8jnJQJ8KK7PR6whPoWUQEWpX-Ly8n3A/edit#
 */
public class ByDatabasePlatformTopicMapper extends TopicMapper {

    public String getTopicName() {
        final String database = table.id().catalog();
        Pattern shardPattern = Pattern.compile("^(etsy_.*)_\\d+$");
        Matcher shardMatcher = shardPattern.matcher(database);
        if (shardMatcher.matches()) {
            return shardMatcher.group(1);
        }
        return database;
    }

    public void enhanceKeySchema(SchemaBuilder keySchemaBuilder) {
        // Now that multiple tables can share a topic, the Key Schema can no longer consist of solely the record's PK
        // fields, since a PK is not guaranteed to be unique across tables.
        keySchemaBuilder.field("tableName", Schema.STRING_SCHEMA);

        // Now that multiple replica sets can share a topic, we need to add the replica set, since even a table + PK
        // combination is not guaranteed to be unique across replica sets.
        keySchemaBuilder.field("replicaSet", Schema.STRING_SCHEMA);
    }

    public Map<String, Object> getNonRowFieldsToAddToKey(Schema schema) {
        Map<String, Object> nonRowFields = new HashMap<>();
        nonRowFields.put("tableName", table.id().table());
        nonRowFields.put("replicaSet", table.id().catalog());
        return nonRowFields;
    }

}
