/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.relational;

import static org.junit.Assert.fail;

import java.sql.Types;

import io.debezium.relational.topic.TopicMapper;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.junit.Before;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

import io.debezium.jdbc.JdbcValueConverters;
import io.debezium.relational.topic.ByTableTopicMapper;
import io.debezium.time.Date;
import io.debezium.util.AvroValidator;

public class TableSchemaBuilderTest {

    private final String prefix = "";
    private final TableId id = new TableId("catalog", "schema", "table");
    private final Object[] data = new Object[] { "c1value", 3.142d, java.sql.Date.valueOf("2001-10-31"), 4 };
    private Table table;
    private Column c1;
    private Column c2;
    private Column c3;
    private Column c4;
    private TableSchema schema;
    private AvroValidator validator;
    private Class<TopicMapper> topicMapperClass;

    @Before
    public void beforeEach() {
        topicMapperClass = (Class <TopicMapper>) ((Class<? extends TopicMapper>) ByTableTopicMapper.class);
        validator = AvroValidator.create((original,replacement, conflict)->{
            fail("Should not have come across an invalid schema name");
        });
        schema = null;
        table = Table.editor()
                     .tableId(id)
                     .addColumns(Column.editor().name("C1")
                                       .type("VARCHAR").jdbcType(Types.VARCHAR).length(10)
                                       .optional(false)
                                       .generated(true)
                                       .create(),
                                 Column.editor().name("C2")
                                       .type("NUMBER").jdbcType(Types.NUMERIC).length(5).scale(3)
                                       .create(),
                                 Column.editor().name("C3")
                                       .type("DATE").jdbcType(Types.DATE).length(4)
                                       .optional(true)
                                       .create(),
                                 Column.editor().name("C4")
                                       .type("COUNTER").jdbcType(Types.INTEGER)
                                       .autoIncremented(true)
                                       .optional(true)
                                       .create())
                     .setPrimaryKeyNames("C1", "C2")
                     .create();
        c1 = table.columnWithName("C1");
        c2 = table.columnWithName("C2");
        c3 = table.columnWithName("C3");
        c4 = table.columnWithName("C4");
    }

    @Test
    public void checkPreconditions() {
        assertThat(c1).isNotNull();
        assertThat(c2).isNotNull();
        assertThat(c3).isNotNull();
        assertThat(c4).isNotNull();
    }

    @Test(expected = NullPointerException.class)
    public void shouldFailToBuildTableSchemaFromNullTable() {
        new TableSchemaBuilder(topicMapperClass, new JdbcValueConverters(),validator::validate).create(prefix,null);
    }

    @Test
    public void shouldBuildTableSchemaFromTable() {
        schema = new TableSchemaBuilder(topicMapperClass, new JdbcValueConverters(),validator::validate).create(prefix,table);
        assertThat(schema).isNotNull();
    }

    @Test
    public void shouldBuildTableSchemaFromTableWithoutPrimaryKey() {
        table = table.edit().setPrimaryKeyNames().create();
        schema = new TableSchemaBuilder(topicMapperClass, new JdbcValueConverters(),validator::validate).create(prefix,table);
        assertThat(schema).isNotNull();
        // Check the keys ...
        assertThat(schema.keySchema()).isNull();
        assertThat(schema.keyFromColumnData(data)).isNull();
        // Check the values ...
        Schema values = schema.valueSchema();
        assertThat(values).isNotNull();
        assertThat(values.field("C1").name()).isEqualTo("C1");
        assertThat(values.field("C1").index()).isEqualTo(0);
        assertThat(values.field("C1").schema()).isEqualTo(SchemaBuilder.string().build());
        assertThat(values.field("C2").name()).isEqualTo("C2");
        assertThat(values.field("C2").index()).isEqualTo(1);
        assertThat(values.field("C2").schema()).isEqualTo(Decimal.builder(3).optional().build()); // scale of 3
        assertThat(values.field("C3").name()).isEqualTo("C3");
        assertThat(values.field("C3").index()).isEqualTo(2);
        assertThat(values.field("C3").schema()).isEqualTo(Date.builder().optional().build()); // optional date
        assertThat(values.field("C4").name()).isEqualTo("C4");
        assertThat(values.field("C4").index()).isEqualTo(3);
        assertThat(values.field("C4").schema()).isEqualTo(SchemaBuilder.int32().optional().build()); // JDBC INTEGER = 32 bits
        Struct value = schema.valueFromColumnData(data);
        assertThat(value).isNotNull();
    }

}
