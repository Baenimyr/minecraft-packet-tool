package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.ModVersion;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Dossiers;
import org.json.JSONException;
import picocli.CommandLine;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * La fonction depot est responsable de toutes les interactions de modification du depot. Elle est le seule capable de
 * modifier les données.
 * <p>
 * Elle permet la lecture/ecriture des attributs des informations sauvegardées dans le dépot. La sous-commande
 * <i>import</i> associé avec une installation minecraft récupère les informations directement dans les
 * <i>mcmod.info</i> des fichiers jar trouvés.
 */
@CommandLine.Command(name = "depot", subcommands = {CommandeDepot.importation.class},
		description = {"Outil de gestion d'un dépot.", "Une installation minecraft peut être utilisée comme source de fichiers."})
public class CommandeDepot implements Runnable {
	
	@CommandLine.Option(names = {"--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;
	
	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}
	
	@CommandLine.Command(name = "refresh", description = "Importe est sauvegarde le dépot.\nPermet de détecter des "
			+ "erreurs. En cas d'erreur ne sauvegarde pas les informations corrompues.")
	public int refresh(@CommandLine.Mixin Dossiers.DossiersOptions dossiers, @CommandLine.Mixin ForgeMods.Help help,
			@CommandLine.Option(names = {"-f", "--force"}, defaultValue = "false",
					description = "Force la sauvegarde du dépot, même après des erreurs lors de l'importation.")
					boolean force) {
		DepotLocal depot = new DepotLocal(dossiers.depot);
		try {
			depot.importation();
		} catch (IOException | JSONException i) {
			System.err.println("Erreur de lecture du dépot: " + i.getClass() + " " + i.getMessage());
			if (!force) return 1;
		}
		
		System.out.println("Dépot importé.");
		
		try {
			depot.sauvegarde();
		} catch (IOException i) {
			System.err.println("Erreur d'écriture du dépot: " + i.getClass() + " " + i.getMessage());
			return 1;
		}
		
		System.out.println("Dépot sauvegardé.");
		return 0;
	}
	
	@CommandLine.Command(name = "import",
			description = "Permet d'importer des informations présentes dans les fichiers mcmod.info des archives jar. "
					+ "Utilise un dépot minecraft comme source des jars.")
	static class importation implements Callable<Integer> {
		@CommandLine.Parameters(index = "0", arity = "0..*", paramLabel = "modid",
				description = "Liste de mod spécifiques à importer (modid).")
		String[] modids;
		@CommandLine.Option(names = {"-a", "--all"}, defaultValue = "false", description = "Importe tout")
		boolean  all;
		@CommandLine.Option(names = {"--include-files"}, defaultValue = "false",
				description = "Copie les fichiers dans le dépot et ajoute un url relatif pour accéder au fichier."
						+ "Ne copie pas les fichiers incertains (pas de mcmod.info)")
		boolean  include_file;
		@CommandLine.Option(names = {"--prefix"},
				description = "Prefixe à appliquer aux urls lors de l'importation des fichiers")
		String   prefix = null;
		
		@CommandLine.Mixin
		Dossiers.DossiersOptions dossiers;
		@CommandLine.Mixin
		ForgeMods.Help           help;
		
		public Integer call() throws MalformedURLException {
			DepotLocal depot = new DepotLocal(dossiers.depot);
			try {
				depot.importation();
			} catch (IOException | JSONException i) {
				System.err
						.println("Erreur de lecture des informations du dépot: " + i.getClass() + " " + i.getMessage());
				return 1;
			}
			DepotInstallation installation = new DepotInstallation(dossiers.minecraft);
			installation.analyseDossier(depot);
			
			System.out.println(
					String.format("%d mods chargés depuis '%s'.", installation.sizeModVersion(), installation.dossier));
			
			Collection<ModVersion> importation = new ArrayList<>();
			if (all) {
				for (String modid : installation.getModids()) {
					importation.addAll(installation.getModVersions(modid));
				}
			} else if (modids != null && modids.length > 0) {
				for (String modid : modids) {
					if (installation.contains(modid)) {
						importation.addAll(installation.getModVersions(modid));
					} else {
						System.err.println("Modid non reconnu: '" + modid + "'");
					}
				}
			} else {
				System.err.println("Il faut au moins un nom de mod à importer. Sinon utiliser l'option '--all'.");
				return 2;
			}
			
			final URL prefix = include_file ? (this.prefix != null ? new URL(this.prefix)
					: depot.dossier.toUri().toURL()) : null;
			for (ModVersion version : importation) {
				final ModVersion reelle = depot.ajoutModVersion(
						new ModVersion(depot.ajoutMod(version.mod), version.version, version.mcversion));
				reelle.fusion(version);
				reelle.urls.removeIf(url -> url.getProtocol().equals("file"));
				
				if (include_file) {
					final Path nouveau_fichier = copieFichier(depot.dossier, version);
					if (nouveau_fichier != null) reelle.ajoutURL(new URL(prefix,
							nouveau_fichier.subpath(nouveau_fichier.getNameCount() - 3, nouveau_fichier.getNameCount())
									.toString()));
				}
			}
			System.out.println(String.format("%d versions importées.", importation.size()));
			
			try {
				depot.sauvegarde();
			} catch (IOException i) {
				System.err.println(
						"Impossible de sauvegarder le dépot local à '" + depot.dossier + "': " + i.getClass() + " " + i
								.getMessage());
				return 1;
			}
			return 0;
		}
		
		private Path copieFichier(Path dossier_depot, ModVersion version) {
			Optional<URL> fichier = version.urls.stream().filter(u -> u.getProtocol().equals("file")).findFirst();
			if (fichier.isPresent()) {
				try {
					final Path source = Path.of(fichier.get().toURI());
					final Path destination = dossier_depot.resolve(version.mod.modid.substring(0, 1))
							.resolve(version.mod.modid).resolve(source.getFileName());
					destination.getParent().toFile().mkdirs();
					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
					return destination;
				} catch (IOException | URISyntaxException e) {
					System.err.println(String.format("Impossible de copier le fichier '%s'!", fichier.get()));
				}
			} else {
				System.err.println(
						String.format("Aucun fichier à copier pour le mod %s=%s", version.mod.modid, version.version));
			}
			return null;
		}
	}
}