package McForgeMods.outils;

import McForgeMods.ModVersion;
import picocli.CommandLine;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

/**
 * Cette classe doit rassembler tous les outils pour trouver ou décider des répertoires.
 * <p>
 * Permet de trouver une installation minecraft relative ou non à la position actuelle.
 * Décide du dossier d'installation des mods téléchargés, de préférence, chaque mod ira dans le dossiers
 * mods/<i>mcversion</i>.
 */
public class Dossiers {
	
	/**
	 * Cherche un dossier d'installation minecraft.
	 * @param defaut: valeur par défaut à utiliser en priorité.
	 */
	public static Path dossierMinecraft(Path defaut) {
		if (defaut != null) return defaut.toAbsolutePath();
		
		Path p = Path.of("").toAbsolutePath();
		
		// Si dossier .minecraft existant dans la hiérarchie actuelle.
		for (int i = p.getNameCount() - 1; i > 0; i--) {
			if (p.getName(i).toString().equals(".minecraft")) return p.subpath(0, i + 1);
		}
		
		return Path.of(System.getProperty("user.home")).resolve(".minecraft");
	}
	
	/**
	 * Cherche un dossier pour le dépot local.
	 * Par défaut, ce dossier se situe dans le répertoire ~/.minecraft/forgemods, peut importe si le dossier
	 * d'installation minecraft utilisé est autrepart.
	 * @param defaut: valeur par défaut à utiliser en priorité
	 * @return la racine du dossier pour le dépot.
	 */
	public static Path dossierDepot(Path defaut) {
		if (defaut != null) return defaut.toAbsolutePath();
		
		return Path.of(System.getProperty("user.home")).resolve(".minecraft").resolve("forgemods");
	}
	
	public static Path fichierIndexDepot(Path depot) {
		return depot.resolve("Mods.json");
	}
	
	/** Localise le fichier d'index général relatif à la racine du dépot.
	 * @param depot: racine du dépot
	 */
	public static URL fichierIndexDepot(URL depot) throws MalformedURLException {
		return new URL(depot, "Mods.json");
	}
	
	/**
	 * Localise le fichier d'information de mod relatif à la racine du dépot.
	 * @param depot: racine du dépot
	 * @param modid: identifiant unique du mod cherché.
	 */
	public static URL fichierModDepot(URL depot, String modid) throws MalformedURLException {
		return new URL(depot, modid.substring(0, 1) + "/" + modid + "/" + modid + ".json");
	}
	
	/** Dossier de préférence où sauvegarder un nouveau mod.
	 * @param minecraft: chemin vers le dossier .minecraft/mods
	 * @param mod: informations de la version
	 * @return .minecraft/mods/<i>mcversion</i>/
	 */
	public static Path dossierInstallationMod(Path minecraft, ModVersion mod) {
		return minecraft.resolve("mods").resolve(mod.mcversion.toString());
	}
	
	/**
	 * Options commune aux fonctions utilisant un dépot et une installation minecraft.
	 */
	public static class DossiersOptions {
		/**
		 * Spécifie le dossier de dépot à utiliser.
		 */
		@CommandLine.Option(names = {"-d", "--depot"}, description = "Dépot local à utiliser")
		public Path depot = null;
		
		/**
		 * Spécifie l'installation minecraft à utiliser. Il peut en exister d'autres de ~/.minecraft.
		 */
		@CommandLine.Option(names = {"-m", "--minecraft"}, description = "Dossier minecraft (~/.minecraft)")
		public Path minecraft = null;
	}
}
