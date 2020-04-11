package simpleJson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Génère du texte JSON à partir de {@link org.json.JSONObject} ou {@link org.json.JSONArray}.
 * <p>
 * Contrairement à {@link JSONObject#write(Writer)} les clés sont triées par ordre alphabétique afin de minimiser les
 * changements dans le fichiers générés lorsque la liste des clés est modifiée.
 */
public class JSONWriter {
	public int  indentfactor;
	public char indent;
	
	public JSONWriter() {
		this(1, '\t');
	}
	
	public JSONWriter(int indentfactor, char indent) {
		this.indentfactor = indentfactor;
		this.indent = indent;
	}
	
	static void quote(String texte, Writer w) throws IOException {
		w.write("\"");
		
		for (int i = 0; i < texte.length(); i++) {
			char c = texte.charAt(i);
			switch (c) {
				case '\\':
				case '"':
					w.write('\\');
					w.write(c);
					break;
				case '\b':
					w.write("\\b");
					break;
				case '\t':
					w.write("\\t");
					break;
				case '\n':
					w.write("\\n");
					break;
				case '\f':
					w.write("\\f");
					break;
				case '\r':
					w.write("\\r");
					break;
				default:
					if (c < ' ' || (c >= '\u0080' && c < '\u00a0') || (c >= '\u2000' && c < '\u2100')) {
						w.write("\\u");
						String hhhh = Integer.toHexString(c);
						w.write("0000", 0, 4 - hhhh.length());
						w.write(hhhh);
					} else {
						w.write(c);
					}
			}
		}
		
		w.write("\"");
	}
	
	void indent(Writer writer, final int quantite) throws IOException {
		for (int i = 0; i < quantite; i++)
			writer.write(this.indent);
	}
	
	public void writeValue(Object o, Writer writer, int indent) throws IOException {
		if (Objects.equals(o, null)) {
			writer.write("null");
		} else if (o instanceof JSONArray) {
			this.writeList((JSONArray) o, writer, indent);
		} else if (o instanceof JSONObject) {
			this.writeObject((JSONObject) o, writer, indent);
		} else if (o instanceof Integer) writer.write(Integer.toString((Integer) o));
		else if (o instanceof Long) writer.write(Long.toString((Long) o));
		else if (o instanceof Float) writer.write(Float.toString((Float) o));
		else if (o instanceof Double) writer.write(Double.toString((Double) o));
		else if (o instanceof Boolean) writer.write(Boolean.toString((Boolean) o));
		else if (o instanceof String) {
			quote(o.toString(), writer);
		} else if (o instanceof Collection<?>) {
			this.writeList(new JSONArray((Collection<?>) o), writer, indent);
		} else {
			throw new IllegalArgumentException("Objet " + o.getClass() + " incompatible.");
		}
	}
	
	public void write(JSONObject jo, Writer writer) throws IOException {
		this.writeObject(jo, writer, 0);
	}
	
	public void write(JSONArray ja, Writer writer) throws IOException {
		this.writeList(ja, writer, 0);
	}
	
	public void writeObject(JSONObject jo, Writer writer, int indent) throws IOException {
		List<String> cles = jo.keySet().stream().sorted(String::compareTo).collect(Collectors.toList());
		
		writer.write('{');
		if (cles.size() == 0) {
			writer.write('}');
		} else if (cles.size() == 1) {
			String cle = cles.get(0);
			quote(cle, writer);
			writer.write(": ");
			this.writeValue(jo.get(cle), writer, indent + indentfactor);
			writer.write('}');
		} else {
			if (indentfactor > 0) writer.write('\n');
			
			this.indent(writer, indent + indentfactor);
			quote(cles.get(0), writer);
			writer.write(": ");
			this.writeValue(jo.get(cles.get(0)), writer, indent + indentfactor);
			
			for (int i = 1; i < cles.size(); i++) {
				String cle = cles.get(i);
				writer.write(",");
				if (indentfactor > 0) {
					writer.write("\n");
					this.indent(writer, indent + indentfactor);
				}
				quote(cle, writer);
				writer.write(": ");
				this.writeValue(jo.get(cle), writer, indent + indentfactor);
			}
			
			if (indentfactor > 0) {
				writer.write("\n");
				this.indent(writer, indent);
			}
			writer.write('}');
		}
	}
	
	public void writeList(JSONArray liste, final Writer writer, int indent) throws IOException {
		writer.write('[');
		if (liste.length() == 0) {
			writer.write(']');
		} else if (liste.length() == 1) {
			this.writeValue(liste.get(0), writer, indent + indentfactor);
			writer.write(']');
		} else {
			if (indentfactor > 0) writer.write('\n');
			this.indent(writer, indent + indentfactor);
			this.writeValue(liste.get(0), writer, indent + indentfactor);
			
			for (int i = 1; i < liste.length(); i++) {
				writer.write(",");
				if (indentfactor > 0) {
					writer.write("\n");
					this.indent(writer, indent + indentfactor);
				}
				this.writeValue(liste.get(i), writer, indent + indentfactor);
			}
			
			if (indentfactor > 0) {
				writer.write("\n");
				this.indent(writer, indent);
			}
			writer.write(']');
		}
	}
}
