package datawave.query.jexl.functions;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import datawave.ingest.protobuf.TermWeightPosition;
import datawave.query.Constants;
import datawave.query.jexl.DatawaveJexlEngine;
import datawave.query.jexl.JexlASTHelper;
import datawave.query.jexl.functions.TermFrequencyList.Zone;
import datawave.query.jexl.functions.arguments.JexlArgumentDescriptor;
import datawave.query.jexl.visitors.JexlStringBuildingVisitor;
import datawave.query.postprocessing.tf.TermOffsetMap;
import datawave.query.util.MockDateIndexHelper;
import datawave.query.util.MockMetadataHelper;
import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.JexlException;
import org.apache.commons.jexl2.MapContext;
import org.apache.commons.jexl2.parser.ASTFunctionNode;
import org.apache.commons.jexl2.parser.ASTJexlScript;
import org.apache.commons.jexl2.parser.ASTReference;
import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.jexl2.parser.ParseException;
import org.apache.log4j.Logger;
import org.javatuples.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.fail;

public class ContentFunctionsTest {
    static final Logger log = Logger.getLogger(ContentFunctionsTest.class);
    private static JexlEngine engine = new DatawaveJexlEngine();
    
    private JexlContext context;
    private TermOffsetMap termOffSetMap;
    
    private String phraseFunction = ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME;
    private String scoredPhraseFunction = ContentFunctions.CONTENT_SCORED_PHRASE_FUNCTION_NAME;
    private String eventId = "shard\0type\0uid";
    
    @BeforeClass
    public static void setUp() throws URISyntaxException {
        Map<String,Object> functions = new HashMap<>();
        functions.put("f", QueryFunctions.class);
        functions.put("geo", GeoFunctions.class);
        functions.put("content", ContentFunctions.class);
        engine.setFunctions(functions);
    }
    
    @Before
    public void setup() {
        this.context = new MapContext();
        this.termOffSetMap = new TermOffsetMap();
    }
    
    /**
     * Ensures that result is Boolean and equal to the expected value
     * 
     * @param result
     *            The object in question
     * @param expected
     *            The expected result
     * @return True if result is Boolean and equal to expected
     */
    public static boolean expect(Object result, Boolean expected) {
        // treat null as false
        if (null == result) {
            return Boolean.FALSE.equals(expected);
        }
        
        if (result instanceof Boolean) {
            return result.equals(expected);
        }
        
        return false;
    }
    
    private String buildFunction(String functionName, String... args) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(ContentFunctions.CONTENT_FUNCTION_NAMESPACE).append(":").append(functionName);
        sb.append("(");
        
        for (String arg : args) {
            sb.append(arg).append(",");
        }
        
        sb.setLength(sb.length() - 1);
        sb.append(")");
        
        return sb.toString();
    }
    
    private List<TermWeightPosition> asList(int... offsets) {
        return asList(true, offsets);
    }
    
    private List<TermWeightPosition> asList(boolean zeroOffsetMatch, int... offsets) {
        List<TermWeightPosition> list = new ArrayList<>();
        for (int offset : offsets) {
            list.add(getPosition(offset, zeroOffsetMatch));
        }
        return list;
    }
    
    private List<TermWeightPosition> asList(List<Integer> offsets, List<Integer> skips) {
        return asList(true, offsets, skips);
    }
    
    private List<TermWeightPosition> asList(boolean zeroOffsetMatch, List<Integer> offsets, List<Integer> skips) {
        if (offsets.size() != skips.size()) {
            fail("Offsets and skips size need to match.");
        }
        
        List<TermWeightPosition> list = new ArrayList<>();
        for (int i = 0; i < offsets.size(); i++) {
            list.add(getPosition(offsets.get(i), skips.get(i), zeroOffsetMatch));
        }
        
        return list;
    }
    
    private List<TermWeightPosition> asList(List<Integer> offsets, List<Integer> skips, List<Float> scores) {
        if (offsets.size() != skips.size() || offsets.size() != scores.size()) {
            fail("Offsets and skips size need to match.");
        }
        
        List<TermWeightPosition> list = new ArrayList<>();
        for (int i = 0; i < offsets.size(); i++) {
            list.add(getPosition(offsets.get(i), skips.get(i), scores.get(i)));
        }
        
        return list;
    }
    
    private TermWeightPosition getPosition(int offset) {
        return new TermWeightPosition.Builder().setOffset(offset).build();
    }
    
    private TermWeightPosition getPosition(int offset, boolean zeroOffsetMatch) {
        return new TermWeightPosition.Builder().setOffset(offset).setZeroOffsetMatch(zeroOffsetMatch).build();
    }
    
    private TermWeightPosition getPosition(int offset, int prevSkips, boolean zeroOffsetMatch) {
        return new TermWeightPosition.Builder().setOffset(offset).setPrevSkips(prevSkips).setZeroOffsetMatch(zeroOffsetMatch).build();
    }
    
    private TermWeightPosition getPosition(int offset, int prevSkips, float score) {
        return new TermWeightPosition.Builder().setOffset(offset).setPrevSkips(prevSkips).setScore(TermWeightPosition.positionScoreToTermWeightScore(score))
                        .build();
    }
    
    private void assertPhraseOffset(String field, int startOffset, int endOffset) {
        Collection<Pair<Integer,Integer>> phraseOffsets = termOffSetMap.getPhraseOffsets(field);
        boolean found = phraseOffsets.stream().anyMatch((pair) -> pair.getValue0().equals(startOffset) && pair.getValue1().equals(endOffset));
        Assert.assertTrue("Expected phrase offset [" + startOffset + ", " + endOffset + "] for field " + field, found);
    }
    
    private void assertPhraseOffsetsEmpty() {
        Assert.assertTrue("Expected empty phrase offset map", termOffSetMap.getPhraseOffsets().isEmpty());
    }
    
    
    @Test
    public void testEvaluation1() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "1", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(1, 2, 3), Arrays.asList(0, 0, 0));
        list2 = asList(Arrays.asList(5, 6, 7), Arrays.asList(0, 2, 0)); // match (6-2) should match (3+1)
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void reverseSharedTokenIndex() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'a'", "'b'", "'c'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> t1, t2, t3;
        t1 = asList(Arrays.asList(234, 239, 252, 257, 265, 281, 286, 340, 363, 367), Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        t2 = asList(Arrays.asList(212, 229, 252, 272), Arrays.asList(0, 0, 0, 0));
        t3 = asList(Arrays.asList(1, 101, 202, 213, 253, 312, 336), Arrays.asList(0, 0, 0, 0, 0, 0, 0));
        
        termOffSetMap.putTermFrequencyList("a", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t1)));
        termOffSetMap.putTermFrequencyList("b", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t2)));
        termOffSetMap.putTermFrequencyList("c", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 252, 253);
    }
    
    @Test
    public void forwardSharedTokenIndex() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'c'", "'b'", "'a'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> t1, t2, t3;
        t1 = asList(Arrays.asList(234, 239, 252, 257, 265, 281, 286, 340, 363, 367), Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        t2 = asList(Arrays.asList(212, 229, 252, 272), Arrays.asList(0, 0, 0, 0));
        t3 = asList(Arrays.asList(1, 101, 202, 213, 251, 312, 336), Arrays.asList(0, 0, 0, 0, 0, 0, 0));
        
        termOffSetMap.putTermFrequencyList("a", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t1)));
        termOffSetMap.putTermFrequencyList("b", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t2)));
        termOffSetMap.putTermFrequencyList("c", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 251, 252);
    }
    
    @Test
    public void reverseAllSharedTokenIndex() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'a'", "'b'", "'c'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> t1, t2, t3;
        t1 = asList(Arrays.asList(234, 239, 252, 257, 265, 281, 286, 340, 363, 367), Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        t2 = asList(Arrays.asList(212, 229, 252, 272), Arrays.asList(0, 0, 0, 0));
        t3 = asList(Arrays.asList(1, 101, 202, 213, 252, 312, 336), Arrays.asList(0, 0, 0, 0, 0, 0, 0));
        
        termOffSetMap.putTermFrequencyList("a", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t1)));
        termOffSetMap.putTermFrequencyList("b", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t2)));
        termOffSetMap.putTermFrequencyList("c", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 252, 252);
    }
    
    @Test
    public void forwardAllSharedTokenIndex() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'c'", "'b'", "'a'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> t1, t2, t3;
        t1 = asList(Arrays.asList(234, 239, 252, 257, 265, 281, 286, 340, 363, 367), Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        t2 = asList(Arrays.asList(212, 229, 252, 272), Arrays.asList(0, 0, 0, 0));
        t3 = asList(Arrays.asList(1, 101, 202, 213, 252, 312, 336), Arrays.asList(0, 0, 0, 0, 0, 0, 0));
        
        termOffSetMap.putTermFrequencyList("a", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t1)));
        termOffSetMap.putTermFrequencyList("b", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t2)));
        termOffSetMap.putTermFrequencyList("c", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), t3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 252, 252);
    }
    
    /**
     * Same as testEvaluation1, however the term frequency list marks teh fields as non-content expansion fields and hence we expect no results
     */
    @Test
    public void testEvaluationNoContentFields() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "1", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(1, 2, 3), Arrays.asList(0, 0, 0));
        list2 = asList(Arrays.asList(5, 6, 7), Arrays.asList(0, 0, 3)); // match (7-3_ should match (3+1)
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", false, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", false, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testQuotedEvaluation() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "1", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog\\'s'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = asList(3, 4, 5);
        
        termOffSetMap.putTermFrequencyList("dog's", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    /**
     * Ensure that we have a failure
     */
    @Test(expected = JexlException.class)
    public void testQuotedEvaluation_1_fail() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "1", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog's'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        fail("Query should have failed to parse");
    }
    
    @Test
    public void testEvaluation1_1() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "1", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1);
        list2 = asList(2);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluation2() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "1", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = asList(5, 6, 7);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluation3() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "1", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = asList(5);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationWithSkips() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "1", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(4), Arrays.asList(1));
        list2 = asList(Arrays.asList(2), Arrays.asList(1)); // (10-6) = (3+1)
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationEmptyOffsetList() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "1", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = new ArrayList<>();
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationThreeTerms() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "3", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'",
                        "'rat'");
        Expression expr = engine.createExpression(query);
        
        // (15-3)-9 <= 3
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 5, 9);
        list2 = asList(3, 7, 11);
        list3 = asList(10, 15, 20, 25);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationThreeTermsTooSmallDistance() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "2", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'",
                        "'rat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 2, 3);
        list2 = asList(3, 4, 5);
        list3 = asList(10, 15, 20, 25);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationFailedThreeTerms() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "3", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'",
                        "'rat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 2, 3);
        list2 = asList(3, 4, 5);
        list3 = asList(10, 15, 20, 25);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationMiddleMatch() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "2", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'",
                        "'rat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 5, 10);
        list2 = asList(2, 4, 20);
        list3 = asList(6, 8, 15);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationAdjacent1() {
        String query = buildFunction(ContentFunctions.CONTENT_ADJACENT_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1);
        list2 = asList(2);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationAdjacent2() {
        String query = buildFunction(ContentFunctions.CONTENT_ADJACENT_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = asList(5, 6, 7);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationAdjacent3() {
        String query = buildFunction(ContentFunctions.CONTENT_ADJACENT_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = asList(5);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationAdjacentEmptyOffsetList() {
        String query = buildFunction(ContentFunctions.CONTENT_ADJACENT_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = new ArrayList<>();
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationAdjacentThreeTerms() {
        String query = buildFunction(ContentFunctions.CONTENT_ADJACENT_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'rat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 5, 9);
        list2 = asList(3, 7, 11);
        list3 = asList(10, 15, 20, 25);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationAdjacentFailedThreeTerms() {
        String query = buildFunction(ContentFunctions.CONTENT_ADJACENT_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'rat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 2, 3);
        list2 = asList(3, 4, 5);
        list3 = asList(10, 15, 20, 25);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationPhraseBasic() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = asList(3, 4, 5);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 2, 3);
    }
    
    @Test
    public void testEvaluationPhraseBasicWithSkips() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(1, 2, 3), Arrays.asList(0, 1, 0));
        list2 = asList(Arrays.asList(5, 6, 7), Arrays.asList(2, 2, 2));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 2, 5);
    }
    
    @Test
    public void testEvaluationPhraseBasic2() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = asList(4, 5, 6);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 3, 4);
    }
    
    @Test
    public void testEvaluationPhraseBasic2WithSkips() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(1, 2, 3), Arrays.asList(0, 1, 0));
        list2 = asList(Arrays.asList(5, 6, 7), Arrays.asList(1, 3, 1));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 2, 6);
    }
    
    @Test
    public void testEvaluationPhraseBasic3() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'fish'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1);
        list2 = asList(2);
        list3 = asList(3);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("fish", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 1, 3);
    }
    
    @Test
    public void testEvaluationPhraseBasic3WithSkips() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'fish'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(Arrays.asList(1), Arrays.asList(0));
        list2 = asList(Arrays.asList(3), Arrays.asList(1)); // ~3-5
        list3 = asList(Arrays.asList(4, 10), Arrays.asList(0, 0));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("fish", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
    
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 1, 4);
    }
    
    @Test
    public void testEvaluationPhraseBasic3FavorContentOrderedFunction() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'fish'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31, 33, 35, 37, 39);
        list2 = asList(2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40);
        list3 = asList(41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("fish", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 39, 41);
    }
    
    @Test
    public void testEvaluationPhraseBasicOrderFail() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(3, 4, 5);
        list2 = asList(1, 2);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationPhraseBasicFailWithSkips() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(3, 4, 5), Arrays.asList(0, 0, 2));
        list2 = asList(Arrays.asList(1, 2), Arrays.asList(0, 1));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationPhraseBasicOrderFail2() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'fish'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(4);
        list2 = asList(3);
        list3 = asList(2);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("fish", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationPhraseBasicFail2WithSkips() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'fish'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(Arrays.asList(4), Arrays.asList(0));
        list2 = asList(Arrays.asList(3), Arrays.asList(1));
        list3 = asList(Arrays.asList(2), Arrays.asList(0));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("fish", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationPhraseBasicOrderFail3() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'fish'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(2);
        list2 = asList(4);
        list3 = asList(3);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("fish", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationPhraseBasicFail3WithSkips() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'fish'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(Arrays.asList(2), Arrays.asList(0));
        list2 = asList(Arrays.asList(4), Arrays.asList(0));
        list3 = asList(Arrays.asList(3), Arrays.asList(0));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("fish", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationPhraseBasicTermOrderFail() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = asList(4, 5, 6);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationPhraseBasicTermOrderFailWithSkips() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(1, 2, 3), Arrays.asList(1, 1, 1));
        list2 = asList(Arrays.asList(4, 5, 6), Arrays.asList(0, 1, 1));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationPhraseSameTermFailure() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1;
        list1 = asList(1, 3, 5);
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationPhraseSameTermSuccessFirst() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1;
        list1 = asList(1, 2, 5);
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 1, 2);
    }
    
    @Test
    public void testEvaluationPhraseSameTermSuccessLast() {
        String query = buildFunction(ContentFunctions.CONTENT_PHRASE_FUNCTION_NAME, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1;
        list1 = asList(1, 4, 5);
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 4, 5);
    }
    
    @Test
    public void testEvaluationAdjacencySameTermFailureTest() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "2", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1;
        list1 = asList(1, 4);
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
        assertPhraseOffsetsEmpty();
    }
    
    @Test
    public void testEvaluationAdjacencySameTermSuccessTest() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "2", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1;
        list1 = asList(1, 3);
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationAdjacencySameTermWithSkipsSuccessTest() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "2", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1;
        list1 = asList(Arrays.asList(1, 4), Arrays.asList(0, 1));
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationAdjacencySameTermMixedSuccessTest() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "4", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 5);
        list2 = asList(3);
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    /**
     * This test case was originally testPhraseBasicTermOrderFail. This test case knowingly returns a false positive. We can't seem to differentiate term order
     * at the same off, but we were missing some valid phraes. Maybe someone can come up with a way. For now, false negatives seemed worse than false positives.
     */
    @Test
    public void testEvaluationPhraseBasicTermOrderFalsePositive() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 2, 3);
        list2 = asList(3, 4, 5);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
        assertPhraseOffset(Constants.ANY_FIELD, 3, 3);
    }
    
    @Test
    public void testEvaluationPhraseThreeTerm() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'rat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 2, 4);
        list2 = asList(5, 7, 9);
        list3 = asList(6, 8, 10);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationPhraseThreeTermFail() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'dog'", "'rat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 2, 4);
        list2 = asList(5, 7, 9);
        list3 = asList(6, 8, 10);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationPhraseThreeTermPass() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'rat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 2, 4); // cat
        list2 = asList(4, 7, 8, 10); // rat
        list3 = asList(4, 6); // dog
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationPhraseThreeTermFail2() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'rat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 2, 4); // cat
        list2 = asList(5, 7, 9); // rat
        list3 = asList(4, 6, 10); // dog
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationPhraseTermOverlap() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'rat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1); // cat
        list2 = asList(1); // rat
        list3 = asList(1); // dog
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationPhraseTermOverlapWithSkips() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'rat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(false, Arrays.asList(135), Arrays.asList(6)); // cat
        list2 = asList(Arrays.asList(135), Arrays.asList(6)); // rat
        list3 = asList(Arrays.asList(1), Arrays.asList(1)); // dog
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationPhraseTermOverlapPass2() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'rat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1); // cat
        list2 = asList(1); // rat
        list3 = asList(2); // dog
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationPhraseTermOverlapPass3() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'rat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1); // cat
        list2 = asList(1, 5); // rat
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationPhraseTermOverlapPass4() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'rat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(5); // cat
        list2 = asList(1, 5); // rat
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationPhraseTermOverlapFail() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'cat'", "'rat'", "'dog'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(2);
        list2 = asList(2);
        list3 = asList(1);
        
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("rat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationScorePass() {
        String query = buildFunction(scoredPhraseFunction, "'CONTENT'", "-0.200", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(1, 2, 3), Arrays.asList(0, 0, 0), Arrays.asList(-0.223f, -1.4339f, -0.0001f));
        list2 = asList(Arrays.asList(3, 4, 5), Arrays.asList(0, 0, 0), Arrays.asList(-0.001f, -1.4339f, -0.2001f));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationScoreNoZonePass() {
        String query = buildFunction(scoredPhraseFunction, "-0.200", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(1, 2, 3), Arrays.asList(0, 0, 0), Arrays.asList(-0.223f, -1.4339f, -0.0001f));
        list2 = asList(Arrays.asList(3, 4, 5), Arrays.asList(0, 0, 0), Arrays.asList(-0.001f, -1.4339f, -0.2001f));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testEvaluationScoreFail() {
        String query = buildFunction(scoredPhraseFunction, "'CONTENT'", "-0.200", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(Arrays.asList(1, 2, 3), Arrays.asList(0, 0, 0), Arrays.asList(-0.223f, -1.4339f, -0.2001f));
        list2 = asList(Arrays.asList(3, 4, 5), Arrays.asList(0, 0, 0), Arrays.asList(-0.001f, -1.4339f, -0.2001f));
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationMultipleContentFunctions() {
        String query1 = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "3", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'bat'", "'dog'",
                        "'cat'");
        String query2 = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "3", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        String query = query1 + "||" + query2;
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 2, 4);
        list2 = asList(4, 5, 6);
        list3 = asList(11, 12, 14);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("bat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    /**
     * This tests the adjustment mechanism in the ContentOrderedEvaluator within the TraverseAndPrune method.
     */
    @Test
    public void testEvaluationPhrasePruningEdgeCondition() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'bat'");
        
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(9, 10);
        list2 = asList(9, 10, 11);
        list3 = asList(7, 12);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("bat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, true));
    }
    
    /**
     * This tests the adjustment mechanism in the ContentOrderedEvaluator within the TraverseAndPrune method.
     */
    @Test
    public void testEvaluationReverseOffsetAdjustment() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'", "'bat'");
        
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(5, 9, 10, 25, 27, 29);
        list2 = asList(3, 9, 10, 12, 13, 20, 23, 25);
        list3 = asList(1, 12, 13, 27);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("bat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        Object o = expr.evaluate(context);
        
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testEvaluationMultiEvent() {
        String query1 = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "3", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'cat'");
        String query2 = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "3", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'dog'", "'bat'");
        String query = query1 + "||" + query2;
        
        List<TermWeightPosition> list1, list2, list3;
        list1 = asList(1, 2, 3);
        list2 = asList(4, 5, 6);
        list3 = asList(4, 5, 6);
        
        termOffSetMap.putTermFrequencyList("dog", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("cat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("bat", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId + ".1"), list3)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        
        Expression expr = engine.createExpression(query1);
        Object o = expr.evaluate(context);
        Assert.assertTrue(expect(o, true));
        
        expr = engine.createExpression(query2);
        o = expr.evaluate(context);
        Assert.assertTrue(expect(o, false));
        
        expr = engine.createExpression(query);
        o = expr.evaluate(context);
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testJexlFunctionArgumentDescriptor() throws ParseException {
        String query = "content:within('BODY', 5, termOffsetMap, 'hello', 'world')";
        String expected = "(BODY == 'hello' && BODY == 'world')";
        
        testJexlFunctionArgumentDescriptors(query, expected);
    }
    
    @Test
    public void testJexlFunctionArgumentDescriptor2() throws ParseException {
        String query = "content:within(5, termOffsetMap, 'hello', 'world')";
        String expected = "((META == 'hello' and META == 'world') or (BODY == 'hello' and BODY == 'world'))";
        
        testJexlFunctionArgumentDescriptors(query, expected);
    }
    
    @Test
    public void testJexlFunctionArgumentDescriptor3() throws ParseException {
        String query = "content:adjacent('BODY', termOffsetMap, 'hello', 'world')";
        String expected = "(BODY == 'hello' and BODY == 'world')";
        
        testJexlFunctionArgumentDescriptors(query, expected);
    }
    
    @Test
    public void testJexlFunctionArgumentDescriptor4() throws ParseException {
        String query = "content:adjacent(termOffsetMap, 'hello', 'world')";
        String expected = "((META == 'hello' and META == 'world') or (BODY == 'hello' and BODY == 'world'))";
        
        testJexlFunctionArgumentDescriptors(query, expected);
    }
    
    @Test
    public void testJexlFunctionArgumentDescriptor5() throws ParseException {
        String query = "content:" + phraseFunction + "('BODY', termOffsetMap, 'hello', 'world')";
        String expected = "(BODY == 'hello' and BODY == 'world')";
        
        testJexlFunctionArgumentDescriptors(query, expected);
    }
    
    @Test
    public void testJexlFunctionArgumentDescriptor6() throws ParseException {
        String query = "content:" + phraseFunction + "(termOffsetMap, 'hello', 'world')";
        String expected = "((META == 'hello' and META == 'world') or (BODY == 'hello' and BODY == 'world'))";
        
        testJexlFunctionArgumentDescriptors(query, expected);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testJexlFunctionArgumentDescriptor7() throws ParseException {
        String query = "content:" + phraseFunction + "('termOffsetMap', 'hello', 'world')";
        testJexlFunctionArgumentDescriptors(query, "");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testJexlFunctionArgumentDescriptor8() throws ParseException {
        String query = "content:within(3, 'hello', 'world')";
        testJexlFunctionArgumentDescriptors(query, "");
    }
    
    @Test
    public void testJexlFunctionArgumentDescriptor9() throws ParseException {
        String query = "content:" + phraseFunction + "(termOffsetMap, 'hello', 'world')";
        String expected = "(BODY == 'hello' and BODY == 'world')";
        testJexlFunctionArgumentDescriptors(query, expected, Sets.newHashSet("BODY"));
    }
    
    @Test
    public void testJexlFunctionArgumentDescriptor10() throws ParseException {
        String query = "content:" + scoredPhraseFunction + "(-1.1, termOffsetMap, 'hello', 'world')";
        String expected = "((META == 'hello' and META == 'world') or (BODY == 'hello' and BODY == 'world'))";
        
        testJexlFunctionArgumentDescriptors(query, expected);
    }
    
    private void testJexlFunctionArgumentDescriptors(String query, String expected) throws ParseException {
        testJexlFunctionArgumentDescriptors(query, expected, null);
    }
    
    private void testJexlFunctionArgumentDescriptors(String query, String expected, Set<String> contentFields) throws ParseException {
        MockMetadataHelper metadataHelper = new MockMetadataHelper();
        metadataHelper.addTermFrequencyFields(Arrays.asList("BODY", "META"));
        metadataHelper.setIndexedFields(Sets.newHashSet("BODY", "META"));
        
        if (contentFields != null) {
            metadataHelper.addContentFields(contentFields);
        }
        
        MockDateIndexHelper dateIndexHelper = new MockDateIndexHelper();
        
        ASTJexlScript script = JexlASTHelper.parseJexlQuery(query);
        
        JexlNode ref = script.jjtGetChild(0);
        Assert.assertEquals("First child of ASTJexlScript is not an ASTReference", ASTReference.class, ref.getClass());
        
        JexlNode child = ref.jjtGetChild(0);
        Assert.assertEquals("First child of ASTJexlScript is not an AStFunctionNode", ASTFunctionNode.class, child.getClass());
        
        ASTFunctionNode function = (ASTFunctionNode) child;
        
        JexlArgumentDescriptor desc = new ContentFunctionsDescriptor().getArgumentDescriptor(function);
        JexlNode indexQuery = desc.getIndexQuery(null, metadataHelper, dateIndexHelper, null);
        
        ASTJexlScript expectedScript = JexlASTHelper.parseJexlQuery(expected);
        JexlNode scriptChild = expectedScript.jjtGetChild(0);
        
        Assert.assertTrue("Expected " + JexlStringBuildingVisitor.buildQuery(scriptChild) + " but was " + JexlStringBuildingVisitor.buildQuery(indexQuery),
                        JexlASTHelper.equals(scriptChild, indexQuery));
    }
    
    @Test
    public void testDoubleWordInPhrase() {
        String query = buildFunction(phraseFunction, Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'foo'", "'bar'", "'foo'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2;
        list1 = asList(1, 3);
        list2 = asList(2);
        
        termOffSetMap.putTermFrequencyList("foo", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("bar", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList("foo", new TermFrequencyList(Maps.immutableEntry(new Zone("CONTENT", true, eventId), list1)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        
        Object o = expr.evaluate(context);
        Assert.assertTrue(expect(o, true));
    }
    
    @Test
    public void testSomeEmptyOffsetsPhrase() {
        String query = buildFunction(phraseFunction, "BODY", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'foo'", "'bar'", "'car'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3, list4;
        list1 = asList(296);
        list2 = asList(1079);
        list3 = asList(260, 284, 304);
        list4 = asList(1165);
        
        termOffSetMap.putTermFrequencyList("foo", new TermFrequencyList(Maps.immutableEntry(new Zone("BODY", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("bar", new TermFrequencyList(Maps.immutableEntry(new Zone("BODY", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList(
                        "car",
                        new TermFrequencyList(Maps.immutableEntry(new Zone("BODY", true, eventId), list3), Maps.immutableEntry(new Zone("META", true, eventId),
                                        list4)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        context.set("BODY", Arrays.asList("foo", "bar", "car"));
        
        Object o = expr.evaluate(context);
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testSomeEmptyOffsetsAdjacency() {
        String query = buildFunction(ContentFunctions.CONTENT_ADJACENT_FUNCTION_NAME, "BODY", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'foo'", "'bar'",
                        "'car'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3, list4;
        list1 = asList(296);
        list2 = asList(1079);
        list3 = asList(260, 284, 304);
        list4 = asList(1165);
        
        termOffSetMap.putTermFrequencyList("foo", new TermFrequencyList(Maps.immutableEntry(new Zone("BODY", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("bar", new TermFrequencyList(Maps.immutableEntry(new Zone("BODY", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList(
                        "car",
                        new TermFrequencyList(Maps.immutableEntry(new Zone("BODY", true, eventId), list3), Maps.immutableEntry(new Zone("META", true, eventId),
                                        list4)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        context.set("BODY", Arrays.asList("foo", "bar", "car"));
        
        Object o = expr.evaluate(context);
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testSomeEmptyOffsetsWithin() {
        String query = buildFunction(ContentFunctions.CONTENT_WITHIN_FUNCTION_NAME, "BODY", "5", Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, "'foo'",
                        "'bar'", "'car'");
        Expression expr = engine.createExpression(query);
        
        List<TermWeightPosition> list1, list2, list3, list4;
        list1 = asList(296);
        list2 = asList(1079);
        list3 = asList(260, 284, 304);
        list4 = asList(1165);
        
        termOffSetMap.putTermFrequencyList("foo", new TermFrequencyList(Maps.immutableEntry(new Zone("BODY", true, eventId), list1)));
        termOffSetMap.putTermFrequencyList("bar", new TermFrequencyList(Maps.immutableEntry(new Zone("BODY", true, eventId), list2)));
        termOffSetMap.putTermFrequencyList(
                        "car",
                        new TermFrequencyList(Maps.immutableEntry(new Zone("BODY", true, eventId), list3), Maps.immutableEntry(new Zone("META", true, eventId),
                                        list4)));
        
        context.set(Constants.TERM_OFFSET_MAP_JEXL_VARIABLE_NAME, termOffSetMap);
        context.set("BODY", Arrays.asList("foo", "bar", "car"));
        
        Object o = expr.evaluate(context);
        Assert.assertTrue(expect(o, false));
    }
    
    @Test
    public void testDuplicatePhraseOffset() {
        TreeMultimap<Zone,TermWeightPosition> multimap = TreeMultimap.create();
    
        TermOffsetMap termOffsetMap = new TermOffsetMap();
        
        multimap.put(genTestZone(), getPosition(19));
        termOffSetMap.putTermFrequencyList("go", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(20));
        multimap.put(genTestZone(), getPosition(27));
        multimap.put(genTestZone(), getPosition(29));
        termOffSetMap.putTermFrequencyList("and", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(21));
        termOffSetMap.putTermFrequencyList("tell", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(22));
        termOffSetMap.putTermFrequencyList("your", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(23));
        termOffSetMap.putTermFrequencyList("brother", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(20));
        multimap.put(genTestZone(), getPosition(24));
        termOffSetMap.putTermFrequencyList("that", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(25));
        termOffSetMap.putTermFrequencyList("dinners", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(26));
        termOffSetMap.putTermFrequencyList("ready", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(28));
        termOffSetMap.putTermFrequencyList("come", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(30));
        termOffSetMap.putTermFrequencyList("wash", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(31));
        termOffSetMap.putTermFrequencyList("his", new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(genTestZone(), getPosition(32));
        multimap.put(genTestZone(), getPosition(42));
        multimap.put(genTestZone(), getPosition(52));
        termOffSetMap.putTermFrequencyList("hands", new TermFrequencyList(multimap));
        
        // ///////////////////////////
        // Phrase functions
        // ///////////////////////////
    
        // full terms list
        Assert.assertNotNull(termOffsetMap.getTermFrequencyList("his"));
        String[] terms = new String[] {"go", "and", "tell", "your", "brother", "that", "dinners", "ready", "and", "come", "and", "wash", "his", "hands"};
        Assert.assertEquals(Collections.singleton("BODY"), ContentFunctions.phrase("BODY", termOffsetMap, terms));
        
        // duplicate consecutive terms fail here
        terms = new String[] {"go", "and", "and", "tell", "your", "brother", "that", "dinners", "ready", "and", "come", "and", "wash", "his", "hands"};
        Assert.assertEquals(Collections.emptySet(), ContentFunctions.phrase("BODY", termOffsetMap, terms));
        
        // duplicate consecutive terms fail here
        terms = new String[] {"go", "and", "and", "tell", "your", "brother", "that", "dinners", "ready", "and", "come"};
        Assert.assertEquals(Collections.emptySet(), ContentFunctions.phrase("BODY", termOffsetMap, terms));
        
        // subset(1, end)
        terms = new String[] {"and", "tell", "your", "brother", "that", "dinners", "ready", "and", "come", "and", "wash", "his", "hands"};
        Assert.assertEquals(Collections.singleton("BODY"), ContentFunctions.phrase("BODY", termOffsetMap, terms));
        
        // subset(1,end-5)
        terms = new String[] {"and", "tell", "your", "brother", "that", "dinners", "ready", "and"};
        Assert.assertEquals(Collections.singleton("BODY"), ContentFunctions.phrase("BODY", termOffsetMap, terms));
        
        // ///////////////////////////
        // Within functions
        // ///////////////////////////
        
        // full terms list
        terms = new String[] {"go", "and", "tell", "your", "brother", "that", "dinners", "ready", "and", "come", "and", "wash", "his", "hands"};
        Assert.assertEquals(Collections.singleton("BODY"), ContentFunctions.within("BODY", 14, termOffsetMap, terms));
        
        // duplicate consecutive terms fail here
        terms = new String[] {"go", "and", "and", "tell", "your", "brother", "that", "dinners", "ready", "and", "come", "and", "wash", "his", "hands"};
        Assert.assertEquals(Collections.emptySet(), ContentFunctions.within("BODY", 15, termOffsetMap, terms));
        
        // placement does not matter
        terms = new String[] {"go", "and", "and", "tell", "your", "brother", "that", "dinners", "ready", "and", "come"};
        Assert.assertEquals(Collections.singleton("BODY"), ContentFunctions.within("BODY", 11, termOffsetMap, terms));
        
        // subset(1, end)
        terms = new String[] {"and", "tell", "your", "brother", "that", "dinners", "ready", "and", "come", "and", "wash", "his", "hands"};
        Assert.assertEquals(Collections.singleton("BODY"), ContentFunctions.within("BODY", 12, termOffsetMap, terms));
        
        // subset(1,end-5)
        terms = new String[] {"and", "tell", "your", "brother", "that", "dinners", "ready", "and", "come", "and"};
        Assert.assertEquals(Collections.singleton("BODY"), ContentFunctions.within("BODY", 10, termOffsetMap, terms));
    }
    
    private Zone genTestZone() {
        return new Zone("BODY", true, "shard\u0000dt\u0000uid");
    }
    
    private Zone genTestZone(String zone) {
        return new Zone(zone, true, "shard\u0000dt\u0000uid");
    }
    
    @Test
    public void testIgnoreIrrelevantZones() {
        Zone zone1 = genTestZone("ZONE1");
        Zone zone2 = genTestZone("ZONE2");
        
        String[] terms = new String[] {"some", "phrase"};
        
        TreeMultimap<Zone,TermWeightPosition> multimap;
        TermOffsetMap termOffsetMap = new TermOffsetMap();
        
        // Build term 1 offsets...
        
        multimap = TreeMultimap.create();
        multimap.put(zone1, getPosition(1));
        multimap.put(zone1, getPosition(100));
        termOffSetMap.putTermFrequencyList(terms[0], new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(zone2, getPosition(19));
        termOffSetMap.putTermFrequencyList(terms[0], new TermFrequencyList(multimap));
        
        // Build term 2 offsets...
        
        multimap = TreeMultimap.create();
        multimap.put(zone1, getPosition(10));
        multimap.put(zone1, getPosition(1000));
        termOffSetMap.putTermFrequencyList(terms[1], new TermFrequencyList(multimap));
        
        multimap = TreeMultimap.create();
        multimap.put(zone2, getPosition(20));
        multimap.put(zone2, getPosition(27));
        termOffSetMap.putTermFrequencyList(terms[1], new TermFrequencyList(multimap));
        
        // The only match, [19, 20], is in ZONE2.
        // Thus, evaluating ZONE1 should return false here (see #1171)...
        Assert.assertEquals(Collections.emptySet(), ContentFunctions.phrase(zone1.getZone(), termOffsetMap, terms));
        
        // Ensure that we do get the hit if we evaluate the other zone
        Assert.assertEquals(Collections.singleton(zone2.getZone()), ContentFunctions.phrase(zone2.getZone(), termOffsetMap, terms));
        
        // Ensure that we get the hit if we evaluate both zones
        Assert.assertEquals(Collections.singleton(zone2.getZone()), ContentFunctions.phrase(Arrays.asList(zone1.getZone(), zone2.getZone()), termOffsetMap, terms));
        
        // Ensure that we get the hit if we evaluate null zone
        Assert.assertEquals(Collections.singleton(zone2.getZone()), ContentFunctions.phrase((Object) null, termOffsetMap, terms));
    }
}
