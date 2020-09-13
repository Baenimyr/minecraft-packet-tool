package McForgeMods.outils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Liste les différentes sources. Permettra à l'avenir l'existance d'options associées aux urls, comme la priorité.
 */
public class Sources {
	private final List<URI> urls = new ArrayList<>();
	
	public Sources() {
	}
	
	public void importation(InputStream fichier) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(fichier))) {
			String ligne;
			while ((ligne = reader.readLine()) != null) {
				if (ligne.startsWith("#")) continue;
				
				try {
					this.add(new URI(ligne));
				} catch (URISyntaxException url) {
					System.err.printf("MalformedURL: '%s'%n", url);
				}
			}
		}
	}
	
	public void add(URI uri) {
		this.urls.add(uri);
	}
	
	public List<URI> urls() {
		return this.urls;
	}
	
	public int size() {
		return this.urls.size();
	}
}
