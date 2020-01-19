package McForgeMods.outils;

import McForgeMods.ModVersion;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class TelechargementFichier extends Telechargement {
	
	public TelechargementFichier(ModVersion mod, URL url, Path minecraft) {
		super(mod, url, minecraft);
	}
	
	@Override
	public Integer call() throws Exception {
		if (!this.creationDossier()) return 2;
		final Path origine = Path.of(this.url.toURI());
		final String nom_cible = this.mod.alias.size() > 0 ? this.mod.alias.get(0) : origine.getFileName().toString();
		
		this.changeTailleTotale(origine.toFile().getTotalSpace());
		Files.copy(origine, this.dossier_cible.resolve(nom_cible), StandardCopyOption.COPY_ATTRIBUTES);
		this.ajoutTelecharge(this.tailleTotal);
		return 0;
	}
}
