package McForgeMods.outils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Liste les différentes sources. Permettra à l'avenir l'existance d'options associées aux urls, comme la priorité ou le
 * type.
 */
public class Sources {
	public enum SourceType {
		TAR("tar"),
		HTTP("http");
		
		final String key;
		
		SourceType(String key) {
			this.key = key.intern();
		}
		
		public static SourceType getType(String key) {
			for (SourceType t : SourceType.values()) {
				if (t.key.equals(key)) return t;
			}
			return null;
		}
	}
	
	private final Map<URL, SourceType> urls = new WeakHashMap<>();
	
	public Sources() {
	}
	
	public Sources(InputStream fichier) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(fichier))) {
			String ligne;
			while ((ligne = reader.readLine()) != null) {
				if (ligne.startsWith("#")) continue;
				String[] s = ligne.split("\\s");
				
				try {
					if (s.length == 1) {
						this.add(new URL(Path.of(".").toUri().toURL(), s[0]));
					} else if (s.length >= 2) {
						SourceType type = SourceType.getType(s[0]);
						if (type == null) {
							System.err.println(String.format("Type de ressource inconnu: '%s'", s[0]));
						}
						this.add(new URL(Path.of(".").toUri().toURL(), s[1]), type);
					}
				} catch (MalformedURLException url) {
					System.err.println(String.format("MalformedURL: '%s'", url));
				}
			}
		}
	}
	
	public void add(URL url) {
		this.add(url, url.getPath().endsWith(".tar") ? SourceType.TAR : SourceType.HTTP);
	}
	
	public void add(URL url, SourceType type) {
		this.urls.put(url, type);
	}
	
	public Map<URL, SourceType> urls() {
		return this.urls;
	}
	
	public int size() {
		return this.urls.size();
	}
	
	public void save(OutputStream out) throws IOException {
		try (OutputStreamWriter buff = new OutputStreamWriter(out);
			 BufferedWriter writer = new BufferedWriter(buff)) {
			for (Map.Entry<URL, SourceType> url : this.urls.entrySet()) {
				writer.write(url.getValue().key);
				writer.write("\t");
				writer.write(url.getKey().toString());
				writer.write("\n");
			}
		}
	}
}
