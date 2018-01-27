package org.neo4j.shell.prettyprint;

import org.junit.Test;
import org.mockito.Matchers;
import org.neo4j.driver.internal.types.InternalTypeSystem;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.summary.*;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Path;
import org.neo4j.driver.v1.types.Relationship;
import org.neo4j.shell.cli.Format;
import org.neo4j.shell.state.BoltResult;
import org.neo4j.shell.state.ListBoltResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableMap;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrettyPrinterTest {

    private final PrettyPrinter plainPrinter = new PrettyPrinter(Format.PLAIN,-1, false);
    private final PrettyPrinter verbosePrinter = new PrettyPrinter(Format.VERBOSE, -1, true);

    @Test
    public void returnStatisticsForEmptyRecords() throws Exception {
        // given
        ResultSummary resultSummary = mock(ResultSummary.class);
        SummaryCounters summaryCounters = mock(SummaryCounters.class);
        BoltResult result = mock(ListBoltResult.class);

        when(result.iterate()).thenReturn(Collections.emptyIterator());

        when(result.getSummary()).thenReturn(resultSummary);
        when(resultSummary.counters()).thenReturn(summaryCounters);
        when(summaryCounters.labelsAdded()).thenReturn(1);
        when(summaryCounters.nodesCreated()).thenReturn(10);

        // when
        StringBuilder actual = new StringBuilder();
        verbosePrinter.format(result, actual::append);


        // then
        assertThat(actual.toString(), containsString("Added 10 nodes, Added 1 labels"));
    }

    @Test
    public void prettyPrintProfileInformation() throws Exception {
        // given
        ResultSummary resultSummary = mock(ResultSummary.class);
        ProfiledPlan plan = mock(ProfiledPlan.class);
        when(plan.dbHits()).thenReturn(1000L);
        when(plan.records()).thenReturn(20L);

        when(resultSummary.hasPlan()).thenReturn(true);
        when(resultSummary.hasProfile()).thenReturn(true);
        when(resultSummary.plan()).thenReturn(plan);
        when(resultSummary.profile()).thenReturn(plan);
        when(resultSummary.resultAvailableAfter(anyObject())).thenReturn(5L);
        when(resultSummary.resultConsumedAfter(anyObject())).thenReturn(7L);
        when(resultSummary.statementType()).thenReturn(StatementType.READ_ONLY);
        Map<String, Value> argumentMap = Values.parameters("Version", "3.1", "Planner", "COST", "Runtime", "INTERPRETED").asMap(v -> v);
        when(plan.arguments()).thenReturn(argumentMap);

        BoltResult result = mock(ListBoltResult.class);
        when(result.iterate()).thenReturn(Collections.emptyIterator());
        when(result.getSummary()).thenReturn(resultSummary);

        // when
        String actual = configureFormat(result);


        // then
        String expected =
                "Plan: \"PROFILE\"\n" +
                "Statement: \"READ_ONLY\"\n" +
                "Version: \"3.1\"\n" +
                "Planner: \"COST\"\n" +
                "Runtime: \"INTERPRETED\"\n" +
                "Time: 12\n" +
                "Rows: 20\n" +
                "DbHits: 1000";
        Stream.of(expected.split("\n")).forEach(e -> assertThat(actual, containsString(e)));
    }

    @Test
    public void prettyPrintExplainInformation() throws Exception {
        // given
        ResultSummary resultSummary = mock(ResultSummary.class);
        ProfiledPlan plan = mock(ProfiledPlan.class);
        when(plan.dbHits()).thenReturn(1000L);
        when(plan.records()).thenReturn(20L);

        when(resultSummary.hasPlan()).thenReturn(true);
        when(resultSummary.hasProfile()).thenReturn(false);
        when(resultSummary.plan()).thenReturn(plan);
        when(resultSummary.resultAvailableAfter(anyObject())).thenReturn(5L);
        when(resultSummary.resultConsumedAfter(anyObject())).thenReturn(7L);
        when(resultSummary.statementType()).thenReturn(StatementType.READ_ONLY);
        Map<String, Value> argumentMap = Values.parameters("Version", "3.1", "Planner", "COST", "Runtime", "INTERPRETED").asMap(v -> v);
        when(plan.arguments()).thenReturn(argumentMap);

        BoltResult result = mock(ListBoltResult.class);
        when(result.iterate()).thenReturn(Collections.emptyIterator());
        when(result.getSummary()).thenReturn(resultSummary);

        // when
        String actual = configureFormat(result);


        // then
        String expected =
                "Plan: \"EXPLAIN\"\n" +
                "Statement: \"READ_ONLY\"\n" +
                "Version: \"3.1\"\n" +
                "Planner: \"COST\"\n" +
                "Runtime: \"INTERPRETED\"\n" +
                "Time: 12";
        Stream.of(expected.split("\n")).forEach(e -> assertThat(actual, containsString(e)));
    }

    @Test
    public void prettyPrintList() throws Exception {
        // given
        BoltResult result = mock(ListBoltResult.class);

        Record record1 = mock(Record.class);
        Record record2 = mock(Record.class);
        Value value1 = mock(Value.class);
        Value value2 = mock(Value.class);


        when(value1.type()).thenReturn(InternalTypeSystem.TYPE_SYSTEM.LIST());
        when(value2.type()).thenReturn(InternalTypeSystem.TYPE_SYSTEM.LIST());

        when(value1.asList(Matchers.anyObject())).thenReturn(asList("val1_1", "val1_2"));
        when(value2.asList(Matchers.anyObject())).thenReturn(asList("val2_1"));

        when(record1.keys()).thenReturn(asList("col1", "col2"));
        when(record1.values()).thenReturn(asList(value1, value2));
        when(record2.values()).thenReturn(asList(value2));

        List<Record> records = asList(record1, record2);
        when(result.iterate()).thenReturn(records.iterator());

        when(result.getSummary()).thenReturn(mock(ResultSummary.class));

        // when
        String actual = configureFormat(result);


        // then
        assertThat(actual, is("col1, col2\n[val1_1, val1_2], [val2_1]\n[val2_1]\n"));
    }

    @Test
    public void prettyPrintNode() throws Exception {
        // given
        BoltResult result = mock(ListBoltResult.class);

        Record record = mock(Record.class);
        Value value = mock(Value.class);

        Node node = mock(Node.class);
        HashMap<String, Object> propertiesAsMap = new HashMap<>();
        propertiesAsMap.put("prop1", "prop1_value");
        propertiesAsMap.put("prop2", "prop2_value");

        when(value.type()).thenReturn(InternalTypeSystem.TYPE_SYSTEM.NODE());

        when(value.asNode()).thenReturn(node);
        when(node.labels()).thenReturn(asList("label1", "label2"));
        when(node.asMap(anyObject())).thenReturn(unmodifiableMap(propertiesAsMap));

        when(record.keys()).thenReturn(asList("col1", "col2"));
        when(record.values()).thenReturn(asList(value));

        when(result.iterate()).thenReturn(asList(record).iterator());
        when(result.getSummary()).thenReturn(mock(ResultSummary.class));

        // when
        String actual = configureFormat(result);


        // then
        assertThat(actual, is("col1, col2\n" +
                "(:label1:label2 {prop2: prop2_value, prop1: prop1_value})\n"));
    }

    @Test
    public void prettyPrintRelationships() throws Exception {
        // given
        BoltResult result = mock(ListBoltResult.class);

        Record record = mock(Record.class);
        Value value = mock(Value.class);

        Relationship relationship = mock(Relationship.class);
        HashMap<String, Object> propertiesAsMap = new HashMap<>();
        propertiesAsMap.put("prop1", "prop1_value");
        propertiesAsMap.put("prop2", "prop2_value");

        when(value.type()).thenReturn(InternalTypeSystem.TYPE_SYSTEM.RELATIONSHIP());

        when(value.asRelationship()).thenReturn(relationship);
        when(relationship.type()).thenReturn("RELATIONSHIP_TYPE");
        when(relationship.asMap(anyObject())).thenReturn(unmodifiableMap(propertiesAsMap));

        when(record.keys()).thenReturn(asList("rel"));
        when(record.values()).thenReturn(asList(value));

        when(result.iterate()).thenReturn(asList(record).iterator());
        when(result.getSummary()).thenReturn(mock(ResultSummary.class));

        // when
        String actual = configureFormat(result);


        // then
        assertThat(actual, is("rel\n[:RELATIONSHIP_TYPE {prop2: prop2_value, prop1: prop1_value}]\n"));
    }

    @Test
    public void printRelationshipsAndNodesWithEscapingForSpecialCharacters() throws Exception {
        BoltResult result = mock(ListBoltResult.class);

        Record record = mock(Record.class);
        Value relVal = mock(Value.class);
        Value nodeVal = mock(Value.class);

        Relationship relationship = mock(Relationship.class);
        HashMap<String, Object> relProp = new HashMap<>();
        relProp.put("prop1", "\"prop1, value\"");
        relProp.put("prop2", "prop2_value");

        Node node = mock(Node.class);
        HashMap<String, Object> nodeProp = new HashMap<>();
        nodeProp.put("prop1", "\"prop1:value\"");
        nodeProp.put("1prop2", "\"\"");
        nodeProp.put("ä", "not-escaped");


        when(relVal.type()).thenReturn(InternalTypeSystem.TYPE_SYSTEM.RELATIONSHIP());
        when(nodeVal.type()).thenReturn(InternalTypeSystem.TYPE_SYSTEM.NODE());

        when(relVal.asRelationship()).thenReturn(relationship);
        when(relationship.type()).thenReturn("RELATIONSHIP,TYPE");
        when(relationship.asMap(anyObject())).thenReturn(unmodifiableMap(relProp));


        when(nodeVal.asNode()).thenReturn(node);
        when(node.labels()).thenReturn(asList("label `1", "label2"));
        when(node.asMap(anyObject())).thenReturn(unmodifiableMap(nodeProp));


        when(record.keys()).thenReturn(asList("rel", "node"));
        when(record.values()).thenReturn(asList(relVal, nodeVal));

        when(result.iterate()).thenReturn(asList(record).iterator());
        when(result.getSummary()).thenReturn(mock(ResultSummary.class));

        // when
        String actual = configureFormat(result);


        // then
        assertThat(actual, is("rel, node\n[:`RELATIONSHIP,TYPE` {prop2: prop2_value, prop1: \"prop1, value\"}], " +
                "(:`label ``1`:label2 {prop1: \"prop1:value\", `1prop2`: \"\", ä: not-escaped})\n"));
    }

    @Test
    public void prettyPrintPaths() throws Exception {
        // given
        BoltResult result = mock(ListBoltResult.class);

        Record record = mock(Record.class);
        Value value = mock(Value.class);

        Node start = mock(Node.class);
        HashMap<String, Object> startProperties = new HashMap<>();
        startProperties.put("prop1", "prop1_value");
        when(start.labels()).thenReturn(asList("start"));
        when(start.id()).thenReturn(1l);

        Node middle = mock(Node.class);
        when(middle.labels()).thenReturn(asList("middle"));
        when(middle.id()).thenReturn(2l);

        Node end = mock(Node.class);
        HashMap<String, Object> endProperties = new HashMap<>();
        endProperties.put("prop2", "prop2_value");
        when(end.labels()).thenReturn(asList("end"));
        when(end.id()).thenReturn(3l);

        Path path = mock(Path.class);
        when(path.start()).thenReturn(start);

        Relationship relationship = mock(Relationship.class);
        when(relationship.type()).thenReturn("RELATIONSHIP_TYPE");
        when(relationship.startNodeId()).thenReturn(1l).thenReturn(3l);


        Path.Segment segment1 = mock(Path.Segment.class);
        when(segment1.start()).thenReturn(start);
        when(segment1.end()).thenReturn(middle);
        when(segment1.relationship()).thenReturn(relationship);

        Path.Segment segment2 = mock(Path.Segment.class);
        when(segment2.start()).thenReturn(middle);
        when(segment2.end()).thenReturn(end);
        when(segment2.relationship()).thenReturn(relationship);

        when(value.type()).thenReturn(InternalTypeSystem.TYPE_SYSTEM.PATH());
        when(value.asPath()).thenReturn(path);
        when(path.iterator()).thenReturn(asList(segment1, segment2).iterator());
        when(start.asMap(anyObject())).thenReturn(unmodifiableMap(startProperties));
        when(end.asMap(anyObject())).thenReturn(unmodifiableMap(endProperties));

        when(record.keys()).thenReturn(asList("path"));
        when(record.values()).thenReturn(asList(value));

        when(result.iterate()).thenReturn(asList(record).iterator());
        when(result.getSummary()).thenReturn(mock(ResultSummary.class));

        // when
        String actual = configureFormat(result);


        // then
        assertThat(actual, is("path\n" +
                "(:start {prop1: prop1_value})-[:RELATIONSHIP_TYPE]->" +
                "(:middle)<-[:RELATIONSHIP_TYPE]-(:end {prop2: prop2_value})\n"));
    }

    @Test
    public void prettyPrintSingleNodePath() throws Exception {
        // given
        BoltResult result = mock(ListBoltResult.class);

        Record record = mock(Record.class);
        Value value = mock(Value.class);

        Node start = mock(Node.class);
        when(start.labels()).thenReturn(asList("start"));
        when(start.id()).thenReturn(1l);

        Node end = mock(Node.class);
        when(end.labels()).thenReturn(asList("end"));
        when(end.id()).thenReturn(2l);

        Path path = mock(Path.class);
        when(path.start()).thenReturn(start);

        Relationship relationship = mock(Relationship.class);
        when(relationship.type()).thenReturn("RELATIONSHIP_TYPE");
        when(relationship.startNodeId()).thenReturn(1l);


        Path.Segment segment1 = mock(Path.Segment.class);
        when(segment1.start()).thenReturn(start);
        when(segment1.end()).thenReturn(end);
        when(segment1.relationship()).thenReturn(relationship);

        when(value.type()).thenReturn(InternalTypeSystem.TYPE_SYSTEM.PATH());
        when(value.asPath()).thenReturn(path);
        when(path.iterator()).thenReturn(asList(segment1).iterator());

        when(record.keys()).thenReturn(asList("path"));
        when(record.values()).thenReturn(asList(value));

        when(result.iterate()).thenReturn(asList(record).iterator());
        when(result.getSummary()).thenReturn(mock(ResultSummary.class));

        // when
        String actual = configureFormat(result);


        // then
        assertThat(actual, is("path\n(:start)-[:RELATIONSHIP_TYPE]->(:end)\n"));
    }

    @Test
    public void prettyPrintThreeSegmentPath() throws Exception {
        // given
        BoltResult result = mock(ListBoltResult.class);

        Record record = mock(Record.class);
        Value value = mock(Value.class);

        Node start = mock(Node.class);
        when(start.labels()).thenReturn(asList("start"));
        when(start.id()).thenReturn(1l);

        Node second = mock(Node.class);
        when(second.labels()).thenReturn(asList("second"));
        when(second.id()).thenReturn(2l);

        Node third = mock(Node.class);
        when(third.labels()).thenReturn(asList("third"));
        when(third.id()).thenReturn(3l);

        Node end = mock(Node.class);
        when(end.labels()).thenReturn(asList("end"));
        when(end.id()).thenReturn(4l);

        Path path = mock(Path.class);
        when(path.start()).thenReturn(start);

        Relationship relationship = mock(Relationship.class);
        when(relationship.type()).thenReturn("RELATIONSHIP_TYPE");
        when(relationship.startNodeId()).thenReturn(1l).thenReturn(3l).thenReturn(3l);


        Path.Segment segment1 = mock(Path.Segment.class);
        when(segment1.start()).thenReturn(start);
        when(segment1.end()).thenReturn(second);
        when(segment1.relationship()).thenReturn(relationship);

        Path.Segment segment2 = mock(Path.Segment.class);
        when(segment2.start()).thenReturn(second);
        when(segment2.end()).thenReturn(third);
        when(segment2.relationship()).thenReturn(relationship);

        Path.Segment segment3 = mock(Path.Segment.class);
        when(segment3.start()).thenReturn(third);
        when(segment3.end()).thenReturn(end);
        when(segment3.relationship()).thenReturn(relationship);

        when(value.type()).thenReturn(InternalTypeSystem.TYPE_SYSTEM.PATH());
        when(value.asPath()).thenReturn(path);
        when(path.iterator()).thenReturn(asList(segment1, segment2, segment3).iterator());

        when(record.keys()).thenReturn(asList("path"));
        when(record.values()).thenReturn(asList(value));

        when(result.iterate()).thenReturn(asList(record).iterator());
        when(result.getSummary()).thenReturn(mock(ResultSummary.class));

        // when
        String actual = configureFormat(result);


        // then
        assertThat(actual, is("path\n" +
                "(:start)-[:RELATIONSHIP_TYPE]->" +
                "(:second)<-[:RELATIONSHIP_TYPE]-(:third)-[:RELATIONSHIP_TYPE]->(:end)\n"));
    }

    private String configureFormat(BoltResult result) {
        StringBuilder actual = new StringBuilder();
        plainPrinter.format(result, (s) ->  {if (s!=null && !s.trim().isEmpty()) actual.append(s).append(OutputFormatter.NEWLINE);});
        return actual.toString();
    }
}
