package simpleJson;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JSONWriterTest {
	
	@Test
	public void ecritureValue() throws IOException {
		JSONObject test = new JSONObject();
		test.put("a", 32);
		test.put("b", 6.3);
		test.put("c", 45886543543L);
		test.put("d\\", "spécial");
		
		List<String> liste = new ArrayList<>();
		liste.add("elem 1");
		liste.add("elem 2");
		liste.add("elem 3");
		test.put("liste", liste);
		
		
		JSONWriter w = new JSONWriter();
		StringWriter writer = new StringWriter();
		w.indent = ' ';
		w.indentfactor = 4;
		
		w.write(test, writer);
		assertEquals("{\n"
				+ "    \"a\": 32,\n"
				+ "    \"b\": 6.3,\n"
				+ "    \"c\": 45886543543,\n"
				+ "    \"d\\\\\": \"spécial\",\n"
				+ "    \"liste\": [\n"
				+ "        \"elem 1\",\n"
				+ "        \"elem 2\",\n"
				+ "        \"elem 3\"\n"
				+ "    ]\n"
				+ "}", writer.toString());
	}
	
	@Test
	public void ecritureCompact() throws IOException {
		JSONObject test = new JSONObject();
		JSONArray prim = new JSONArray();
		test.put("prim", prim);
		prim.put(1);
		prim.put(2);
		prim.put(3);
		prim.put(5);
		prim.put(7);
		prim.put(11);
		prim.put(13);
		test.put("alphabet", "abcdefghijklmnopqrstuvwxyz");
		
		JSONWriter w = new JSONWriter();
		StringWriter out = new StringWriter();
		w.indentfactor = 0;
		
		w.write(test, out);
		assertEquals("{\"alphabet\": \"abcdefghijklmnopqrstuvwxyz\",\"prim\": [1,2,3,5,7,11,13]}", out.toString());
	}
}
