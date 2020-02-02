package tar;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class FichierTar {
	private Map<String, EntreeTar> entrees = new LinkedHashMap<>();
	
	public FichierTar(InputStream fichier) throws IOException {
		while (fichier.available() > 0) {
			final byte[] teteb = new byte[512];
			if (fichier.read(teteb, 0, 512) != 512)
				throw new IOException();
			if (teteb[0] != 0) {
				EntreeTar entree = new EntreeTar(teteb, fichier);
				// System.out.println(entree);
				this.entrees.put(entree.nom, entree);
				
				if (entree.taille % 512 != 0) {
					fichier.skip(512 - (entree.taille % 512));
				}
			} else {
				break;
			}
		}
	}
	
	public int size() {
		return this.entrees.size();
	}
	
	public Set<String> listeFichiers() {
		return this.entrees.keySet();
	}
	
	public EntreeTar fichier(String nom) throws FileNotFoundException {
		if (!this.entrees.containsKey(nom))
			throw new FileNotFoundException(nom);
		return this.entrees.get(nom);
	}
	
	public EntreeTar fichier(Path fichier) throws FileNotFoundException {
		return this.fichier(fichier.toString());
	}
	
	public static void main(String[] args) throws IOException {
		try (FileInputStream fichier = new FileInputStream(new File("build.tar"))) {
			FichierTar tar = new FichierTar(fichier);
		}
	}
}
