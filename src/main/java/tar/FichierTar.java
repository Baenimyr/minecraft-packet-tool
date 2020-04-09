package tar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class FichierTar {
	private InputStream in;
	private Map<String, EntreeTar> entrees = new LinkedHashMap<>();
	
	public FichierTar(InputStream fichier) throws IOException {
		this.in = fichier;
		this.lectureTotale();
	}
	
	private int lectureFragment() throws IOException {
		if (this.in == null) return -1;
		
		final byte[] teteb = new byte[512];
		int lu = 0;
		while (lu < 512) {
			int l = in.read(teteb, lu, 512 - lu);
			if (l == -1) {
				this.in.close();
				this.in = null;
				return -1;
			}
			lu += l;
		}
		if (teteb[0] != 0) {
			EntreeTar entree = new EntreeTar(teteb, this.in);
			// System.out.println(entree);
			this.entrees.put(entree.nom, entree);
			lu += entree.taille;
			if (entree.taille % 512 != 0) {
				this.in.skip(512 - (entree.taille % 512));
				lu += 512 - (entree.taille % 512);
			}
		}
		return lu;
	}
	
	/** Force la lecture de tout le fichier et l'importation des données. */
	public void lectureTotale() throws IOException {
		while (this.lectureFragment() != -1)
			continue;
	}
	
	public int size() {
		return this.entrees.size();
	}
	
	public boolean contains(String nom) {
		try {
			while (!this.entrees.containsKey(nom) && this.lectureFragment() != -1) continue;
		} catch (IOException ignored) {}
		return this.entrees.containsKey(nom);
	}
	
	public Set<String> listeFichiers() {
		return this.entrees.keySet();
	}
	
	public EntreeTar fichier(String nom) throws FileNotFoundException {
		if (!this.entrees.containsKey(nom))
			throw new FileNotFoundException(nom);
		return this.entrees.get(nom);
	}
	
	public EntreeTar fichier(Path fichier) throws IOException {
		return this.fichier(fichier.toString());
	}
}
