package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.ModVersion;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

/**
 * La fonction depot est responsable de toutes les interactions de modification du depot.
 * Elle est le seule capable de modifier les données.
 * <p>
 * Elle permet la lecture/ecriture des attributs des informations sauvegardées dans le dépot.
 * La sous-commande <i>import</i> associé avec une installation minecraft récupère les informations directement dans les <i>mcmod.info</i> des fichiers jar trouvés.
 */
@CommandLine.Command(name = "depot", description = "Outil de gestion d'un dépot.\n"
		+ "Une installation minecraft peut être utilisée comme source de fichiers.")
public class CommandeDepot implements Runnable {
	
	@CommandLine.Option(names = {"--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;
	
	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}
	
	@CommandLine.Command(name = "import",
			description = "Permet d'importer des informations présentes dans les fichiers mcmod.info des archives jar. "
					+ "Utilise un dépot minecraft comme source des jars.")
	public int importation(@CommandLine.Parameters(index = "0", arity = "0..*", paramLabel = "modid",
			description = "Liste de mod spécifiques à importer (modid).") String[] modids,
			@CommandLine.Option(names = {"-a", "--all"}, description = "Importe tout") boolean all,
			@CommandLine.Mixin ForgeMods.DossiersOptions dossiers,
			@CommandLine.Option(names = {"--help"}, usageHelp = true) boolean help) {
		Path minecraft = DepotInstallation.resolutionDossierMinecraft(dossiers.minecraft);
		if (minecraft == null) {
			System.out.println("Impossible de trouver un dossier d'installation minecraft.");
			return 1;
		}
		
		DepotInstallation installation = new DepotInstallation(minecraft);
		installation.analyseDossier();
		DepotLocal depot = new DepotLocal(dossiers.depot);
		try {
			depot.importation();
		} catch (IOException i) {
			System.err.println("Erreur de lecture des informations du dépot.");
			return 1;
		}
		
		Collection<ModVersion> importation = new ArrayList<>();
		if (all) {
			for (String modid : installation.getModids()) {
				importation.addAll(installation.getModVersions(modid));
			}
		} else if (modids != null && modids.length > 0) {
			for (String modid : modids) {
				if (installation.getModids().contains(modid)) {
					importation.addAll(installation.getModVersions(modid));
				} else {
					System.err.println("Modid non reconnu: '" + modid + "'");
				}
			}
		} else {
			System.err.println("Il faut au moins un nom de mod à importer. Sinon utiliser l'option '--all'.");
			return 2;
		}
		
		for (ModVersion version : importation) {
			ModVersion reelle = depot.ajoutModVersion(version);
			reelle.urls.removeIf(url -> url.getProtocol().equals("file"));
		}
		System.out.println(String.format("%d versions importées.", importation.size()));
		
		try {
			depot.sauvegarde();
		} catch (IOException i) {
			System.err.println("Impossible de sauvegarder le dépot local à '" + depot.dossier + "'");
			return 1;
		}
		return 0;
	}
}