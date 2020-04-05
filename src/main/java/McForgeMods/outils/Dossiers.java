package McForgeMods.outils;

import McForgeMods.ModVersion;
import picocli.CommandLine;

import java.nio.file.Path;

/**
 * Cette classe doit rassembler tous les outils pour trouver ou décider des répertoires.
 * <p>
 * Permet de trouver une installation minecraft relative ou non à la position actuelle. Décide du dossier d'installation
 * des mods téléchargés, de préférence, chaque mod ira dans le dossiers mods/<i>mcversion</i>.
 */
public class Dossiers {
	
	public static Path autocompletion(Path chemin) {
		if (chemin.getNameCount() > 0 && chemin.getName(0).toString().equals("~"))
			return Path.of(System.getProperty("user.home")).resolve(chemin.subpath(1, chemin.getNameCount()));
		return chemin;
	}
	
	/**
	 * Cherche un dossier d'installation minecraft.
	 *
	 * @param defaut: valeur par défaut à utiliser en priorité.
	 */
	public static Path dossierMinecraft(Path defaut) {
		if (defaut != null) {
			return autocompletion(defaut);
		}
		
		Path p = Path.of("").toAbsolutePath();
		// Si dossier .minecraft existant dans la hiérarchie actuelle.
		for (int i = p.getNameCount() - 1; i > 0; i--) {
			if (p.getName(i).toString().equals(".minecraft")) return p.subpath(0, i + 1).resolve("mods");
		}
		return Path.of(System.getProperty("user.home")).resolve(".minecraft").resolve("mods");
	}
	
	/**
	 * Cherche un dossier pour le dépot local. Par défaut, ce dossier se situe dans le répertoire
	 * ~/.minecraft/forgemods, peut importe si le dossier d'installation minecraft utilisé est autrepart.
	 *
	 * @param defaut: valeur par défaut à utiliser en priorité
	 * @return la racine du dossier pour le dépot.
	 */
	public static Path dossierDepot(Path defaut) {
		if (defaut != null) {
			return autocompletion(defaut);
		}
		
		return Path.of(System.getProperty("user.home")).resolve(".minecraft").resolve("forgemods");
	}
	
	public static Path fichierIndexDepot(Path depot) {
		return depot.resolve("Mods.json");
	}
	
	public static Path dossierModDepot(final Path depot, String modid) {
		return depot.resolve(modid.substring(0, 1)).resolve(modid);
	}
	
	public static Path fichierModDepot(Path depot, String modid) {
		return dossierModDepot(depot, modid).resolve(modid + ".json");
	}
	
	/**
	 * Dossier de préférence où sauvegarder un nouveau mod.
	 *
	 * @param minecraft: chemin vers le dossier .minecraft/mods
	 * @param mod:       informations de la version
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
