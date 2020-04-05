package McForgeMods.outils;

import McForgeMods.ModVersion;

import java.nio.file.Path;

/**
 * Cette classe doit rassembler tous les outils pour trouver ou décider des répertoires.
 * <p>
 * Permet de trouver une installation minecraft relative ou non à la position actuelle. Décide du dossier d'installation
 * des mods téléchargés, de préférence, chaque mod ira dans le dossiers mods/<i>mcversion</i>.
 */
public class Dossiers {
	
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
	
}
